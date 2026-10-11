package com.totemmod;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Recovery Heart — a Heart of the Sea item that stores a player's entire
 * inventory. Created when a player dies with a Totem of Inventory.
 *
 * Right-click to recover all stored items. Despawns after 5 minutes.
 */
public final class RecoveryHeart {

    private RecoveryHeart() {}

    public static final String RECOVERY_NBT_KEY = "TotemRecovery";
    public static final String OWNER_KEY = "RecoveryOwner";
    public static final long RECOVERY_TIMEOUT_MS = 5 * 60 * 1000; // 5 minutes

    /** Create a Recovery Heart item stack. */
    public static ItemStack create(java.util.UUID ownerUuid) {
        ItemStack stack = new ItemStack(Items.HEART_OF_THE_SEA);
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean(RECOVERY_NBT_KEY, true);
        nbt.putString(OWNER_KEY, ownerUuid.toString());
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("Сердце Возврата").formatted(Formatting.AQUA, Formatting.BOLD));
        stack.set(DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(List.of(
                Text.literal("Хранит ваши вещи").formatted(Formatting.GRAY),
                Text.literal("ПКМ — вернуть инвентарь").formatted(Formatting.YELLOW),
                Text.literal("Исчезнет через 5 минут").formatted(Formatting.RED)
        )));
        return stack;
    }

    /** Check if an item stack is a Recovery Heart. */
    public static boolean isRecoveryHeart(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!stack.isOf(Items.HEART_OF_THE_SEA)) return false;
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) return false;
        return customData.copyNbt().getBoolean(RECOVERY_NBT_KEY, false);
    }

    /** Get the owner UUID from a Recovery Heart. */
    public static java.util.UUID getOwner(ItemStack stack) {
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) return null;
        NbtCompound nbt = customData.copyNbt();
        try {
            return java.util.UUID.fromString(nbt.getString(OWNER_KEY, ""));
        } catch (Exception e) {
            return null;
        }
    }
}
