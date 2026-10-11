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
 * Лук Огня (Fire Bow) — drop from Leomord loot bag.
 *
 * Behavior (to be implemented via mixin on BowItem):
 *   - When firing this bow, instead of (or in addition to) the regular arrow,
 *     spawn 4 fireballs above the target (auto-homing).
 *
 * Base item: vanilla BOW.
 */
public final class FireBow {

    private FireBow() {}

    public static final String NBT_KEY = "LeomordFireBow";
    /** Tag placed on arrows fired from the Fire Bow. */
    public static final String ARROW_TAG = "LeomordFireBowArrow";

    public static ItemStack create() {
        ItemStack stack = new ItemStack(Items.BOW);
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean(NBT_KEY, true);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("Лук Огня")
                        .formatted(Formatting.DARK_RED, Formatting.BOLD));
        stack.set(DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(List.of(
                Text.literal("Дроп с Леоморда").formatted(Formatting.GOLD),
                Text.literal("Спавнит 4 фаербола над врагом").formatted(Formatting.DARK_RED),
                Text.literal("Автонаводка").formatted(Formatting.YELLOW)
        )));
        stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    public static boolean isFireBow(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!stack.isOf(Items.BOW)) return false;
        NbtComponent cd = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (cd == null || cd.isEmpty()) return false;
        return cd.copyNbt().getBoolean(NBT_KEY, false);
    }
}
