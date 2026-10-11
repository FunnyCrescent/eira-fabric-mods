package com.leomordmod.mixin;

import com.leomordmod.CarrionCrossbow;
import com.leomordmod.ExplosiveSpear;
import com.leomordmod.FireBow;
import com.leomordmod.LeomordProjectiles;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PersistentProjectileHitMixin — intercepts onEntityHit and onBlockHit on
 * PersistentProjectileEntity (parent of ArrowEntity).
 *
 * Handles the following tags on ArrowEntity instances:
 *   - "LeomordArrow"             — Phase 3 boss arrow (explosion + stun +
 *     fireballs at each enemy in aggro radius)
 *   - "LeomordGetOverHere"       — Phase 2 boss pull projectile (teleport hit
 *     entity to boss)
 *   - "LeomordSpray"             — Phase 3 spray arrow (explosion only)
 *   - "LeomordCarrionArrow"      — fired from Carrion Crossbow: pull hit
 *     entity to FIRING PLAYER + mark player for x2 combo on next melee hit
 *   - "LeomordFireBowArrow"      — fired from Fire Bow: spawn 4 homing
 *     fireballs above the hit entity (auto-targets that enemy)
 *
 * NOTE: TridentEntity OVERRIDES onEntityHit, so this mixin does NOT catch
 * tridents. For tridents (thrown Explosive Spear), see TridentEntityHitMixin.
 */
@Mixin(PersistentProjectileEntity.class)
public abstract class PersistentProjectileHitMixin {

    @Inject(method = "onEntityHit", at = @At("HEAD"), cancellable = true)
    private void leomordmod$onEntityHit(EntityHitResult result, CallbackInfo ci) {
        PersistentProjectileEntity self = (PersistentProjectileEntity) (Object) this;
        if (!(self instanceof ArrowEntity arrow)) return;

        boolean isBossArrow = arrow.getCommandTags().contains(LeomordProjectiles.ARROW_TAG);
        boolean isPull      = arrow.getCommandTags().contains(LeomordProjectiles.PULL_TAG);
        boolean isSpray     = arrow.getCommandTags().contains(LeomordProjectiles.SPRAY_TAG);
        boolean isCarrion   = arrow.getCommandTags().contains(CarrionCrossbow.ARROW_TAG);
        boolean isFireBow   = arrow.getCommandTags().contains(FireBow.ARROW_TAG);

        if (!isBossArrow && !isPull && !isSpray && !isCarrion && !isFireBow) return;

        Entity hit = result.getEntity();
        ServerWorld world = (ServerWorld) arrow.getEntityWorld();

        if (isBossArrow) {
            LeomordProjectiles.handleArrowEntityHit(arrow, hit);
        } else if (isPull) {
            LeomordProjectiles.handlePullEntityHit(arrow, hit);
        } else if (isSpray) {
            LeomordProjectiles.handleSprayEntityHit(arrow, hit);
        } else if (isCarrion) {
            handleCarrionArrowHit(arrow, hit, world);
        } else if (isFireBow) {
            handleFireBowArrowHit(arrow, hit, world);
        }

        arrow.discard();
        ci.cancel();
    }

    @Inject(method = "onBlockHit", at = @At("HEAD"), cancellable = true)
    private void leomordmod$onBlockHit(BlockHitResult result, CallbackInfo ci) {
        PersistentProjectileEntity self = (PersistentProjectileEntity) (Object) this;
        if (!(self instanceof ArrowEntity arrow)) return;

        boolean isBossArrow = arrow.getCommandTags().contains(LeomordProjectiles.ARROW_TAG);
        boolean isSpray     = arrow.getCommandTags().contains(LeomordProjectiles.SPRAY_TAG);
        boolean isCarrion   = arrow.getCommandTags().contains(CarrionCrossbow.ARROW_TAG);
        boolean isFireBow   = arrow.getCommandTags().contains(FireBow.ARROW_TAG);

        if (!isBossArrow && !isSpray && !isCarrion && !isFireBow) return;
        // Pull arrows (LeomordGetOverHere) silently discard on block hit

        ServerWorld world = (ServerWorld) arrow.getEntityWorld();

        if (isBossArrow) {
            LeomordProjectiles.handleArrowBlockHit(arrow);
        } else if (isSpray) {
            LeomordProjectiles.handleSprayBlockHit(arrow);
        } else if (isCarrion) {
            // Carrion arrow hit a block (missed) — no pull, no combo.
            // Just discard silently.
        } else if (isFireBow) {
            // Fire Bow arrow hit a block — no fireballs (needs entity hit).
            // Discard silently.
        }

        arrow.discard();
        ci.cancel();
    }

    /**
     * Handle Carrion Crossbow arrow entity hit:
     *   - Pull hit entity to the FIRING PLAYER (not the boss).
     *   - Mark the firing player with a CarrionCombo tag for 3 seconds — their
     *     next melee attack on the pulled entity deals x2 damage.
     */
    private static void handleCarrionArrowHit(ArrowEntity arrow, Entity hit, ServerWorld world) {
        if (!(hit instanceof LivingEntity livingHit)) return;
        if (!livingHit.isAlive()) return;

        Entity owner = arrow.getOwner();
        if (!(owner instanceof PlayerEntity player)) return;
        if (!player.isAlive()) return;

        // Teleport hit entity to the player (in front of player, facing player)
        float yaw = player.getYaw();
        double tx = player.getX() + (-Math.sin(Math.toRadians(yaw)) * 2.0);
        double ty = player.getY();
        double tz = player.getZ() + (Math.cos(Math.toRadians(yaw)) * 2.0);
        livingHit.refreshPositionAndAngles(tx, ty, tz, player.getYaw() + 180, 0);
        livingHit.setVelocity(0, 0, 0);

        // Mark player with CarrionCombo for 3 seconds (60 ticks)
        long expiryTick = world.getServer().getTicks() + 60;
        com.leomordmod.LeomordItemBehaviors.markCarrionCombo(player.getUuid(), expiryTick);

        // Visual + audio
        world.spawnParticles(net.minecraft.particle.ParticleTypes.REVERSE_PORTAL,
                livingHit.getX(), livingHit.getY() + 1, livingHit.getZ(),
                30, 0.5, 1.0, 0.5, 0.1);
        world.playSound(null, arrow.getX(), arrow.getY(), arrow.getZ(),
                net.minecraft.sound.SoundEvents.ENTITY_ENDERMAN_TELEPORT,
                net.minecraft.sound.SoundCategory.PLAYERS, 1.5f, 0.6f);
    }

    /**
     * Handle Fire Bow arrow entity hit:
     *   - Spawn 4 homing fireballs at 10 blocks above the hit entity.
     *   - Fireballs auto-track the hit entity (homing).
     *
     * The hit entity must be a LivingEntity. If it's a non-living entity
     * (e.g., armor stand), no fireballs spawn.
     */
    private static void handleFireBowArrowHit(ArrowEntity arrow, Entity hit, ServerWorld world) {
        if (!(hit instanceof LivingEntity livingHit)) return;
        if (!livingHit.isAlive()) return;

        // Spawn 4 homing fireballs above the hit entity
        // (reuses the boss's spawnCrossFireballs — same mechanic)
        Entity owner = arrow.getOwner();
        if (owner instanceof LivingEntity livingOwner) {
            // Treat the firing player as the "boss" for the fireball cross
            // (use the player's UUID as the target reference)
            LeomordProjectiles.spawnCrossFireballsForTarget(world, livingHit);
        }
    }
}
