package com.vulcadavmod.mixin;

import com.vulcadavmod.WolfRod;
import com.vulcadavmod.WolfRodHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin on FishingBobberEntity — when a Wolf Rod bobber hits an entity,
 * mark the target with a command tag.
 */
@Mixin(FishingBobberEntity.class)
public abstract class WolfRodBobberMixin {

    @Inject(method = "onEntityHit", at = @At("RETURN"))
    private void vulcadavmod$markTarget(net.minecraft.util.hit.EntityHitResult entityHitResult, CallbackInfo ci) {
        FishingBobberEntity self = (FishingBobberEntity) (Object) this;
        if (self.getEntityWorld().isClient()) return;

        // Check if the owner is holding a Wolf Rod
        if (self.getOwner() instanceof net.minecraft.entity.player.PlayerEntity player) {
            net.minecraft.item.ItemStack mainHand = player.getMainHandStack();
            if (WolfRod.isWolfRod(mainHand)) {
                WolfRodHandler.markTarget(entityHitResult.getEntity());
            }
        }
    }
}
