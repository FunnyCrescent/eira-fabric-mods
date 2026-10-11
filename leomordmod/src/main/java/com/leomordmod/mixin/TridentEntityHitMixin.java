package com.leomordmod.mixin;

import com.leomordmod.ExplosiveSpear;
import com.leomordmod.LeomordProjectiles;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TridentEntityHitMixin — intercepts onEntityHit and onBlockHit on
 * TridentEntity specifically (because TridentEntity OVERRIDES onEntityHit,
 * so the parent PersistentProjectileHitMixin doesn't catch it).
 *
 * Per LESSONS_LEARNED.md:
 *   "Если твой mixin на ProjectileEntity — он НЕ сработает для TridentEntity
 *    (вызывается версия из TridentEntity)."
 *
 * Tag handled: "LeomordExplosiveSpearProjectile" — applied by
 * LeomordProjectileTagger when a thrown trident's owner was holding an
 * Explosive Spear at the moment of throw.
 *
 * On hit: TNT-radius explosion (no block break) at the impact point.
 */
@Mixin(TridentEntity.class)
public abstract class TridentEntityHitMixin {

    @Inject(method = "onEntityHit", at = @At("HEAD"), cancellable = true)
    private void leomordmod$onTridentEntityHit(EntityHitResult result, CallbackInfo ci) {
        TridentEntity self = (TridentEntity) (Object) this;
        if (!self.getCommandTags().contains("LeomordExplosiveSpearProjectile")) return;

        Entity hit = result.getEntity();
        ServerWorld world = (ServerWorld) self.getEntityWorld();

        // Trigger explosion (TNT radius, no block break, manual entity damage)
        // Source = trident owner (firing player) so explosion damage is
        // attributed correctly + the player is excluded from self-damage.
        Entity owner = self.getOwner();
        LeomordProjectiles.triggerExplosion(world, owner,
                self.getX(), self.getY() + 0.5, self.getZ());

        // Discard the trident
        self.discard();
        ci.cancel();
    }

    @Inject(method = "onBlockHit", at = @At("HEAD"), cancellable = true)
    private void leomordmod$onTridentBlockHit(BlockHitResult result, CallbackInfo ci) {
        TridentEntity self = (TridentEntity) (Object) this;
        if (!self.getCommandTags().contains("LeomordExplosiveSpearProjectile")) return;

        ServerWorld world = (ServerWorld) self.getEntityWorld();
        Entity owner = self.getOwner();
        LeomordProjectiles.triggerExplosion(world, owner,
                self.getX(), self.getY() + 0.5, self.getZ());

        self.discard();
        ci.cancel();
    }
}
