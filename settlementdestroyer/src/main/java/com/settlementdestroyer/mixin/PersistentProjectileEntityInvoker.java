package com.settlementdestroyer.mixin;

import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Invoker mixin for PersistentProjectileEntity — exposes protected methods
 * (asItemStack, isInGround) so ThreeFangsTrident can read the trident's item
 * stack and detect landing.
 *
 * Both methods are defined on PersistentProjectileEntity (the parent of
 * TridentEntity), so the mixin must target PersistentProjectileEntity, not
 * TridentEntity. The cast in ThreeFangsTrident.tick() works because
 * TridentEntity extends PersistentProjectileEntity.
 */
@Mixin(PersistentProjectileEntity.class)
public interface PersistentProjectileEntityInvoker {

    @Invoker("asItemStack")
    ItemStack settlementdestroyer$asItemStack();

    @Invoker("isInGround")
    boolean settlementdestroyer$isInGround();
}
