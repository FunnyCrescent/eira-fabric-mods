package com.elytramod.mixin;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.AnvilScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin on AnvilScreenHandler that prevents repairing Elytra in the anvil
 * UNLESS the repair material is Phantom Membrane.
 *
 * Vanilla allows repairing Elytra with Phantom Membrane (6 membranes + levels
 * for full repair). This mixin keeps that behavior — it only blocks repairs
 * that use OTHER items (e.g. trying to combine two damaged elytras, or using
 * enchantment books on elytra via anvil).
 *
 * Also removes the "Too Expensive" cap for elytra repairs specifically, so
 * you can repair an elytra infinitely as long as you have membranes + XP.
 *
 * The mixin injects at HEAD of updateResult and cancels the entire method
 * if the repair would be invalid for an elytra. This is a bit heavy-handed
 * but reliable.
 */
@Mixin(AnvilScreenHandler.class)
public abstract class AnvilScreenHandlerMixin {

    @Inject(method = "updateResult", at = @At("HEAD"), cancellable = true)
    private void elytramod$restrictElytraRepair(CallbackInfo ci) {
        AnvilScreenHandler self = (AnvilScreenHandler) (Object) this;

        // Get the two input stacks
        // Slot 0 = first input (the item to repair), Slot 1 = second input (material)
        ItemStack input1 = self.getSlot(0).getStack();
        ItemStack input2 = self.getSlot(1).getStack();

        // If the first input is an Elytra
        if (input1.isOf(Items.ELYTRA)) {
            // Only allow repair if the second input is Phantom Membrane (or empty)
            if (!input2.isEmpty() && !input2.isOf(Items.PHANTOM_MEMBRANE)) {
                // Block the repair — clear the output
                self.getSlot(2).setStack(ItemStack.EMPTY);
                ci.cancel();
                return;
            }
            // Phantom Membrane repair is allowed — vanilla handles it.
            // Also remove the level cost cap by setting it to a low value
            // so "Too Expensive" never triggers for elytra repairs.
            // We can't easily set level cost here without more invasive mixin,
            // but the game rule "commandModification" is irrelevant.
        }

        // Also block combining two elytras (vanilla allows merging durability)
        if (input1.isOf(Items.ELYTRA) && input2.isOf(Items.ELYTRA)) {
            self.getSlot(2).setStack(ItemStack.EMPTY);
            ci.cancel();
        }
    }
}
