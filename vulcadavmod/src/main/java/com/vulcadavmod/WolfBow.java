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
 * Wolf Bow — bow with custom NBT.
 * When arrow hits a target, spawns 3 wolves that attack the target,
 * then disappear after the target dies or after 30 seconds.
 *
 * Repaired with armadillo scutes.
 * Has bow durability.
 */
public final class WolfBow {

    private WolfBow() {}

    public static final String NBT_KEY = "WolfBow";

    public static ItemStack create() {
        ItemStack stack = new ItemStack(Items.BOW);
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean(NBT_KEY, true);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("Волчий Лук").formatted(Formatting.AQUA, Formatting.BOLD));
        stack.set(DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(List.of(
                Text.literal("При попадании призывает 3 волков").formatted(Formatting.GRAY),
                Text.literal("Чинится щитками броненосца").formatted(Formatting.GRAY)
        )));
        return stack;
    }

    public static boolean isWolfBow(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!stack.isOf(Items.BOW)) return false;
        NbtComponent cd = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (cd == null || cd.isEmpty()) return false;
        return cd.copyNbt().getBoolean(NBT_KEY, false);
    }
}
