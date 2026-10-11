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
 * Меч Подлёта (Lift Sword) — drop from Leomord loot bag.
 *
 * Behavior (to be implemented via AttackEntityCallback):
 *   - On melee hit: launches the target into the air (strong upward velocity)
 *
 * Base item: vanilla NETHERITE_SWORD.
 */
public final class LiftSword {

    private LiftSword() {}

    public static final String NBT_KEY = "LeomordLiftSword";

    public static ItemStack create() {
        ItemStack stack = new ItemStack(Items.NETHERITE_SWORD);
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean(NBT_KEY, true);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        stack.set(DataComponentTypes.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE);
        stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("Меч Подлёта")
                        .formatted(Formatting.DARK_RED, Formatting.BOLD));
        stack.set(DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(List.of(
                Text.literal("Дроп с Леоморда").formatted(Formatting.GOLD),
                Text.literal("Подбрасывает врага в воздух").formatted(Formatting.DARK_RED)
        )));
        stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    public static boolean isLiftSword(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!stack.isOf(Items.NETHERITE_SWORD)) return false;
        NbtComponent cd = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (cd == null || cd.isEmpty()) return false;
        return cd.copyNbt().getBoolean(NBT_KEY, false);
    }
}
