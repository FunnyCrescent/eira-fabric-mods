package com.leomordmod;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * LeomordProjectileTagger — tick-based scanner that tags projectiles fired
 * by players holding Leomord items (Carrion Crossbow, Fire Bow, Explosive
 * Spear).
 *
 * Why tick-scanner instead of mixins:
 *   - The mixin on `CrossbowItem.shoot` would require knowing the exact
 *     method name in 1.21.11 (which varies between versions).
 *   - The tick-scanner is more portable: it scans for new projectiles in
 *     the world, checks the owner's main hand at the moment of seeing
 *     the projectile, and tags it.
 *   - For arrows (ArrowEntity), the existing PersistentProjectileHitMixin
 *     catches the tag on hit.
 *   - For tridents (TridentEntity), a separate mixin (TridentEntityHitMixin)
 *     catches the tag on hit — because TridentEntity OVERRIDES onEntityHit
 *     (per LESSONS_LEARNED.md).
 *
 * Tags applied:
 *   - "LeomordCarrionArrow"     — fired from Carrion Crossbow (pull on hit)
 *   - "LeomordFireBowArrow"     — fired from Fire Bow (4 homing fireballs on hit)
 *   - "LeomordExplosiveSpearProjectile" — thrown Explosive Spear (explosion on hit)
 *
 * The "seen" set prevents re-tagging the same projectile every tick.
 */
public final class LeomordProjectileTagger {

    private LeomordProjectileTagger() {}

    private static final Set<UUID> SEEN = new HashSet<>();

    public static void tick(ServerWorld world) {
        // Tag arrows (ArrowEntity — caught by PersistentProjectileHitMixin)
        for (ArrowEntity arrow : world.getEntitiesByType(
                net.minecraft.entity.EntityType.ARROW, e -> e.isAlive())) {
            if (SEEN.contains(arrow.getUuid())) continue;
            SEEN.add(arrow.getUuid());
            tagArrowIfFromLeomordItem(arrow);
        }

        // Tag tridents (TridentEntity — caught by TridentEntityHitMixin)
        for (TridentEntity trident : world.getEntitiesByType(
                net.minecraft.entity.EntityType.TRIDENT, e -> e.isAlive())) {
            if (SEEN.contains(trident.getUuid())) continue;
            SEEN.add(trident.getUuid());
            tagTridentIfFromExplosiveSpear(trident);
        }

        // Periodic cleanup to prevent memory leak
        if (world.getServer().getTicks() % 600 == 0 && SEEN.size() > 500) {
            SEEN.clear();
        }
    }

    /**
     * Check the arrow's owner (the player who fired it). If their main hand
     * at the moment of firing was a Carrion Crossbow or Fire Bow, tag the arrow.
     */
    private static void tagArrowIfFromLeomordItem(ArrowEntity arrow) {
        Entity owner = arrow.getOwner();
        if (!(owner instanceof PlayerEntity player)) return;
        if (!(player.getEntityWorld() instanceof ServerWorld)) return;

        ItemStack mainHand = player.getMainHandStack();
        if (CarrionCrossbow.isCarrionCrossbow(mainHand)) {
            arrow.addCommandTag(CarrionCrossbow.ARROW_TAG);
        } else if (FireBow.isFireBow(mainHand)) {
            arrow.addCommandTag(FireBow.ARROW_TAG);
        }
    }

    /**
     * Check the trident's owner. If their main hand at the moment of throwing
     * was an Explosive Spear, tag the trident for explosion on hit.
     */
    private static void tagTridentIfFromExplosiveSpear(TridentEntity trident) {
        Entity owner = trident.getOwner();
        if (!(owner instanceof PlayerEntity player)) return;
        if (!(player.getEntityWorld() instanceof ServerWorld)) return;

        ItemStack mainHand = player.getMainHandStack();
        if (ExplosiveSpear.isExplosiveSpear(mainHand)) {
            trident.addCommandTag("LeomordExplosiveSpearProjectile");
        }
    }

    public static void clear() {
        SEEN.clear();
    }
}
