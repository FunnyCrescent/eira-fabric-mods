package com.totemmod;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.EnderChestInventory;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

import java.util.List;

/**
 * Portable Ender Chest — Trial Key with NBT marker.
 * Right-click to open ender chest inventory anywhere.
 *
 * Craft: 4 bones + ender chest + 4 blank totems (cross pattern)
 */
public final class PortableEnderChest {

    private PortableEnderChest() {}

    public static final String NBT_KEY = "PortableEnderChest";

    public static ItemStack create() {
        ItemStack stack = new ItemStack(Items.TRIAL_KEY);
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean(NBT_KEY, true);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("Ключ Эндерсундука").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
        stack.set(DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(List.of(
                Text.literal("Открывает эндерсундук где угодно").formatted(Formatting.GRAY)
        )));
        return stack;
    }

    public static boolean isPortableEnderChest(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!stack.isOf(Items.TRIAL_KEY)) return false;
        NbtComponent cd = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (cd == null || cd.isEmpty()) return false;
        return cd.copyNbt().getBoolean(NBT_KEY, false);
    }

    public static void register() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;

            ItemStack stack = player.getStackInHand(hand);
            if (!isPortableEnderChest(stack)) return ActionResult.PASS;

            // Open ender chest inventory
            EnderChestInventory enderInv = player.getEnderChestInventory();

            player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInv, p) -> GenericContainerScreenHandler.createGeneric9x3(
                        syncId, playerInv, enderInv),
                Text.literal("Эндерсундук")
            ));

            return ActionResult.SUCCESS;
        });
    }
}
