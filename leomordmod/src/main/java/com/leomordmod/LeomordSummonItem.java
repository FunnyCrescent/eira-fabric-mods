package com.leomordmod;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Leomord Summon Item — Echo Shard with NBT marker.
 * Right-click on block to summon Leomord boss (riding Skeleton Horse).
 *
 * Unique NBT key "LeomordSummon" — does NOT collide with any other mod.
 *
 * Pattern follows settlementdestroyer.DestroyerSummonItem 1:1.
 */
public final class LeomordSummonItem {

    private LeomordSummonItem() {}

    /** Unique NBT marker — all leomordmod custom items use Leomord* prefix. */
    public static final String NBT_KEY = "LeomordSummon";

    public static ItemStack create() {
        ItemStack stack = new ItemStack(Items.ECHO_SHARD);
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean(NBT_KEY, true);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("Зов Леоморда")
                        .formatted(Formatting.DARK_RED, Formatting.BOLD));
        stack.set(DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(List.of(
                Text.literal("Призывает Леоморда — Вождя Падших").formatted(Formatting.GRAY),
                Text.literal("ПКМ по блоку — призвать босса").formatted(Formatting.YELLOW),
                Text.literal("Огниво в крафте теряет 50% прочности").formatted(Formatting.DARK_GRAY)
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
