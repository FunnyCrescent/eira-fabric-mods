package com.settlementdestroyer;

import com.settlementdestroyer.mixin.PersistentProjectileEntityInvoker;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for TridentEntity hit events — called from TridentEntityHitMixin
 * when a trident hits an entity.
 */
public final class PersistentProjectileHitHandler {

    private PersistentProjectileHitHandler() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("settlementdestroyer");

    public static void onEntityHit(TridentEntity trident, EntityHitResult hitResult) {
        LOGGER.info("[HIT] onEntityHit called for trident!");

        World world = ((net.minecraft.entity.Entity) trident).getEntityWorld();
        if (world.isClient()) return;
        if (!(world instanceof ServerWorld serverWorld)) return;

        PersistentProjectileEntityInvoker accessor = (PersistentProjectileEntityInvoker) trident;
        ItemStack stack = accessor.settlementdestroyer$asItemStack();
        LOGGER.info("[HIT] trident stack={}, isThreeFangs={}", stack, ThreeFangsTrident.isThreeFangs(stack));
        if (!ThreeFangsTrident.isThreeFangs(stack)) return;

        if (ThreeFangsTrident.hasFired(trident.getUuid())) return;

        Entity hitEntity = hitResult.getEntity();
        Entity owner = trident.getOwner();

        // /dtr mode check:
        //   - If hitEntity is a Player and tridentHitsPlayers() == false → skip
        //     (fanged players are protected when DTR is OFF)
        //   - Mobs are always damaged
        if (hitEntity instanceof net.minecraft.entity.player.PlayerEntity
                && !DtrCommand.tridentHitsPlayers()) {
            LOGGER.info("[HIT] hit entity is player and DTR is OFF — skipping");
            ThreeFangsTrident.markFired(trident.getUuid());
            return;
        }

        ThreeFangsTrident.spawnFangsOnThrowHit(
                serverWorld,
                hitEntity.getX(),
                hitEntity.getY(),
                hitEntity.getZ(),
                owner
        );

        // AoE: spawn 3 fangs at every LivingEntity within 25 blocks of impact.
        // Players in AoE are also subject to the /dtr mode check.
        double ix = hitEntity.getX();
        double iy = hitEntity.getY();
        double iz = hitEntity.getZ();
        net.minecraft.util.math.Box aoeBox = new net.minecraft.util.math.Box(
                ix - ThreeFangsTrident.THROW_AOE_RADIUS, iy - 5, iz - ThreeFangsTrident.THROW_AOE_RADIUS,
                ix + ThreeFangsTrident.THROW_AOE_RADIUS, iy + 5, iz + ThreeFangsTrident.THROW_AOE_RADIUS);
        boolean hitsPlayers = DtrCommand.tridentHitsPlayers();
        int aoeCount = 0;
        for (Entity e : serverWorld.getOtherEntities(owner, aoeBox)) {
            if (e instanceof net.minecraft.entity.LivingEntity && e.isAlive()) {
                // Skip players in AoE if DTR is OFF
                if (e instanceof net.minecraft.entity.player.PlayerEntity && !hitsPlayers) {
                    continue;
                }
                double dx = e.getX() - ix;
                double dz = e.getZ() - iz;
                if (dx * dx + dz * dz <= ThreeFangsTrident.THROW_AOE_RADIUS * ThreeFangsTrident.THROW_AOE_RADIUS) {
                    ThreeFangsTrident.spawnFangsCluster(serverWorld, e.getX(), e.getY(), e.getZ(), owner);
                    aoeCount++;
                }
            }
        }
        LOGGER.info("[HIT] spawned AoE fangs at {} entities", aoeCount);

        ThreeFangsTrident.markFired(trident.getUuid());
    }
}
