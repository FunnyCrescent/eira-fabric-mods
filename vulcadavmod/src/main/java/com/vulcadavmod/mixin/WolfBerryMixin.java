package com.vulcadavmod.mixin;

import com.vulcadavmod.WolfBerry;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin on WolfEntity — makes wolves invulnerable if fed Wolf Berry.
 * Uses command tags (not NBT) for tracking — simpler and Bedrock-safe.
 */
@Mixin(WolfEntity.class)
public abstract class WolfBerryMixin {

    @Inject(method = "tick", at = @At("RETURN"))
    private void vulcadavmod$invulnerableWolf(CallbackInfo ci) {
        WolfEntity self = (WolfEntity) (Object) this;
        if (self.getEntityWorld().isClient()) return;

        if (self.getCommandTags().contains("wolfberry_invulnerable")) {
            self.setInvulnerable(true);
            if (self.getHealth() < self.getMaxHealth()) {
                self.heal(1.0f);
            }
        }
    }

    @Inject(method = "interactMob", at = @At("HEAD"), cancellable = true)
    private void vulcadavmod$feedWolfBerry(net.minecraft.entity.player.PlayerEntity player,
                                            net.minecraft.util.Hand hand,
                                            CallbackInfoReturnable<net.minecraft.util.ActionResult> cir) {
        WolfEntity self = (WolfEntity) (Object) this;
        ItemStack stack = player.getStackInHand(hand);

        if (WolfBerry.isWolfBerry(stack)) {
            stack.decrement(1);
            self.addCommandTag("wolfberry_invulnerable");
            self.setInvulnerable(true);
            self.heal(self.getMaxHealth());
            self.playSound(net.minecraft.sound.SoundEvents.ENTITY_RAVAGER_ROAR, 1.0f, 1.5f);
            cir.setReturnValue(net.minecraft.util.ActionResult.SUCCESS);
        }
    }
}
