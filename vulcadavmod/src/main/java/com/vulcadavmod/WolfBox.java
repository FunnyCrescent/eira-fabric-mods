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
 * Wolf Box — drops 100% from Larry group.
 * Textured as Mace (vanilla item, visible on Bedrock).
 * Right-click to summon Vulcadav boss.
 */
public final class WolfBox {

    private WolfBox() {}

    public static final String NBT_KEY = "WolfBox";

    public static ItemStack create() {
        ItemStack stack = new ItemStack(Items.HEAVY_CORE);
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean(NBT_KEY, true);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("Волчья Шкатулка").formatted(Formatting.DARK_PURPLE, Formatting.BOLD));
        stack.set(DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(List.of(
                Text.literal("Призывает Вулкадава").formatted(Formatting.GRAY),
                Text.literal("ПКМ — использовать").formatted(Formatting.YELLOW)
        )));
        return stack;
    }

    public static boolean isWolfBox(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!stack.isOf(Items.HEAVY_CORE)) return false;
        NbtComponent cd = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (cd == null || cd.isEmpty()) return false;
        return cd.copyNbt().getBoolean(NBT_KEY, false);
    }
}
