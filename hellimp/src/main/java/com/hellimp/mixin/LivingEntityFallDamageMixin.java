package com.hellimp.mixin;

import com.hellimp.AccessoriesSystem;
import com.hellimp.ElytraCloak;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.LivingEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin on LivingEntity.handleFallDamage — if the player has an Elytra Cloak
 * in any accessory slot, cancel all fall damage.
 *
 * handleFallDamage(double fallDistance, float damageMultiplier, DamageSource damageSource)
 * Returns boolean (whether damage was applied).
 *
 * We inject at HEAD and cancel (return false) if the player has the cloak.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityFallDamageMixin {

    @Inject(method = "handleFallDamage", at = @At("HEAD"), cancellable = true)
    private void hellimp$cancelFallDamage(double fallDistance, float damageMultiplier,
                                           DamageSource damageSource,
                                           CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;

        // Only handle players (not mobs)
        if (!(self instanceof net.minecraft.entity.player.PlayerEntity player)) return;

        // Only on server
        if (self.getEntityWorld().isClient()) return;

        // Check if any accessory slot has Elytra Cloak
        SimpleInventory inv = AccessoriesSystem.getInventory(player);
        if (inv == null) return;

        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (ElytraCloak.isElytraCloak(stack)) {
                // Cancel fall damage — return false (no damage applied)
                cir.setReturnValue(false);
                return;
            }
        }
    }
}
