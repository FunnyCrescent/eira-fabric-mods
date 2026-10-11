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
 * Anti-Gravity Accessory (Аксессуар Антигравитации) — removes gravity from arrows.
 *
 * Recipe: 1 Arrow + 8 Phantom Membranes → Anti-Gravity Accessory
 * Texture: Arrow (vanilla item, Bedrock-visible via Geyser)
 *
 * Effect: While in an accessory slot, arrows fired by the player have NO gravity.
 * They fly in a straight line until they hit something.
 * Works with both bow and crossbow.
 *
 * Implementation: Arrow with NBT marker. PersistentProjectileEntityTickMixin
 * checks if the arrow's owner has the accessory → sets the arrow's velocity
 * Y component to ignore gravity (cancels the gravity application).
 */
public final class AntiGravityAccessory {

    private AntiGravityAccessory() {}

    public static final String NBT_KEY = "AntiGravityAccessory";

    public static ItemStack create() {
        ItemStack stack = new ItemStack(Items.ARROW);
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean(NBT_KEY, true);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("Аксессуар Антигравитации")
                        .formatted(Formatting.AQUA, Formatting.BOLD));
        stack.set(DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(List.of(
                Text.literal("Аксессуар").formatted(Formatting.GOLD),
                Text.literal("Убирает гравитацию у стрел").formatted(Formatting.GRAY),
                Text.literal("Стрелы летят ровно, пока не врежутся").formatted(Formatting.GRAY),
                Text.literal("Поместите в слот аксессуара через /aks").formatted(Formatting.YELLOW)
        )));
        stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    public static boolean isAntiGravity(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!stack.isOf(Items.ARROW)) return false;
        NbtComponent cd = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (cd == null || cd.isEmpty()) return false;
        return cd.copyNbt().getBoolean(NBT_KEY, false);
    }
}
