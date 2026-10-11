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
 * Арбалет Падали (Carrion Crossbow) — drop from Leomord loot bag.
 *
 * Behavior (to be implemented via mixin):
 *   - When firing this crossbow, the spawned arrow is tagged as a Carrion Arrow.
 *   - On entity hit, the Carrion Arrow teleports the hit entity to the firing
 *     player (in front of them).
 *   - The player is marked with a "CarrionCombo" tag for 3 seconds — their
 *     next melee attack on the pulled entity deals x2 damage.
 *
 * Base item: vanilla Crossbow (so Bedrock can render it).
 */
public final class CarrionCrossbow {

    private CarrionCrossbow() {}

    public static final String NBT_KEY = "LeomordCarrionCrossbow";
    /** Tag placed on arrows fired from the Carrion Crossbow. */
    public static final String ARROW_TAG = "LeomordCarrionArrow";

    public static ItemStack create() {
        ItemStack stack = new ItemStack(Items.CROSSBOW);
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean(NBT_KEY, true);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("Арбалет Падали")
                        .formatted(Formatting.DARK_RED, Formatting.BOLD));
        stack.set(DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(List.of(
                Text.literal("Дроп с Леоморда").formatted(Formatting.GOLD),
                Text.literal("Притягивает врага к тебе").formatted(Formatting.GRAY),
                Text.literal("Следующий удар x2 урон").formatted(Formatting.DARK_RED)
        )));
        stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    public static boolean isCarrionCrossbow(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!stack.isOf(Items.CROSSBOW)) return false;
        NbtComponent cd = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (cd == null || cd.isEmpty()) return false;
        return cd.copyNbt().getBoolean(NBT_KEY, false);
    }
}
