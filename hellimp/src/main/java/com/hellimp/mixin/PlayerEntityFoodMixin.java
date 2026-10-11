package com.hellimp.mixin;

import com.hellimp.AccessoriesSystem;
import com.hellimp.InfiniteFoodAccessory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin on PlayerEntity.tick — if the player has an Infinite Food Accessory
 * in any accessory slot, keep hunger and saturation at max.
 *
 * Every tick: if accessory is present, set hungerLevel=20 (max) and
 * saturationLevel=20 (max). This means the player never gets hungry.
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityFoodMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void hellimp$keepHungerMax(CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        World world = self.getEntityWorld();
        if (world.isClient()) return;

        SimpleInventory inv = AccessoriesSystem.getInventory(self);
        if (inv == null) return;

        // Check if any accessory slot has Infinite Food Accessory
        boolean hasAccessory = false;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (InfiniteFoodAccessory.isInfiniteFood(stack)) {
                hasAccessory = true;
                break;
            }
        }

        if (hasAccessory) {
            // Set hunger to max (20) and saturation to max (20)
            self.getHungerManager().setFoodLevel(20);
            self.getHungerManager().setSaturationLevel(20.0f);
        }
    }
}
