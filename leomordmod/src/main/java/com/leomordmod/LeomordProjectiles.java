package com.leomordmod;

import com.leomordmod.mixin.PersistentProjectileEntityAccessor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Leomord Projectiles — runtime tracking of boss arrows + stun system + wind burst.
 *
 * Projectiles:
 *   - LeomordArrow    : TNT-radius explosion (no block break), stun (players only),
 *     fireball cross at each enemy in aggro radius (multi-target)
 *   - GetOverHere     : pulls the hit entity to the boss on entity hit
 *   - SprayArrow      : explosion only, no stun, no fireballs
 *
 * Stun: pure server-side — zeroes XZ velocity via PlayerStunMixin. Applies
 * Weakness IV + Mining Fatigue IV (NO Slowness — avoids FOV distortion,
 * NO Blindness per user request — just clean stun).
 *
 * Wind burst effect: manually creates the upward-knock-up + particles that
 * vanilla WindCharge produces, WITHOUT spawning a WindChargeEntity (which
 * flies upward uselessly). Used by horse landing and death sequence.
 */
public final class LeomordProjectiles {

    private LeomordProjectiles() {}

    public static final String ARROW_TAG = "LeomordArrow";
    public static final String PULL_TAG  = "LeomordGetOverHere";
    public static final String SPRAY_TAG = "LeomordSpray";

    // === Stun tracking ===
    private static final Map<UUID, Long> stunMap = new ConcurrentHashMap<>();

    // === Hovering fireballs waiting to launch ===
    private static final List<HoveringFireball> hoveringFireballs = new ArrayList<>();
    // === Homing fireballs in flight (track target every tick) ===
    private static final List<HomingFireball> homingFireballs = new ArrayList<>();

    // ====================================================================
    // SPAWN METHODS
    // ====================================================================

    public static void spawnLeomordArrow(ServerWorld world, LivingEntity boss, LivingEntity target) {
        ArrowEntity arrow = new ArrowEntity(world, boss, ItemStack.EMPTY, new ItemStack(Items.ARROW));
        arrow.addCommandTag(ARROW_TAG);
        aimArrow(arrow, boss, target, 2.5);
        arrow.pickupType = PersistentProjectileEntity.PickupPermission.DISALLOWED;
        world.spawnEntity(arrow);
        world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.HOSTILE, 1.5f, 0.5f);
    }

    public static void spawnGetOverHere(ServerWorld world, LivingEntity boss, LivingEntity target) {
        ArrowEntity arrow = new ArrowEntity(world, boss, ItemStack.EMPTY, new ItemStack(Items.ARROW));
        arrow.addCommandTag(PULL_TAG);
        aimArrow(arrow, boss, target, 2.0);
        arrow.pickupType = PersistentProjectileEntity.PickupPermission.DISALLOWED;
        world.spawnEntity(arrow);
        world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.HOSTILE, 2.0f, 0.7f);
    }

    public static void spawnSprayArrow(ServerWorld world, LivingEntity boss, LivingEntity target) {
        ArrowEntity arrow = new ArrowEntity(world, boss, ItemStack.EMPTY, new ItemStack(Items.ARROW));
        arrow.addCommandTag(SPRAY_TAG);
        aimArrow(arrow, boss, target, 2.5);
        arrow.pickupType = PersistentProjectileEntity.PickupPermission.DISALLOWED;
        world.spawnEntity(arrow);
    }

    /**
     * Spawn N Get Over Here arrows above the boss's head, each aimed at a
     * random point around the target enemy (within 4-block sphere). Makes
     * it easier to hit a moving target.
     */
    public static void spawnGetOverHereMultiShot(ServerWorld world, LivingEntity boss,
                                                  LivingEntity target, int count) {
        double baseX = boss.getX();
        double baseY = boss.getY() + 4.0;
        double baseZ = boss.getZ();
        for (int i = 0; i < count; i++) {
            double angle = (i * 2 * Math.PI) / count + world.getRandom().nextDouble() * 0.5;
            double radius = 1.5 + world.getRandom().nextDouble() * 2.5;
            double targetX = target.getX() + Math.cos(angle) * radius;
            double targetY = target.getY() + (world.getRandom().nextDouble() - 0.5) * 2.0;
            double targetZ = target.getZ() + Math.sin(angle) * radius;

            ArrowEntity arrow = new ArrowEntity(world, boss, ItemStack.EMPTY, new ItemStack(Items.ARROW));
            arrow.addCommandTag(PULL_TAG);
            arrow.refreshPositionAndAngles(baseX, baseY, baseZ, 0, 0);
            double dx = targetX - baseX;
            double dy = targetY - baseY;
            double dz = targetZ - baseZ;
            double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
            if (dist < 0.001) dist = 1.0;
            double speed = 2.0;
            arrow.setVelocity(dx/dist * speed, dy/dist * speed, dz/dist * speed);
            arrow.pickupType = PersistentProjectileEntity.PickupPermission.DISALLOWED;
            world.spawnEntity(arrow);
        }
        world.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.HOSTILE, 3.0f, 0.5f);
    }

    private static void aimArrow(ArrowEntity arrow, LivingEntity boss, LivingEntity target, double speed) {
        double dx = target.getX() - boss.getX();
        double dy = (target.getY() + target.getStandingEyeHeight() * 0.6) - boss.getEyeY();
        double dz = target.getZ() - boss.getZ();
        double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
        if (dist < 0.001) dist = 1.0;
        arrow.setVelocity(dx/dist * speed, dy/dist * speed, dz/dist * speed);
    }

    // ====================================================================
    // HIT HANDLERS — called from PersistentProjectileHitMixin
    // ====================================================================

    /**
     * LeomordArrow hit an entity:
     *   - Apply TNT-radius explosion (no block break) at impact point.
     *   - If hit is a player → apply stun (45 ticks = 2.25s).
     *   - Spawn fireball cross above EACH enemy in aggro radius of the boss
     *     (multi-target per user spec — fireballs hit every enemy, not just
     *     the one hit by the arrow).
     */
    public static void handleArrowEntityHit(ArrowEntity arrow, Entity hit) {
        ServerWorld world = (ServerWorld) arrow.getEntityWorld();
        double ix = arrow.getX();
        double iy = arrow.getY();
        double iz = arrow.getZ();

        triggerExplosion(world, arrow, ix, iy, iz);

        // Stun only players (Larrys and other mobs don't get stunned)
        if (hit instanceof PlayerEntity player && player.isAlive()) {
            applyStun(player, 45);
        }

        // Fireballs spawn above EACH enemy in aggro radius of the boss
        Entity owner = arrow.getOwner();
        if (owner instanceof LeomordEntity boss && boss.isAlive()) {
            List<LivingEntity> enemies = findEnemiesInRadius(world, boss, boss.AGGRO_RADIUS);
            for (LivingEntity enemy : enemies) {
                spawnCrossFireballs(world, enemy);
            }
        }
    }

    public static void handleArrowBlockHit(ArrowEntity arrow) {
        ServerWorld world = (ServerWorld) arrow.getEntityWorld();
        triggerExplosion(world, arrow, arrow.getX(), arrow.getY(), arrow.getZ());
    }

    /**
     * Get Over Here hit:
     *   - If hit is a LivingEntity (player OR Larry) → teleport THEM to the boss
     *     (in front of boss), facing the boss. NOT the boss to them.
     *   - Mark player as "pulled" for x2 combo window (only players get combo).
     *   - Notify boss the pull succeeded (for phase 3 state machine).
     */
    public static void handlePullEntityHit(ArrowEntity arrow, Entity hit) {
        if (!(hit instanceof LivingEntity livingHit)) return;
        if (!livingHit.isAlive()) return;

        Entity owner = arrow.getOwner();
        if (!(owner instanceof LeomordEntity boss)) return;
        if (!boss.isAlive()) return;

        // Defensive: if hit IS the boss (arrow somehow hit its own owner), skip
        if (livingHit == boss) return;

        // Teleport hit entity to boss (2 blocks in front of boss, facing boss)
        float yaw = boss.getYaw();
        double tx = boss.getX() + (-Math.sin(Math.toRadians(yaw)) * 2.0);
        double ty = boss.getY();
        double tz = boss.getZ() + (Math.cos(Math.toRadians(yaw)) * 2.0);
        // Hit entity faces the boss: rotate 180° from boss's yaw
        livingHit.refreshPositionAndAngles(tx, ty, tz, boss.getYaw() + 180, 0);
        livingHit.setVelocity(0, 0, 0);

        // Mark player as pulled for x2 combo (only players)
        if (livingHit instanceof PlayerEntity player) {
            boss.markPulled(player.getUuid(), worldTick(arrow));
        }

        ServerWorld world = (ServerWorld) arrow.getEntityWorld();
        world.spawnParticles(ParticleTypes.REVERSE_PORTAL,
                livingHit.getX(), livingHit.getY() + 1, livingHit.getZ(),
                30, 0.5, 1.0, 0.5, 0.1);
        world.playSound(null, arrow.getX(), arrow.getY(), arrow.getZ(),
                SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1.5f, 0.6f);

        boss.notifyPullHit();
    }

    public static void handleSprayEntityHit(ArrowEntity arrow, Entity hit) {
        ServerWorld world = (ServerWorld) arrow.getEntityWorld();
        triggerExplosion(world, arrow, arrow.getX(), arrow.getY(), arrow.getZ());
    }

    public static void handleSprayBlockHit(ArrowEntity arrow) {
        ServerWorld world = (ServerWorld) arrow.getEntityWorld();
        triggerExplosion(world, arrow, arrow.getX(), arrow.getY(), arrow.getZ());
    }

    private static long worldTick(Entity entity) {
        if (entity.getEntityWorld() instanceof ServerWorld sw) {
            return sw.getServer().getTicks();
        }
        return 0;
    }

    // ====================================================================
    // EXPLOSION — guaranteed no block break, with entity damage
    // ====================================================================

    /**
     * Trigger a TNT-radius explosion with NO block breaking. Source entity is
     * EXCLUDED from damage — so Leomord doesn't kill himself with his own arrows,
     * and a horse doesn't damage itself.
     */
    public static void triggerExplosion(ServerWorld world, Entity source, double x, double y, double z) {
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 0, 0, 0, 0, 0);
        world.spawnParticles(ParticleTypes.FLAME, x, y, z, 30, 1.0, 1.0, 1.0, 0.3);
        world.playSound(null, x, y, z, SoundEvents.ENTITY_GENERIC_EXPLODE,
                SoundCategory.HOSTILE, 2.0f, 0.8f);

        // NONE = guaranteed no block break + no built-in entity damage.
        // We manually damage entities in the radius for the "TNT feel".
        world.createExplosion(source, x, y, z, 4.0f, false,
                World.ExplosionSourceType.NONE);

        // Manual entity damage in 4-block (TNT) radius — reduced by armor via mobAttack.
        // Source is excluded from the getOtherEntities() call so the boss/horse
        // doesn't damage itself.
        LivingEntity owner = (source instanceof LivingEntity) ? (LivingEntity) source : null;
        double radius = 4.0;
        Box box = new Box(x - radius, y - radius, z - radius,
                          x + radius, y + radius, z + radius);
        DamageSource dmg = world.getDamageSources().mobAttack(owner);
        for (Entity e : world.getOtherEntities(source, box)) {
            if (!(e instanceof LivingEntity living)) continue;
            double dist = e.squaredDistanceTo(x, y, z);
            if (dist > radius * radius) continue;
            float damage = 12.0f * (1.0f - (float) (dist / (radius * radius)));
            living.damage(world, dmg, damage);
        }
    }

    // ====================================================================
    // WIND BURST — manual knock-up effect (no WindCharge entity)
    // ====================================================================

    /**
     * Trigger a wind burst effect — throws nearby LivingEntity UP (just like
     * vanilla WindCharge's explosion would, but WITHOUT spawning a WindChargeEntity
     * that flies upward uselessly).
     *
     * `source` is excluded from the knock-up effect (so the horse that triggered
     * the burst doesn't bounce itself into an infinite jump loop).
     */
    public static void triggerWindBurst(ServerWorld world, Entity source, double x, double y, double z, double radius) {
        // Visual + audio (matching vanilla wind charge burst)
        world.spawnParticles(ParticleTypes.GUST, x, y + 0.5, z,
                30, radius * 0.3, 0.5, radius * 0.3, 0.0);
        world.spawnParticles(ParticleTypes.GUST_EMITTER_LARGE, x, y + 0.5, z,
                5, 0.0, 0.0, 0.0, 0.0);
        world.playSound(null, x, y, z, SoundEvents.ENTITY_WIND_CHARGE_WIND_BURST,
                SoundCategory.HOSTILE, 1.5f, 1.0f);

        // Apply upward velocity to all LivingEntity in radius (EXCEPT source)
        double r2 = radius * radius;
        Box box = new Box(x - radius, y - radius, z - radius,
                          x + radius, y + radius, z + radius);
        for (Entity e : world.getOtherEntities(source, box)) {
            if (!(e instanceof LivingEntity living)) continue;
            if (e.squaredDistanceTo(x, y, z) > r2) continue;
            Vec3d cur = living.getVelocity();
            double dist = Math.sqrt(e.squaredDistanceTo(x, y, z));
            double factor = 1.0 - Math.min(1.0, dist / radius);
            living.setVelocity(cur.x * 0.3, 0.6 + factor * 0.5, cur.z * 0.3);
            living.velocityDirty = true;
        }
    }

    // ====================================================================
    // STUN — server-side tracking, no Blindness (per user spec)
    // ====================================================================

    /**
     * Apply a stun to the player for `durationTicks` ticks.
     *
     * Implementation (per user spec — "ПРОСТО СТАН, без слепоты"):
     *   - stunMap<UUID, Long> tracks expiry tick (server-side, in-memory).
     *   - Weakness IV + Mining Fatigue IV potions (no FOV change).
     *   - PlayerStunMixin zeroes XZ velocity every tick.
     *
     * No Slowness (FOV distortion per user complaint).
     * No Blindness (user explicitly removed it in iteration 4).
     */
    public static void applyStun(PlayerEntity player, int durationTicks) {
        long expiryTick;
        if (player.getEntityWorld() instanceof ServerWorld sw) {
            expiryTick = sw.getServer().getTicks() + durationTicks;
        } else {
            expiryTick = System.currentTimeMillis() / 50 + durationTicks;
        }
        stunMap.put(player.getUuid(), expiryTick);

        // Only Weakness (no melee damage) + Mining Fatigue (no block breaking).
        // NO Slowness — FOV distortion.
        // NO Blindness — user explicitly removed this.
        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.WEAKNESS, durationTicks, 4, false, false, true));
        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.MINING_FATIGUE, durationTicks, 4, false, false, true));
        var cur = player.getVelocity();
        player.setVelocity(0.0, Math.min(cur.y, -0.1), 0.0);
    }

    public static boolean isStunned(UUID uuid) {
        return stunMap.containsKey(uuid);
    }

    public static void tickStun(UUID uuid) {
        // Expiry handled in tick() (called from ServerTickEvents.END_SERVER_TICK)
    }

    // ====================================================================
    // ENEMY FINDER — used by Phase 3 multi-target logic
    // ====================================================================

    /**
     * Find all valid enemies in radius of the source entity.
     *
     * "Valid enemy" = LivingEntity that:
     *   - is alive
     *   - is not the source itself
     *   - is not a creative-mode player (per user spec — don't attack creative players)
     *   - is not a spectator
     *   - is not a peaceful animal (the boss doesn't attack passive mobs)
     *
     * Includes: players (survival), Larrys, zombies, skeletons, etc.
     */
    public static List<LivingEntity> findEnemiesInRadius(ServerWorld world, LivingEntity source, double radius) {
        List<LivingEntity> result = new ArrayList<>();
        double r2 = radius * radius;
        Box box = new Box(source.getX() - radius, source.getY() - radius, source.getZ() - radius,
                          source.getX() + radius, source.getY() + radius, source.getZ() + radius);
        for (Entity e : world.getOtherEntities(source, box)) {
            if (!(e instanceof LivingEntity living)) continue;
            if (!living.isAlive()) continue;
            if (living.isSpectator()) continue;
            // Skip creative-mode players
            if (living instanceof PlayerEntity p && p.isCreative()) continue;
            // Skip LeomordHorseEntity instances (boss's own horses + leftover
            // death horses from previous Leomords)
            if (living instanceof LeomordHorseEntity) continue;
            // Skip peaceful animals (matches vulcadavmod pattern)
            if (isPeacefulAnimal(living)) continue;
            if (living.squaredDistanceTo(source) > r2) continue;
            result.add(living);
        }
        return result;
    }

    private static boolean isPeacefulAnimal(LivingEntity entity) {
        net.minecraft.entity.EntityType<?> type = entity.getType();
        return type == net.minecraft.entity.EntityType.SHEEP
                || type == net.minecraft.entity.EntityType.COW
                || type == net.minecraft.entity.EntityType.PIG
                || type == net.minecraft.entity.EntityType.CHICKEN
                || type == net.minecraft.entity.EntityType.RABBIT
                || type == net.minecraft.entity.EntityType.HORSE
                || type == net.minecraft.entity.EntityType.DONKEY
                || type == net.minecraft.entity.EntityType.MULE
                || type == net.minecraft.entity.EntityType.CAT
                || type == net.minecraft.entity.EntityType.PARROT
                || type == net.minecraft.entity.EntityType.OCELOT
                || type == net.minecraft.entity.EntityType.BEE
                || type == net.minecraft.entity.EntityType.TURTLE
                || type == net.minecraft.entity.EntityType.PANDA
                || type == net.minecraft.entity.EntityType.FROG
                || type == net.minecraft.entity.EntityType.ALLAY
                || type == net.minecraft.entity.EntityType.SNIFFER
                || type == net.minecraft.entity.EntityType.AXOLOTL
                || type == net.minecraft.entity.EntityType.GOAT
                || type == net.minecraft.entity.EntityType.CAMEL
                || type == net.minecraft.entity.EntityType.ARMADILLO
                || type == net.minecraft.entity.EntityType.MOOSHROOM
                || type == net.minecraft.entity.EntityType.BAT
                || type == net.minecraft.entity.EntityType.SQUID
                || type == net.minecraft.entity.EntityType.GLOW_SQUID
                || type == net.minecraft.entity.EntityType.COD
                || type == net.minecraft.entity.EntityType.SALMON
                || type == net.minecraft.entity.EntityType.PUFFERFISH
                || type == net.minecraft.entity.EntityType.TROPICAL_FISH
                || type == net.minecraft.entity.EntityType.DOLPHIN
                || type == net.minecraft.entity.EntityType.WANDERING_TRADER
                || type == net.minecraft.entity.EntityType.VILLAGER
                || type == net.minecraft.entity.EntityType.SKELETON_HORSE  // skip our death horses
                || type == net.minecraft.entity.EntityType.ZOMBIE_HORSE;
    }

    // ====================================================================
    // FIREBALL CROSS — spawned at 10 blocks above an enemy
    // ====================================================================

    /**
     * Public accessor — called from PersistentProjectileHitMixin's
     * handleFireBowArrowHit when a Fire Bow arrow hits a LivingEntity.
     * Spawns 4 homing fireballs at 10 blocks above the hit entity.
     */
    public static void spawnCrossFireballsForTarget(ServerWorld world, LivingEntity target) {
        spawnCrossFireballs(world, target);
    }

    private static void spawnCrossFireballs(ServerWorld world, LivingEntity target) {
        double baseX = target.getX();
        double baseY = target.getY() + 10.0;
        double baseZ = target.getZ();
        Vec3d[] offsets = {
                new Vec3d(5, 0, 0), new Vec3d(-5, 0, 0),
                new Vec3d(0, 0, 5), new Vec3d(0, 0, -5)
        };
        long launchTick;
        if (world.getServer() != null) {
            launchTick = world.getServer().getTicks() + 40; // 2s hover
        } else {
            launchTick = 0;
        }
        UUID targetId = target.getUuid();
        for (Vec3d off : offsets) {
            net.minecraft.entity.projectile.SmallFireballEntity fb =
                    new net.minecraft.entity.projectile.SmallFireballEntity(
                            world, baseX + off.x, baseY, baseZ + off.z,
                            new Vec3d(0, 0, 0));
            fb.setVelocity(0, 0, 0);
            fb.addCommandTag("LeomordFireball");
            fb.setNoGravity(true);
            world.spawnEntity(fb);
            hoveringFireballs.add(new HoveringFireball(fb.getUuid(), launchTick, targetId));
        }
        // (targetId referenced as targetUuid in HoveringFireball — works for any LivingEntity, not just players)
        world.playSound(null, baseX, baseY, baseZ, SoundEvents.ENTITY_BLAZE_SHOOT,
                SoundCategory.HOSTILE, 2.0f, 0.5f);
    }

    // ====================================================================
    // TICK — fireball launches + stun cleanup
    // ====================================================================

    public static void tick(ServerWorld world) {
        long currentTick = world.getServer().getTicks();

        // === Launch hovering fireballs whose timer expired ===
        java.util.Iterator<HoveringFireball> it = hoveringFireballs.iterator();
        while (it.hasNext()) {
            HoveringFireball hf = it.next();
            if (currentTick < hf.launchTick) continue;

            net.minecraft.entity.projectile.SmallFireballEntity fb = null;
            for (net.minecraft.entity.projectile.SmallFireballEntity e :
                    world.getEntitiesByType(net.minecraft.entity.EntityType.SMALL_FIREBALL, e2 -> e2.isAlive())) {
                if (e.getUuid().equals(hf.fireballUuid)) {
                    fb = e;
                    break;
                }
            }
            if (fb == null) {
                it.remove();
                continue;
            }
            LivingEntity target = resolveTarget(world, hf.targetUuid);
            if (target == null) {
                fb.discard();
                it.remove();
                continue;
            }
            // Initial velocity toward target
            double dx = target.getX() - fb.getX();
            double dy = (target.getY() + target.getStandingEyeHeight() * 0.6) - fb.getY();
            double dz = target.getZ() - fb.getZ();
            double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
            if (dist < 0.001) dist = 1.0;
            double speed = 0.8;
            fb.setVelocity(dx/dist * speed, dy/dist * speed, dz/dist * speed);
            fb.setNoGravity(false);
            world.playSound(null, fb.getX(), fb.getY(), fb.getZ(),
                    SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 1.5f, 1.0f);
            // Move to homing list for continuous tracking
            homingFireballs.add(new HomingFireball(fb.getUuid(), hf.targetUuid));
            it.remove();
        }
        if (hoveringFireballs.size() > 500) hoveringFireballs.clear();

        // === Update homing fireballs — adjust velocity toward target's CURRENT position every tick ===
        java.util.Iterator<HomingFireball> hit = homingFireballs.iterator();
        while (hit.hasNext()) {
            HomingFireball hf = hit.next();
            net.minecraft.entity.projectile.SmallFireballEntity fb = null;
            for (net.minecraft.entity.projectile.SmallFireballEntity e :
                    world.getEntitiesByType(net.minecraft.entity.EntityType.SMALL_FIREBALL, e2 -> e2.isAlive())) {
                if (e.getUuid().equals(hf.fireballUuid)) {
                    fb = e;
                    break;
                }
            }
            if (fb == null) {
                hit.remove();
                continue;
            }
            LivingEntity target = resolveTarget(world, hf.targetUuid);
            if (target == null || !target.isAlive()) {
                // Target dead/gone — keep going straight (last velocity)
                hit.remove();
                continue;
            }
            // Adjust velocity toward target's current position (homing)
            double dx = target.getX() - fb.getX();
            double dy = (target.getY() + target.getStandingEyeHeight() * 0.6) - fb.getY();
            double dz = target.getZ() - fb.getZ();
            double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
            if (dist < 0.001) dist = 1.0;
            double speed = 0.8;
            // Smooth steering — blend current velocity with desired direction
            Vec3d desired = new Vec3d(dx/dist * speed, dy/dist * speed, dz/dist * speed);
            Vec3d current = fb.getVelocity();
            Vec3d blended = current.multiply(0.5).add(desired.multiply(0.5));
            fb.setVelocity(blended);
        }
        if (homingFireballs.size() > 500) homingFireballs.clear();

        // Clean up expired stuns
        stunMap.entrySet().removeIf(entry -> entry.getValue() <= currentTick);
    }

    /** Resolve a target entity by UUID — player first, then any living entity.
     *  Public so LeomordEntity's death sequence can resolve target enemies
     *  (which may be players OR Larrys/mobs). */
    public static LivingEntity resolveTargetPublic(ServerWorld world, UUID uuid) {
        return resolveTarget(world, uuid);
    }

    /** Resolve a target entity by UUID — player first, then any living entity. */
    private static LivingEntity resolveTarget(ServerWorld world, UUID uuid) {
        PlayerEntity p = world.getServer().getPlayerManager().getPlayer(uuid);
        if (p != null && p.isAlive()) return p;
        Entity ent = world.getEntity(uuid);
        if (ent instanceof LivingEntity living && living.isAlive()) return living;
        return null;
    }

    public static void clear() {
        stunMap.clear();
        hoveringFireballs.clear();
        homingFireballs.clear();
    }

    public static void clearStun(UUID uuid) {
        stunMap.remove(uuid);
    }

    private static final class HoveringFireball {
        final UUID fireballUuid;
        final long launchTick;
        final UUID targetUuid;
        HoveringFireball(UUID fireballUuid, long launchTick, UUID targetUuid) {
            this.fireballUuid = fireballUuid;
            this.launchTick = launchTick;
            this.targetUuid = targetUuid;
        }
    }

    private static final class HomingFireball {
        final UUID fireballUuid;
        final UUID targetUuid;
        HomingFireball(UUID fireballUuid, UUID targetUuid) {
            this.fireballUuid = fireballUuid;
            this.targetUuid = targetUuid;
        }
    }
}
