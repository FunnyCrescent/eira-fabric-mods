package com.leomordmod;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FireBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SkeletonHorseEntity;
import net.minecraft.entity.player.PlayerEntity;
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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;

/**
 * Leomord Horse — SkeletonHorse with custom "Fox Jump" mechanic.
 *
 * Phase 1 mount of Leomord boss. The horse has 100 HP.
 *
 * "Fox Jump" cycle:
 *   Every ~7 seconds (140 ticks), the horse jumps.
 *   On landing (was airborne, now on ground):
 *     - Knock up players in 4-block radius (apply upward velocity)
 *     - Ignite blocks in 4-block radius (set fire to air blocks above solid blocks)
 *     - Deal contact damage to players in 4-block radius (reduced by armor)
 *
 * The horse carries Leomord while alive. When the horse dies, Leomord
 * dismounts automatically and transitions to Phase 2.
 */
public class LeomordHorseEntity extends SkeletonHorseEntity {

    /** The horse has 100 HP (spec). */
    private static final float HORSE_HP = 100.0f;

    /** Jump interval — every 140 ticks (7 seconds). */
    private static final int JUMP_INTERVAL_TICKS = 140;
    private int jumpCooldown = JUMP_INTERVAL_TICKS; // jump on first cycle

    /** Track previous Y for landing detection. */
    private boolean wasInAir = false;
    /** True when horse is in mid-fox-jump (set on jump, cleared on landing).
     *  Used to ensure explosion triggers ONLY after a deliberate fox jump —
     *  not after walking off a cliff or falling for any other reason. */
    private boolean isFoxJumping = false;

    /** Cached reference to the Leomord boss riding this horse (may be null briefly). */
    private LeomordEntity cachedBoss = null;

    public LeomordHorseEntity(EntityType<? extends SkeletonHorseEntity> entityType, World world) {
        super(entityType, world);
        this.setCustomName(Text.literal("Конь Леоморда").formatted(Formatting.DARK_RED, Formatting.BOLD));
        this.setCustomNameVisible(true);
        this.setPersistent();
        // Don't call setTame(true) — tamed horses wait for player input and
        // don't wander/chase. Wild skeleton horses have full AI.
        this.setAiDisabled(false);
    }

    public static net.minecraft.entity.attribute.DefaultAttributeContainer.Builder createLeomordHorseAttributes() {
        return SkeletonHorseEntity.createSkeletonHorseAttributes()
                .add(EntityAttributes.MAX_HEALTH, HORSE_HP)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.35)
                .add(EntityAttributes.JUMP_STRENGTH, 1.0) // max jump
                .add(EntityAttributes.KNOCKBACK_RESISTANCE, 1.0)
                // CRITICAL — MeleeAttackGoal calls mob.tryAttack(target) which
                // reads ATTACK_DAMAGE attribute. Without this attribute registered,
                // the horse crashes with "Can't find attribute minecraft:attack_damage"
                // the first time the AI tries to attack.
                .add(EntityAttributes.ATTACK_DAMAGE, 6.0);
    }

    @Override
    protected void initGoals() {
        // Clear vanilla goals (including SkeletonHorseTrapTriggerGoal which can
        // interfere with custom AI). Then add our own.
        this.goalSelector.clear(g -> true);
        this.targetSelector.clear(g -> true);

        // === Target selector — chase enemies ===
        this.targetSelector.add(1, new net.minecraft.entity.ai.goal.RevengeGoal(this));
        // Target any LivingEntity (player OR Larry/mob), excluding creative
        // players, spectators, peaceful animals, and other LeomordHorses.
        this.targetSelector.add(2, new net.minecraft.entity.ai.goal.ActiveTargetGoal<>(
                this, net.minecraft.entity.LivingEntity.class, 10, true, true,
                (target, world) -> isValidHorseTarget(target)));

        // === Goal selector — chase + attack + wander ===
        // MeleeAttackGoal pathfinds to the target so the horse carries the boss
        // toward the player. Without this, the horse just wanders randomly.
        this.goalSelector.add(1, new net.minecraft.entity.ai.goal.MeleeAttackGoal(this, 1.0, true));
        this.goalSelector.add(5, new net.minecraft.entity.ai.goal.WanderAroundFarGoal(this, 0.8));
        this.goalSelector.add(6, new net.minecraft.entity.ai.goal.LookAtEntityGoal(this, PlayerEntity.class, 20.0f));
    }

    /** Valid target predicate for Phase 1 horse — same as Leomord's predicate. */
    private boolean isValidHorseTarget(net.minecraft.entity.LivingEntity target) {
        if (target == this) return false;
        if (target.isDead()) return false;
        if (target.isSpectator()) return false;
        if (target instanceof PlayerEntity p && p.isCreative()) return false;
        // Don't attack other LeomordHorses (own kind)
        if (target instanceof LeomordHorseEntity) return false;
        // Skip peaceful animals
        net.minecraft.entity.EntityType<?> type = target.getType();
        if (type == net.minecraft.entity.EntityType.SHEEP
                || type == net.minecraft.entity.EntityType.COW
                || type == net.minecraft.entity.EntityType.PIG
                || type == net.minecraft.entity.EntityType.CHICKEN
                || type == net.minecraft.entity.EntityType.HORSE
                || type == net.minecraft.entity.EntityType.DONKEY
                || type == net.minecraft.entity.EntityType.MULE
                || type == net.minecraft.entity.EntityType.VILLAGER
                || type == net.minecraft.entity.EntityType.WANDERING_TRADER
                || type == net.minecraft.entity.EntityType.SKELETON_HORSE
                || type == net.minecraft.entity.EntityType.ZOMBIE_HORSE) return false;
        return true;
    }

    @Override
    public void tickMovement() {
        super.tickMovement();
        if (this.getEntityWorld().isClient()) return;
        ServerWorld sw = (ServerWorld) this.getEntityWorld();

        // === DEATH HORSES — skip Phase 1 logic entirely ===
        // Death horses are spawned by LeomordEntity's death sequence with the
        // "LeomordDeathHorse" command tag. They should just fall via gravity
        // (no fox jumps, no Phase 1 landing impact). The death sequence's
        // tickDeathSequence handles all the explosion/block-break logic.
        if (this.getCommandTags().contains("LeomordDeathHorse")) {
            return;
        }

        // Cache the boss reference if riding
        if (cachedBoss == null) {
            Entity passenger = this.getFirstPassenger();
            if (passenger instanceof LeomordEntity leomord) {
                cachedBoss = leomord;
            }
        }

        // === Fox Jump cycle ===
        jumpCooldown--;
        if (jumpCooldown <= 0 && this.isOnGround()) {
            jumpCooldown = JUMP_INTERVAL_TICKS;
            isFoxJumping = true;  // mark that we're performing a fox jump
            performFoxJump(sw);
        }

        // === Landing detection — only trigger impact if it was a fox jump ===
        boolean inAir = !this.isOnGround();
        if (wasInAir && !inAir) {
            // Landed — but only trigger impact if this was a deliberate fox jump
            // (not just walking off a cliff or being pushed)
            if (isFoxJumping) {
                onLandingImpact(sw);
            }
            isFoxJumping = false;
        }
        wasInAir = inAir;
    }

    /**
     * Death horses must be invulnerable to player attacks (including Three
     * Fangs trident) so they can fall to the target's Y level without being
     * killed on the way. But /kill must still work for cleanup.
     *
     * Implementation: allow damage sources in the BYPASSES_INVULNERABILITY
     * tag (which includes OUT_OF_WORLD = /kill damage), block everything else.
     */
    @Override
    public boolean isInvulnerableTo(ServerWorld world, DamageSource source) {
        if (this.getCommandTags().contains("LeomordDeathHorse")) {
            // Allow /kill and other "bypass invulnerability" damage
            if (source.isIn(net.minecraft.registry.tag.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                return false;
            }
            return true;  // block all other damage
        }
        return super.isInvulnerableTo(world, source);
    }

    /**
     * Perform the "Fox Jump" — a leap with high upward velocity.
     * The horse jumps toward its target (or forward if no target).
     */
    private void performFoxJump(ServerWorld world) {
        // Find a target player to leap toward
        PlayerEntity target = null;
        double bestDist = 30.0 * 30.0;
        for (PlayerEntity p : world.getPlayers()) {
            if (!p.isAlive() || p.isSpectator()) continue;
            double d = p.squaredDistanceTo(this);
            if (d < bestDist) {
                bestDist = d;
                target = p;
            }
        }

        double dx, dz;
        if (target != null) {
            dx = target.getX() - this.getX();
            dz = target.getZ() - this.getZ();
            double dist = Math.sqrt(dx*dx + dz*dz);
            if (dist > 0.001) { dx /= dist; dz /= dist; }
        } else {
            float yaw = this.getYaw();
            dx = -Math.sin(Math.toRadians(yaw));
            dz = Math.cos(Math.toRadians(yaw));
        }

        // High upward velocity + horizontal toward target
        // Fox pounce base velocity: 0.5-0.7 vertical
        this.setVelocity(dx * 0.6, 1.0, dz * 0.6);
        this.velocityDirty = true;
        this.playSound(SoundEvents.ENTITY_FOX_AGGRO, 1.5f, 0.5f);
    }

    /**
     * On landing impact (per user spec iteration 5):
     *   1. TNT explosion at landing point — guaranteed no block break.
     *   2. Wind burst effect — manually apply upward velocity to nearby
     *      LivingEntity (no WindChargeEntity that flies up uselessly).
     *   3. Place pointed dripstone stalagmites in 4-block radius for 2s.
     *   4. Contact damage to nearby LivingEntity (reduced by armor).
     */
    private void onLandingImpact(ServerWorld world) {
        double radius = 4.0;
        double cx = this.getX();
        double cy = this.getY();
        double cz = this.getZ();

        this.playSound(SoundEvents.ENTITY_RAVAGER_ROAR, 2.0f, 0.6f);
        world.spawnParticles(net.minecraft.particle.ParticleTypes.LARGE_SMOKE, cx, cy, cz,
                20, 1.0, 0.5, 1.0, 0.05);

        // === 1. TNT explosion (no block break) ===
        LeomordProjectiles.triggerExplosion(world, this, cx, cy + 0.5, cz);

        // === 2. Wind burst effect — manual upward knock-up ===
        // Pass `this` as source so the horse doesn't bounce itself into an
        // infinite jump loop (it would otherwise be knocked up by its own burst).
        LeomordProjectiles.triggerWindBurst(world, this, cx, cy, cz, 4.0);

        // === 3. Place pointed dripstone stalagmites in 4-block radius ===
        BlockPos center = BlockPos.ofFloored(cx, cy, cz);
        java.util.List<BlockPos> placedDripstones = new java.util.ArrayList<>();
        for (int bx = -4; bx <= 4; bx++) {
            for (int bz = -4; bz <= 4; bz++) {
                if (bx*bx + bz*bz > 16) continue; // circle
                BlockPos pos = center.add(bx, 1, bz);
                BlockState state = world.getBlockState(pos);
                if (state.isAir()) {
                    BlockPos below = pos.down();
                    BlockState belowState = world.getBlockState(below);
                    if (belowState.isSolidBlock(world, below) && belowState.isOpaque()) {
                        BlockState dripstone = Blocks.POINTED_DRIPSTONE.getDefaultState()
                                .with(net.minecraft.state.property.Properties.VERTICAL_DIRECTION,
                                        net.minecraft.util.math.Direction.UP)
                                .with(net.minecraft.state.property.Properties.THICKNESS,
                                        net.minecraft.block.enums.Thickness.TIP);
                        world.setBlockState(pos, dripstone);
                        placedDripstones.add(pos);
                    }
                }
            }
        }
        if (!placedDripstones.isEmpty()) {
            DripstoneRemoval.schedule(world, placedDripstones, 40);
        }

        // === 4. Contact damage (reduced by armor) ===
        double r2 = radius * radius;
        Box box = new Box(cx - radius, cy - 2, cz - radius,
                          cx + radius, cy + 3, cz + radius);
        for (PlayerEntity p : world.getPlayers()) {
            if (p.isSpectator() || !p.isAlive()) continue;
            if (p.squaredDistanceTo(cx, cy, cz) > r2) continue;
            p.damage(world, world.getDamageSources().mobAttack(this), 6.0f);
        }

        world.spawnParticles(net.minecraft.particle.ParticleTypes.DUST_PLUME, cx, cy + 0.1, cz,
                30, 2.0, 0.1, 2.0, 0.0);
    }

    /**
     * SkeletonHorse is fire-immune by default (it's an undead). Confirm
     * we also are immune to lava (lava is fire damage).
     */
    @Override
    public boolean isFireImmune() {
        return true;
    }

    /**
     * Prevent the horse from dropping experience or items.
     */
    @Override
    protected void dropLoot(ServerWorld world, DamageSource source, boolean causedByPlayer) {
        // No drops — horse is a boss mechanic, not a loot source
    }

    @Override
    protected int getExperienceToDrop(ServerWorld world) {
        return 0;
    }

    /**
     * When the horse dies, notify the boss (if cached) to start Phase 2.
     */
    @Override
    public void onDeath(DamageSource source) {
        super.onDeath(source);
        if (cachedBoss != null && cachedBoss.isAlive()) {
            cachedBoss.onHorseDeath((ServerWorld) this.getEntityWorld());
        }
    }

    /**
     * Disguise as vanilla SKELETON_HORSE for client rendering.
     *
     * CRITICAL for Bedrock/Geyser clients: the custom EntityType
     * `leomordmod:leomord_horse` is not known to vanilla/Bedrock clients,
     * so without this override, Geyser can't translate the spawn packet
     * and Bedrock players see nothing (the horse is invisible).
     *
     * With this override, the spawn packet claims the entity is a vanilla
     * SKELETON_HORSE. The client renders a skeleton horse; the server still
     * tracks the custom LeomordHorseEntity class for behavior.
     */
    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket(EntityTrackerEntry entry) {
        return new EntitySpawnS2CPacket(
                this.getId(),
                this.getUuid(),
                entry.getPos().x, entry.getPos().y, entry.getPos().z,
                entry.getPitch(), entry.getYaw(),
                EntityType.SKELETON_HORSE,
                0,
                entry.getVelocity(),
                entry.getHeadYaw()
        );
    }

    /**
     * Override the passenger attachment position so the rider (Leomord, a tall
     * wither skeleton ~2.4 blocks) sits on the horse's back instead of having
     * its feet inside the horse's body.
     *
     * Vanilla SkeletonHorse's default attachment puts the rider's feet at
     * approximately Y = horse.y + 0.65 (which is INSIDE the horse's 1.6-tall
     * body). For our tall rider, we want the rider's feet above the horse's
     * back so the boss visibly sits on the saddle.
     *
     * The offset returned here is relative to the horse's position (which is
     * at the horse's feet Y). Y = 1.35 puts the rider's feet ~0.25 above the
     * horse's back, so the boss is visibly mounted.
     */
    @Override
    protected net.minecraft.util.math.Vec3d getPassengerAttachmentPos(
            Entity passenger, net.minecraft.entity.EntityDimensions dimensions, float tickDelta) {
        // Custom Y offset — sit on the saddle instead of inside the horse
        if (passenger instanceof LeomordEntity) {
            return new net.minecraft.util.math.Vec3d(0.0, 1.35, -0.15);
        }
        // For other passengers (e.g., player riding the horse), use default
        return super.getPassengerAttachmentPos(passenger, dimensions, tickDelta);
    }
}
