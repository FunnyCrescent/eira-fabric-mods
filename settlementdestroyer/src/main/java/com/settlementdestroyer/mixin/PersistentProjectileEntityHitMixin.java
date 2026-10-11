package com.settlementdestroyer.mixin;

import com.settlementdestroyer.PersistentProjectileHitHandler;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin for PersistentProjectileEntity — intercepts onEntityHit to
 * spawn 3 Evoker Fangs when a Three Fangs trident hits an entity.
 *
 * Previously, fangs only spawned when the trident hit a block (detected
 * via isInGround() in the tick handler). Now fangs also spawn when the
 * trident hits an entity (player, mob, etc.).
 */
@Mixin(PersistentProjectileEntity.class)
public abstract class PersistentProjectileEntityHitMixin {

    @Inject(method = "onEntityHit", at = @At("TAIL"))
    private void settlementdestroyer$onEntityHit(EntityHitResult entityHitResult, CallbackInfo ci) {
        PersistentProjectileEntity self = (PersistentProjectileEntity) (Object) this;
        PersistentProjectileHitHandler.onEntityHit(self, entityHitResult);
    }
}
