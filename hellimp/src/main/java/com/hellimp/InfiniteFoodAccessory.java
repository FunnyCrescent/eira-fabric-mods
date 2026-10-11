package com.hellimp;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Infinite Food Accessory (Аксессуар Бесконечной Еды) — keeps hunger at max.
 *
 * Recipe: 9 Enchanted Golden Apples → Infinite Food Accessory
 * Texture: Golden Apple (vanilla item, Bedrock-visible via Geyser)
 *
 * Effect: While in an accessory slot, the player's hunger and saturation
 * are constantly refilled to maximum.
 *
 * Implementation: Golden Apple with NBT marker. PlayerEntityTickMixin
 * checks if any accessory slot has this item → sets hunger to 20 (max)
 * and saturation to max every tick.
 */
public final class InfiniteFoodAccessory {

    private InfiniteFoodAccessory() {}

    public static final String NBT_KEY = "InfiniteFoodAccessory";

    public static ItemStack create() {
        ItemStack stack = new ItemStack(Items.GOLDEN_APPLE);
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean(NBT_KEY, true);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("Аксессуар Бесконечной Еды")
                        .formatted(Formatting.GOLD, Formatting.BOLD));
        stack.set(DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(List.of(
                Text.literal("Аксессуар").formatted(Formatting.GOLD),
                Text.literal("Держит сытость на максимуме").formatted(Formatting.GRAY),
                Text.literal("Поместите в слот аксессуара через /aks").formatted(Formatting.YELLOW)
        )));
        stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    public static boolean isInfiniteFood(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!stack.isOf(Items.GOLDEN_APPLE)) return false;
        NbtComponent cd = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (cd == null || cd.isEmpty()) return false;
        return cd.copyNbt().getBoolean(NBT_KEY, false);
    }
}
