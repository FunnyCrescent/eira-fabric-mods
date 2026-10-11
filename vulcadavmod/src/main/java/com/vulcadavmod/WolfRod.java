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
 * Wolf Rod — unbreakable fishing rod with custom NBT.
 *
 * Combo: hit with bobber (mark) → pull/approach → hit (teleport to box).
 * Spawns a 10×10×10 bedrock box 100 blocks above the target with 10
 * invulnerable wolves inside. Target is trapped for 5 seconds, then
 * box is removed and original blocks restored.
 *
 * Tracked as "sword" in the weapon scoreboard.
 */
public final class WolfRod {

    private WolfRod() {}

    public static final String NBT_KEY = "WolfRod";

    public static ItemStack create() {
        ItemStack stack = new ItemStack(Items.FISHING_ROD);
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean(NBT_KEY, true);
        // Make unbreakable — UNBREAKABLE component takes Unit.INSTANCE
        stack.set(DataComponentTypes.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("Волчья Удочка").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
        stack.set(DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(List.of(
                Text.literal("Поплавок → удар = тюрьма с волками").formatted(Formatting.GRAY),
                Text.literal("Неломаемая").formatted(Formatting.GREEN)
        )));
        return stack;
    }

    public static boolean isWolfRod(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!stack.isOf(Items.FISHING_ROD)) return false;
        NbtComponent cd = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (cd == null || cd.isEmpty()) return false;
        return cd.copyNbt().getBoolean(NBT_KEY, false);
    }
}
