package com.leomordmod.mixin;

import net.minecraft.entity.projectile.PersistentProjectileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Accessor mixin for PersistentProjectileEntity — exposes the protected
 * isInGround() method so LeomordProjectiles can detect when an arrow has
 * landed (block hit) and trigger impact behavior.
 *
 * Pattern follows settlementdestroyer.PersistentProjectileEntityInvoker.
 * Targets PersistentProjectileEntity directly (parent of ArrowEntity), so
 * the cast works for any PersistentProjectileEntity subclass.
 */
@Mixin(PersistentProjectileEntity.class)
public interface PersistentProjectileEntityAccessor {

    @Invoker("isInGround")
    boolean leomordmod$isInGround();
}
