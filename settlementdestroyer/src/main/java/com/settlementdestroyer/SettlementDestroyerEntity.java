package com.settlementdestroyer;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.RavagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.mob.EvokerFangsEntity;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.item.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Settlement Destroyer — Ravager-based boss with 2 phases.
 *
 * Phase 1 (100% - 50% HP / 200-100 HP):
 *   - Standard Ravager melee attack
 *   - On each successful melee hit, spawns Evoker Fangs at target position
 *
 * Phase 2 (< 50% HP / < 100 HP):
 *   - Phase 1 mechanics continue
 *   - Every 30 seconds, 50/50 roll:
 *     A) Ring of Evoker Fangs around boss (radius 10 blocks)
 *     B) Dash attack toward target with fang trail, x2 damage, block breaking
 *
 * Boss has 200 HP, 100% knockback resistance, immune to explosions.
 *
 * Bedrock-compatible: extends RavagerEntity directly so vanilla clients
 * (and Bedrock via Geyser) see a normal Ravager with custom name + boss bar.
 */
public class SettlementDestroyerEntity extends RavagerEntity {

    private final ServerBossBar bossBar;
    private int phase = 1;
    /** Ticks since last special attack in phase 2. 10 sec = 200 ticks. */
    private int specialCooldown = 0;
    private static final int SPECIAL_INTERVAL_TICKS = 200;
    private static final double COMBAT_RADIUS = 50.0;
    /** Tracks all players who damaged the boss — used for loot distribution. */
    private final Set<UUID> damageDealers = new HashSet<>();
    private boolean deathHandled = false;

    /** Scheduled rings for the "Converging Ring" attack. Each entry:
     *  {ticksUntilSpawn, radius, count}. Processed in tickMovement(). */
    private final java.util.List<int[]> ringQueue = new java.util.ArrayList<>();
    /** Center position of the converging ring attack — captured when the attack starts. */
    private double ringCenterX, ringCenterY, ringCenterZ;

    /** === DASH STATE ===
     *  Boss performs a velocity-based dash at BABY ZOMBIE speed (~0.45 b/tick).
     *
     *  Each tick while dashing:
     *   1. Boss moves via velocity (set once at start, friction is minimal
     *      because we also forcefully teleport to expected position each tick)
     *   2. Spawn 2 fangs at boss's CURRENT position (left + right) —
     *      but only every ~1 block traveled (not every tick)
     *   3. Break blocks in a 3x3x3 box around boss's current position
     *   4. Apply ×2 damage to entities in a box around boss's current position
     *   5. When boss reaches target (or runs out of ticks) → stop + spawn
     *      4-fang end cluster
     *
     *  Speed: 0.45 blocks/tick (baby zombie speed, ~9 blocks/sec)
     *  For distance N: ticks = ceil(N / 0.45)
     */
    private boolean dashing = false;
    private int dashTicksLeft = 0;
    private double dashTargetX, dashTargetY, dashTargetZ;
    private double dashDirX, dashDirZ;
    private float dashFangYaw;
    private double dashPerpX, dashPerpZ;
    /** Position of last fang spawn — used to spawn fangs every ~1 block. */
    private double dashLastFangX, dashLastFangZ;
    private static final double DASH_SPEED = 0.45; // baby zombie speed (blocks/tick)

    public SettlementDestroyerEntity(EntityType<? extends RavagerEntity> entityType, World world) {
        super(entityType, world);
        this.bossBar = new ServerBossBar(
                Text.literal("Разрушитель Поселений")
                        .formatted(Formatting.DARK_RED, Formatting.BOLD),
                BossBar.Color.RED, BossBar.Style.PROGRESS);
        this.setCustomName(Text.literal("Разрушитель Поселений")
                .formatted(Formatting.DARK_RED, Formatting.BOLD));
        this.setCustomNameVisible(true);
        this.setPersistent();
        // Set 200 HP immediately
        this.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(200.0);
        this.setHealth(200.0f);
    }

    public static DefaultAttributeContainer.Builder createSettlementDestroyerAttributes() {
        return RavagerEntity.createRavagerAttributes()
                .add(EntityAttributes.MAX_HEALTH, 200.0)
                .add(EntityAttributes.KNOCKBACK_RESISTANCE, 1.0) // 100%
                .add(EntityAttributes.ARMOR, 4.0)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.3)
                .add(EntityAttributes.ATTACK_DAMAGE, 12.0)
                .add(EntityAttributes.FOLLOW_RANGE, 64.0);
    }

    @Override
    protected void initGoals() {
        // Clear vanilla Ravager goals (we want our own AI, not raid AI)
        this.goalSelector.clear(g -> true);
        this.targetSelector.clear(g -> true);

        // Melee attack goal
        this.goalSelector.add(1, new MeleeAttackGoal(this, 1.0, true));
        // Revenge — attack anything that damages us
        this.targetSelector.add(1, new RevengeGoal(this));
        // Target nearby players
        this.targetSelector.add(2, new net.minecraft.entity.ai.goal.ActiveTargetGoal<>(
                this, PlayerEntity.class, true));
        // Wander + look goals
        this.goalSelector.add(5, new WanderAroundFarGoal(this, 0.8));
        this.goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 32.0f));
    }

    // === COMBAT ===

    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        boolean result = super.damage(world, source, amount);
        if (result && source.getAttacker() instanceof ServerPlayerEntity player) {
            damageDealers.add(player.getUuid());
            if (!bossBar.getPlayers().contains(player)) {
                bossBar.addPlayer(player);
            }
        }
        return result;
    }

    /**
     * Phase 1 mechanic: spawn Evoker Fangs at target position on successful hit.
     * This overrides RavagerEntity.tryAttack to add fang spawn after standard attack.
     */
    @Override
    public boolean tryAttack(ServerWorld world, Entity target) {
        boolean hit = super.tryAttack(world, target);
        if (hit && target instanceof net.minecraft.entity.LivingEntity) {
            // Phase 1 mechanic: spawn 5 Evoker Fangs in a line from boss toward target.
            // Direction from boss to target.
            double dx = target.getX() - this.getX();
            double dz = target.getZ() - this.getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len < 0.001) {
                // Target is exactly on boss — spawn 1 fang at boss position
                spawnFangsAt(world, this.getX(), this.getY(), this.getZ(), 0);
            } else {
                double nx = dx / len;
                double nz = dz / len;
                float yaw = (float) Math.toDegrees(Math.atan2(-nx, nz));
                // Spawn 5 fangs in a straight line from boss toward target,
                // 1 block apart.
                for (int i = 1; i <= 5; i++) {
                    double fx = this.getX() + nx * i;
                    double fz = this.getZ() + nz * i;
                    spawnFangsAt(world, fx, this.getY(), fz, yaw);
                }
            }
        }
        return hit;
    }

    // === PHASES & SPECIAL ATTACKS ===

    @Override
    public void tickMovement() {
        super.tickMovement();

        if (this.getEntityWorld().isClient()) return;
        ServerWorld serverWorld = (ServerWorld) this.getEntityWorld();

        // Cleanup if dead
        if (!this.isAlive() || this.isRemoved()) {
            if (bossBar != null && !bossBar.getPlayers().isEmpty()) {
                bossBar.setPercent(0);
                bossBar.clearPlayers();
            }
            return;
        }

        // Update boss bar progress
        bossBar.setPercent(this.getHealth() / this.getMaxHealth());

        // Auto-add nearby players to boss bar so everyone in combat radius
        // can see the boss HP, even if they haven't damaged it yet.
        for (ServerPlayerEntity p : serverWorld.getPlayers()) {
            if (p.squaredDistanceTo(this) < COMBAT_RADIUS * COMBAT_RADIUS
                    && !bossBar.getPlayers().contains(p)) {
                bossBar.addPlayer(p);
            }
        }

        // Phase transition
        float hp = this.getHealth();
        int newPhase = hp > 100.0f ? 1 : 2;
        if (newPhase != phase) {
            phase = newPhase;
            onPhaseChange(serverWorld);
        }

        // Phase 2: special attack every 10 seconds (200 ticks).
        // Timer is absolute — does NOT reset when boss takes or deals damage.
        if (phase == 2) {
            specialCooldown++;
            if (specialCooldown >= SPECIAL_INTERVAL_TICKS) {
                specialCooldown = 0;
                rollSpecialAttack(serverWorld);
            }
        }

        // Process any pending converging ring spawns (queued by spawnFangRing)
        processRingQueue(serverWorld);

        // Process active dash attack (moves boss, spawns fangs, breaks blocks,
        // deals damage each tick)
        processDash(serverWorld);
    }

    private void onPhaseChange(ServerWorld world) {
        this.playSound(SoundEvents.ENTITY_RAVAGER_ROAR, 3.0f, 0.5f);
        if (phase == 2) {
            bossBar.setColor(BossBar.Color.PURPLE);
            bossBar.setName(Text.literal("Разрушитель Поселений — Ярость")
                    .formatted(Formatting.DARK_PURPLE, Formatting.BOLD));
            // Smoke particle effect at boss position
            for (int i = 0; i < 30; i++) {
                double dx = (world.random.nextDouble() - 0.5) * 4;
                double dy = world.random.nextDouble() * 3;
                double dz = (world.random.nextDouble() - 0.5) * 4;
                world.spawnParticles(net.minecraft.particle.ParticleTypes.LARGE_SMOKE,
                        this.getX() + dx, this.getY() + dy, this.getZ() + dz,
                        5, 0, 0, 0, 0.05);
            }
            // Announce
            for (ServerPlayerEntity player : bossBar.getPlayers()) {
                player.sendMessage(Text.literal("§5Разрушитель впадает в ярость! Берегитесь спецатак!")
                        .formatted(Formatting.DARK_PURPLE), false);
            }
            // Immediately trigger the first special attack on phase transition.
            // Cooldown resets so the next attack is in 10 sec.
            specialCooldown = 0;
            rollSpecialAttack(world);
        }
    }

    /**
     * 50/50 roll between Fang Ring and Dash Attack.
     */
    private void rollSpecialAttack(ServerWorld world) {
        boolean ring = world.getRandom().nextBoolean();
        if (ring) {
            spawnFangRing(world);
        } else {
            dashAttack(world);
        }
    }

    /**
     * Ability A: CONVERGING RING of Evoker Fangs.
     *
     * Instead of spawning 80+ fangs at once (laggy), spawns 4 concentric rings
     * with decreasing radius and a 0.5-second delay between them. Each ring
     * has fewer fangs as it shrinks toward the boss. Total ~56 fangs but
     * distributed over 1.5 seconds for smooth visuals and minimal TPS impact.
     *
     * Rings (in spawn order):
     *   Ring 1: r=10, 24 fangs (immediately)
     *   Ring 2: r=7,  20 fangs (after 10 ticks = 0.5s)
     *   Ring 3: r=4,  16 fangs (after 20 ticks = 1.0s)
     *   Ring 4: r=1,  8  fangs (after 30 ticks = 1.5s)
     *
     * Each fang also breaks the block directly above it (except unbreakable).
     */
    private void spawnFangRing(ServerWorld world) {
        // Boss stops and roars
        this.playSound(SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, 2.0f, 0.5f);
        this.getNavigation().stop();

        // Capture center position — rings converge on this point even if the
        // boss moves while the attack is in progress.
        ringCenterX = this.getX();
        ringCenterY = this.getY();
        ringCenterZ = this.getZ();

        // Schedule 4 rings with decreasing radius and 10-tick (0.5s) delay
        ringQueue.clear();
        ringQueue.add(new int[]{0,  10, 24});  // ring 1: immediate, r=10, 24 fangs
        ringQueue.add(new int[]{10, 7,  20});  // ring 2: +0.5s, r=7, 20 fangs
        ringQueue.add(new int[]{20, 4,  16});  // ring 3: +1.0s, r=4, 16 fangs
        ringQueue.add(new int[]{30, 1,  8});   // ring 4: +1.5s, r=1, 8 fangs

        // Announce
        for (ServerPlayerEntity player : bossBar.getPlayers()) {
            player.sendMessage(Text.literal("§c§lСужающееся Кольцо Челюстей!")
                    .formatted(Formatting.RED), true);
        }
    }

    /**
     * Process the converging ring queue — called every tick from tickMovement().
     * Spawns rings whose delay has elapsed, then removes them from the queue.
     */
    private void processRingQueue(ServerWorld world) {
        if (ringQueue.isEmpty()) return;
        // Decrement all timers
        java.util.Iterator<int[]> it = ringQueue.iterator();
        while (it.hasNext()) {
            int[] entry = it.next();
            entry[0]--; // decrement ticksUntilSpawn
            if (entry[0] <= 0) {
                // Spawn this ring
                int radius = entry[1];
                int count = entry[2];
                spawnRingAt(world, ringCenterX, ringCenterY, ringCenterZ, radius, count);
                it.remove();
            }
        }
    }

    /**
     * Spawn a single ring of fangs at the given center with the given radius.
     */
    private void spawnRingAt(ServerWorld world, double cx, double cy, double cz,
                             int radius, int count) {
        for (int i = 0; i < count; i++) {
            double angle = (i * 2 * Math.PI) / count;
            double fx = cx + Math.cos(angle) * radius;
            double fz = cz + Math.sin(angle) * radius;
            spawnFangsAndBreakAbove(world, fx, cy, fz);
        }
        // Sound effect per ring for audio feedback
        world.playSound(null, cx, cy, cz,
                SoundEvents.ENTITY_EVOKER_FANGS_ATTACK,
                net.minecraft.sound.SoundCategory.HOSTILE, 1.0f, 0.8f);
    }

    /**
     * Spawn a fang at the given position and break the block above it
     * (unless it's obsidian, bedrock, or other unbreakable block).
     */
    private void spawnFangsAndBreakAbove(ServerWorld world, double x, double y, double z) {
        // Spawn fang
        EvokerFangsEntity fangs = new EvokerFangsEntity(world, x, y, z, 0f, 0, this);
        world.spawnEntity(fangs);
        // Break block above the fang
        BlockPos above = BlockPos.ofFloored(x, y + 1, z);
        BlockState state = world.getBlockState(above);
        if (state.isAir()) return;
        if (state.getBlock().getHardness() < 0) return; // bedrock
        if (state.getBlock() == Blocks.OBSIDIAN) return;
        if (state.getBlock() == Blocks.CRYING_OBSIDIAN) return;
        if (state.getBlock() == Blocks.REINFORCED_DEEPSLATE) return;
        if (state.getBlock() == Blocks.NETHERITE_BLOCK) return;
        if (state.getBlock() == Blocks.ANCIENT_DEBRIS) return;
        world.breakBlock(above, false);
    }

    /**
     * Ability B: Dash attack — boss rams toward target at BABY ZOMBIE speed.
     *
     * Distance: min(distance to target, 25) + 2 blocks overshoot
     * Speed: 0.45 blocks/tick (baby zombie, ~9 blocks/sec)
     * Duration: ceil(distance / 0.45) ticks
     *
     * Each tick the boss moves, spawns fangs (2 per block traveled), breaks
     * blocks around current position, and deals ×2 damage (reduced by armor)
     * to entities around current position. At the end, spawns 4-fang cluster.
     */
    private void dashAttack(ServerWorld world) {
        Entity target = this.getTarget();
        Vec3d dir;
        double distanceToTarget;

        if (target == null) {
            float yaw = this.getYaw();
            dir = new Vec3d(-Math.sin(Math.toRadians(yaw)), 0, Math.cos(Math.toRadians(yaw)));
            distanceToTarget = 10.0;
        } else {
            Vec3d toTarget = new Vec3d(
                    target.getX() - this.getX(),
                    0,
                    target.getZ() - this.getZ());
            distanceToTarget = toTarget.length();
            if (distanceToTarget < 0.1) {
                float yaw = this.getYaw();
                dir = new Vec3d(-Math.sin(Math.toRadians(yaw)), 0, Math.cos(Math.toRadians(yaw)));
                distanceToTarget = 5.0;
            } else {
                dir = toTarget.normalize();
            }
        }

        this.playSound(SoundEvents.ENTITY_RAVAGER_ATTACK, 3.0f, 0.7f);

        // Distance: cap at 25, then add 2 blocks overshoot past the target
        double cappedDistance = Math.min(distanceToTarget, 25.0);
        double dashDistance = cappedDistance + 2.0;

        // Set up dash state
        dashing = true;
        dashTargetX = this.getX() + dir.x * dashDistance;
        dashTargetY = this.getY();
        dashTargetZ = this.getZ() + dir.z * dashDistance;
        dashDirX = dir.x;
        dashDirZ = dir.z;
        dashFangYaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        dashPerpX = -dir.z;
        dashPerpZ = dir.x;
        dashLastFangX = this.getX();
        dashLastFangZ = this.getZ();
        // Number of ticks = ceil(distance / speed). Add 1 for safety.
        dashTicksLeft = (int) Math.ceil(dashDistance / DASH_SPEED) + 1;

        // Set velocity for visual smoothness — baby zombie speed.
        // We also forcefully reposition the boss each tick in processDash()
        // so friction doesn't cause overshoot.
        this.setVelocity(dir.x * DASH_SPEED, 0.0, dir.z * DASH_SPEED);
        this.velocityDirty = true;

        // Announce
        for (ServerPlayerEntity player : bossBar.getPlayers()) {
            player.sendMessage(Text.literal("§c§lСмертоносный рывок!")
                    .formatted(Formatting.RED), true);
        }
    }

    /**
     * Process the dash — called every tick from tickMovement() while dashing.
     *
     * Each tick:
     *   1. Forcefully move boss toward target by DASH_SPEED (overriding friction)
     *   2. Spawn 2 fangs at boss's CURRENT position if traveled ~1 block since
     *      last fang spawn (gradual fang trail, not all at once)
     *   3. Break blocks in a 3x3x3 box around boss's current position
     *   4. Apply ×2 damage to entities in a box around boss's current position
     *   5. When boss reaches target (or runs out of ticks) → stop + spawn
     *      4-fang end cluster
     */
    private void processDash(ServerWorld world) {
        if (!dashing) return;

        // Calculate expected position for this tick — boss moves DASH_SPEED
        // blocks per tick toward target. This OVERRIDES friction/physics.
        double curX = this.getX();
        double curZ = this.getZ();
        double dx = dashTargetX - curX;
        double dz = dashTargetZ - curZ;
        double remaining = Math.sqrt(dx * dx + dz * dz);

        if (remaining <= DASH_SPEED || dashTicksLeft <= 0) {
            // Reached target (or ran out of ticks) — stop and spawn end cluster
            this.setVelocity(0, 0, 0);
            this.velocityDirty = true;
            this.refreshPositionAndAngles(dashTargetX, dashTargetY, dashTargetZ,
                    this.getYaw(), this.getPitch());

            // Spawn 4-fang end cluster at the final position
            spawnFangsAt(world, dashTargetX + dashPerpX * 0.5, dashTargetY, dashTargetZ + dashPerpZ * 0.5, dashFangYaw);
            spawnFangsAt(world, dashTargetX - dashPerpX * 0.5, dashTargetY, dashTargetZ - dashPerpZ * 0.5, dashFangYaw);
            spawnFangsAt(world, dashTargetX + dashPerpX * 1.5, dashTargetY, dashTargetZ + dashPerpZ * 1.5, dashFangYaw);
            spawnFangsAt(world, dashTargetX - dashPerpX * 1.5, dashTargetY, dashTargetZ - dashPerpZ * 1.5, dashFangYaw);

            dashing = false;
            dashTicksLeft = 0;
            return;
        }

        // Move boss toward target by DASH_SPEED this tick
        double moveX = (dx / remaining) * DASH_SPEED;
        double moveZ = (dz / remaining) * DASH_SPEED;
        double newX = curX + moveX;
        double newZ = curZ + moveZ;
        this.refreshPositionAndAngles(newX, this.getY(), newZ, this.getYaw(), this.getPitch());
        // Maintain velocity so the client renders smooth movement
        this.setVelocity(moveX, 0.0, moveZ);
        this.velocityDirty = true;
        dashTicksLeft--;

        // Spawn 2 fangs at boss's CURRENT position if we've traveled ~1 block
        // since the last fang spawn. This gives a gradual fang trail.
        double fangDx = newX - dashLastFangX;
        double fangDz = newZ - dashLastFangZ;
        if (fangDx * fangDx + fangDz * fangDz >= 1.0) {
            spawnFangsAt(world, newX + dashPerpX * 0.5, this.getY(), newZ + dashPerpZ * 0.5, dashFangYaw);
            spawnFangsAt(world, newX - dashPerpX * 0.5, this.getY(), newZ - dashPerpZ * 0.5, dashFangYaw);
            dashLastFangX = newX;
            dashLastFangZ = newZ;
        }

        // Break blocks in a 3x3x3 box around boss's current position
        breakBlocksAround(world, newX, this.getY(), newZ);

        // Apply ×2 damage to entities in a box around boss's current position.
        // Uses mobAttack so armor reduces damage (per user spec).
        double dashDamage = this.getAttributeValue(EntityAttributes.ATTACK_DAMAGE) * 2.0;
        net.minecraft.util.math.Box box = new net.minecraft.util.math.Box(
                newX - 1.5, this.getY() - 1, newZ - 1.5,
                newX + 1.5, this.getY() + 3, newZ + 1.5);
        for (Entity e : world.getOtherEntities(this, box)) {
            if (e instanceof net.minecraft.entity.LivingEntity && e != this) {
                e.damage(world, world.getDamageSources().mobAttack(this),
                        (float) dashDamage);
            }
        }
    }

    /**
     * Break blocks in a 3x3x3 box around the given position.
     * Excludes obsidian, bedrock, and other unbreakable blocks.
     */
    private void breakBlocksAround(ServerWorld world, double cx, double cy, double cz) {
        BlockPos center = BlockPos.ofFloored(cx, cy, cz);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = center.add(dx, dy, dz);
                    BlockState state = world.getBlockState(pos);
                    if (state.isAir()) continue;
                    if (state.getBlock().getHardness() < 0) continue;
                    if (state.getBlock() == Blocks.OBSIDIAN) continue;
                    if (state.getBlock() == Blocks.CRYING_OBSIDIAN) continue;
                    if (state.getBlock() == Blocks.REINFORCED_DEEPSLATE) continue;
                    if (state.getBlock() == Blocks.NETHERITE_BLOCK) continue;
                    if (state.getBlock() == Blocks.ANCIENT_DEBRIS) continue;
                    world.breakBlock(pos, false);
                }
            }
        }
    }

    /**
     * Break blocks along dash path. Excludes obsidian, bedrock, and unbreakable blocks.
     */
    private void breakBlocksAlongPath(ServerWorld world, double x1, double z1, double x2, double z2) {
        Vec3d start = new Vec3d(x1, this.getY(), z1);
        Vec3d end = new Vec3d(x2, this.getY(), z2);
        Vec3d delta = end.subtract(start);
        int steps = (int) Math.ceil(delta.length()) + 1;
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double bx = start.x + delta.x * t;
            double bz = start.z + delta.z * t;
            // Break a 3x3 column at each step (ravager is ~2 blocks wide)
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 2; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos pos = BlockPos.ofFloored(bx + dx, this.getY() + dy, bz + dz);
                        BlockState state = world.getBlockState(pos);
                        if (state.isAir()) continue;
                        // Skip unbreakable blocks
                        if (state.getBlock().getHardness() < 0) continue; // bedrock
                        if (state.getBlock() == Blocks.OBSIDIAN) continue;
                        if (state.getBlock() == Blocks.CRYING_OBSIDIAN) continue;
                        if (state.getBlock() == Blocks.REINFORCED_DEEPSLATE) continue;
                        if (state.getBlock() == Blocks.NETHERITE_BLOCK) continue;
                        if (state.getBlock() == Blocks.ANCIENT_DEBRIS) continue;
                        // Break without drops (like Ravager does)
                        world.breakBlock(pos, false);
                    }
                }
            }
        }
    }

    /**
     * Spawn a single Evoker Fang at the given position.
     */
    private void spawnFangsAt(ServerWorld world, double x, double y, double z, float yaw) {
        // EvokerFangsEntity(World, x, y, z, yaw, warmup, owner)
        // warmup=0 → instant damage, no delay (was 20 = 1 second delay)
        EvokerFangsEntity fangs = new EvokerFangsEntity(world, x, y, z, yaw, 0, this);
        world.spawnEntity(fangs);
    }

    // === DEATH ===

    @Override
    public void onDeath(DamageSource source) {
        if (deathHandled) return;
        deathHandled = true;
        super.onDeath(source);

        // Clear boss bar
        bossBar.setPercent(0);
        bossBar.clearPlayers();

        ServerWorld world = (ServerWorld) this.getEntityWorld();

        // Announce death
        world.getServer().getPlayerManager().broadcast(
                Text.literal("§a§lРазрушитель Поселений повержен! Заберите награду!")
                        .formatted(Formatting.GREEN), false);

        // Give Loot Bag to each damage dealer in combat radius
        for (UUID uuid : damageDealers) {
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(uuid);
            if (player != null && player.squaredDistanceTo(this) < COMBAT_RADIUS * COMBAT_RADIUS) {
                ItemStack bag = LootBag.createBossBag();
                if (!player.getInventory().insertStack(bag)) {
                    player.dropItem(bag, false);
                }
                player.sendMessage(Text.literal("§aВы получили Мешок Босса! ПКМ — открыть")
                        .formatted(Formatting.GREEN), false);
            }
        }
    }

    /** Cancel equipment drops — Ravager has no equipment anyway. */
    // Note: dropEquipment signature varies across MC versions; we skip the
    // override entirely. RavagerEntity has no equipment slots by default.

    @Override
    public boolean isImmuneToExplosion(net.minecraft.world.explosion.Explosion explosion) {
        return true;
    }

    /**
     * Disguise as vanilla RAVAGER for client rendering.
     * The custom EntityType isn't known to vanilla/Bedrock clients, so we
     * send a spawn packet that claims this entity is a Ravager. The client
     * renders a Ravager with proper dimensions, hitbox, and animations.
     * The server still tracks the custom SettlementDestroyerEntity.
     */
    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket(EntityTrackerEntry entry) {
        return new EntitySpawnS2CPacket(
                this.getId(),
                this.getUuid(),
                entry.getPos().x, entry.getPos().y, entry.getPos().z,
                entry.getPitch(), entry.getYaw(),
                EntityType.RAVAGER,
                0,
                entry.getVelocity(),
                entry.getHeadYaw()
        );
    }

    /** Clear boss bar when entity is removed (chunk unload, /kill, etc.). */
    @Override
    public void onRemoved() {
        if (bossBar != null) {
            bossBar.setPercent(0);
            bossBar.clearPlayers();
        }
        super.onRemoved();
    }
}
