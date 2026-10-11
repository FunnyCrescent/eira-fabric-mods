package com.larrymod.mixin;

import com.larrymod.LarryBone;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevent wolves from eating Larry's Sharpened Bone.
 * Vanilla WolfEntity.interactMob checks isOf(Items.BONE) and tames the wolf
 * by consuming the bone. Since LarryBone is a BONE item with NBT marker,
 * vanilla would happily eat it. This mixin returns PASS at HEAD if the held
 * stack is a LarryBone, so vanilla never gets to consume it.
 */
@Mixin(WolfEntity.class)
public abstract class WolfNoBoneMixin {

    @Inject(method = "interactMob", at = @At("HEAD"), cancellable = true)
    private void larrymod$refuseLarryBone(PlayerEntity player, Hand hand,
                                           CallbackInfoReturnable<ActionResult> cir) {
        ItemStack stack = player.getStackInHand(hand);
        if (LarryBone.isLarryBone(stack)) {
            cir.setReturnValue(ActionResult.PASS);
        }
    }
}
