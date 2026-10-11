package com.leomordmod.mixin;

import com.leomordmod.LeomordSummonItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.CraftingResultSlot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Crafting Result Slot Mixin — implements the Flint and Steel durability
 * mechanic for the Leomord Summon recipe.
 *
 * Spec:
 *   - When crafting the Leomord Summon item, the Flint and Steel loses
 *     50% of its MAXIMUM durability (MaxDamage * 0.5) and is returned to the player.
 *   - If the Flint and Steel's current durability BEFORE crafting is less than
 *     50% of max, the Flint and Steel fully breaks (disappears).
 *
 * Implementation:
 *   - Inject at HEAD of CraftingResultSlot.onTakeItem(PlayerEntity, ItemStack).
 *   - If the result is the Leomord Summon item:
 *     1. Iterate the input inventory's slots.
 *     2. For each slot containing Flint and Steel:
 *        - Compute current durability left = max - current damage
 *        - If durability left < max * 0.5 → leave it in slot for the default
 *          decrement (which sets max-count=1 items to empty = break).
 *        - Else → copy the stack, apply damage += max * 0.5, give back to the
 *          player (insert into inventory or drop), then clear the input slot.
 *   - The default decrement runs AFTER this injection (we don't cancel) and:
 *     - For slots where we cleared the Flint and Steel → nothing to decrement (skip)
 *     - For slots where we left the Flint and Steel (broke) → set to empty (default behavior)
 *     - For Crown, Bones → normal decrement
 */
@Mixin(CraftingResultSlot.class)
public abstract class CraftingResultSlotMixin {

    @Shadow
    @Final
    private RecipeInputInventory input;

    @Inject(method = "onTakeItem", at = @At("HEAD"))
    private void leomordmod$onTakeItem(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
        if (player.getEntityWorld().isClient()) return;
        if (!LeomordSummonItem.isSummonItem(stack)) return;

        // Iterate input slots — apply Flint and Steel durability logic
        for (int i = 0; i < input.size(); i++) {
            ItemStack in = input.getStack(i);
            if (in.isEmpty()) continue;
            if (!in.isOf(Items.FLINT_AND_STEEL)) continue;

            int maxDamage = in.getMaxDamage();
            if (maxDamage <= 0) continue; // sanity — flint and steel always has durability
            int currentDamage = in.getDamage();
            int durabilityLeft = maxDamage - currentDamage;

            if (durabilityLeft < maxDamage * 0.5) {
                // Break — leave it in slot; default decrement will set to empty.
                // (Vanilla decrement for max-count=1 items: setStack(i, EMPTY))
            } else {
                // Damage by 50% of max and return to player
                ItemStack damaged = in.copy();
                int newDamage = currentDamage + (int) (maxDamage * 0.5);
                // Clamp to max (can't have damage beyond max — item breaks)
                if (newDamage >= maxDamage) {
                    newDamage = maxDamage; // will render as "broken" but not actually break
                }
                damaged.setDamage(newDamage);
                damaged.setCount(1); // safety
                if (!player.getInventory().insertStack(damaged)) {
                    player.dropItem(damaged, false);
                }
                // Clear the slot — default decrement will skip (nothing to decrement)
                input.setStack(i, ItemStack.EMPTY);
            }
        }
    }
}
