package com.settlementdestroyer;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Destroyer Summon Item — Echo Shard with NBT marker.
 * Right-click on block to spawn Settlement Destroyer boss.
 *
 * Uses ECHO_SHARD (visible on Bedrock via Geyser) with custom name + lore.
 * Echo Shard has no default right-click behavior, so UseItemCallback
 * works cleanly on both Java and Bedrock.
 */
public final class DestroyerSummonItem {

    private DestroyerSummonItem() {}

    public static final String NBT_KEY = "DestroyerSummon";

    public static ItemStack create() {
        ItemStack stack = new ItemStack(Items.ECHO_SHARD);
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean(NBT_KEY, true);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("Знак Разрушителя")
                        .formatted(Formatting.DARK_RED, Formatting.BOLD));
        stack.set(DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(List.of(
                Text.literal("Призывает Разрушителя Поселений").formatted(Formatting.GRAY),
                Text.literal("ПКМ по блоку — использовать").formatted(Formatting.YELLOW)
        )));
        stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    public static boolean isSummonItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!stack.isOf(Items.ECHO_SHARD)) return false;
        NbtComponent cd = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (cd == null || cd.isEmpty()) return false;
        return cd.copyNbt().getBoolean(NBT_KEY, false);
    }
}
