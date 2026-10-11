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
 * Символ Огня / Зажигалка (Fire Symbol) — accessory item, drop from Leomord.
 *
 * When equipped in a hellimp accessory slot, gives the player an infinite
 * fireball ability:
 *   - Right-click with empty hand → launches fireball in look direction.
 *   - Right-click looking straight down → spawns fireball under the player
 *     that bounces them very high into the air.
 *   - 45-second cooldown per use.
 *   - Persists across deaths (accessory is in slot, not main inventory).
 *
 * Base item: vanilla FIRE_CHARGE (looks like a fireball visually).
 *
 * Registered with hellimp's AccessoriesSystem.registerAccessory(NBT_KEY) so
 * the accessory slot recognizes it as an accessory.
 */
public final class FireSymbol {

    private FireSymbol() {}

    public static final String NBT_KEY = "LeomordFireSymbol";
    /** Cooldown in ticks = 45 seconds = 900 ticks. */
    public static final int COOLDOWN_TICKS = 900;

    public static ItemStack create() {
        ItemStack stack = new ItemStack(Items.FIRE_CHARGE);
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean(NBT_KEY, true);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("Символ Огня")
                        .formatted(Formatting.GOLD, Formatting.BOLD));
        stack.set(DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(List.of(
                Text.literal("Дроп с Леоморда").formatted(Formatting.DARK_RED),
                Text.literal("Аксессуар (hellimp)").formatted(Formatting.YELLOW),
                Text.literal("ПКМ — запустить файрбол").formatted(Formatting.GRAY),
                Text.literal("КД: 45 сек. Сохраняется после смерти").formatted(Formatting.DARK_GRAY)
        )));
        stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    public static boolean isFireSymbol(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!stack.isOf(Items.FIRE_CHARGE)) return false;
        NbtComponent cd = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (cd == null || cd.isEmpty()) return false;
        return cd.copyNbt().getBoolean(NBT_KEY, false);
    }
}
