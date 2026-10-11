package com.vulcadavmod;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Wolf Berry — glow berries with NBT marker.
 * Feed to a wolf to make it invulnerable.
 * Cannot be eaten by players.
 */
public final class WolfBerry {

    private WolfBerry() {}

    public static final String NBT_KEY = "WolfBerry";

    public static ItemStack create() {
        ItemStack stack = new ItemStack(Items.GLOW_BERRIES);
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean(NBT_KEY, true);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("Волчья Ягода").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
        stack.set(DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(List.of(
                Text.literal("Скормите волку — станет неуязвимым").formatted(Formatting.GRAY),
                Text.literal("Нельзя есть игрокам").formatted(Formatting.RED)
        )));
        return stack;
    }

    public static boolean isWolfBerry(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!stack.isOf(Items.GLOW_BERRIES)) return false;
        NbtComponent cd = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (cd == null || cd.isEmpty()) return false;
        return cd.copyNbt().getBoolean(NBT_KEY, false);
    }
}
