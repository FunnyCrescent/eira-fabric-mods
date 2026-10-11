package com.settlementdestroyer.mixin;

import com.settlementdestroyer.PersistentProjectileHitHandler;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin for TridentEntity — intercepts onEntityHit to spawn 3 Evoker Fangs
 * when a Three Fangs trident hits an entity.
 *
 * TridentEntity OVERRIDES onEntityHit from PersistentProjectileEntity, so
 * the mixin must target TridentEntity directly. A mixin on the parent
 * (PersistentProjectileEntity) does NOT fire for TridentEntity instances
 * because the override is called instead.
 */
@Mixin(TridentEntity.class)
public abstract class TridentEntityHitMixin {

    @Inject(method = "onEntityHit", at = @At("TAIL"))
    private void settlementdestroyer$onEntityHit(EntityHitResult entityHitResult, CallbackInfo ci) {
        TridentEntity self = (TridentEntity) (Object) this;
        PersistentProjectileHitHandler.onEntityHit(self, entityHitResult);
    }
}
