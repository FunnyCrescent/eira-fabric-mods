package com.vulcadavmod.mixin;

import com.vulcadavmod.WolfBowHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin on PersistentProjectileEntity — when an arrow hits an entity,
 * check if the shooter was holding a Wolf Bow. If so, spawn wolves.
 */
@Mixin(PersistentProjectileEntity.class)
public abstract class WolfBowProjectileMixin {

    @Inject(method = "onEntityHit", at = @At("RETURN"))
    private void vulcadavmod$onArrowHit(EntityHitResult entityHitResult, CallbackInfo ci) {
        PersistentProjectileEntity self = (PersistentProjectileEntity) (Object) this;
        if (self.getEntityWorld().isClient()) return;
        if (!(self.getEntityWorld() instanceof ServerWorld serverWorld)) return;

        Entity target = entityHitResult.getEntity();
        WolfBowHandler.onArrowHit(serverWorld, self, target);
    }
}
