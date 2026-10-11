package com.larrymod;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * "Точенная кость Ларри" (Larry's Sharpened Bone) — legendary weapon.
 *
 * It's a vanilla BONE item (renders as bone on all clients including Bedrock)
 * but behaves as a sword with a unique combo system:
 *
 *  - Each hit in a series doubles the damage: 1, 2, 4, 8, 16, 32 (max)
 *  - Time windows between hits:
 *      Hit 1 (1 dmg) → 16 sec for hit 2
 *      Hit 2 (2 dmg) → 8 sec for hit 3
 *      Hit 3 (4 dmg) → 4 sec for hit 4
 *      Hit 4 (8 dmg) → 2 sec for hit 5
 *      Hit 5 (16 dmg) → 0.5 sec for hit 6 (CRIT)
 *      Hit 6 (32 dmg, CRIT) → resets to 2 dmg
 *
 *  - If the time window expires, the combo resets to 1 dmg
 *  - Identified by custom_data NBT marker "LarryBone"
 */
public final class LarryBone {

    private LarryBone() {}

    public static final String BONE_NBT_KEY = "LarryBone";

    /** Time windows in milliseconds for each combo stage. */
    public static final long[] COMBO_WINDOWS_MS = {
            16000, // after hit 1 (1 dmg): 16 sec for hit 2
            8000,  // after hit 2 (2 dmg): 8 sec for hit 3
            4000,  // after hit 3 (4 dmg): 4 sec for hit 4
            2000,  // after hit 4 (8 dmg): 2 sec for hit 5
            2000,  // after hit 5 (16 dmg): 2 sec for hit 6 (CRIT) — was 0.5s, too fast for Bedrock
            16000  // after hit 6 (32 dmg CRIT): 16 sec (resets to 2)
    };

    /** Damage for each combo stage. */
    public static final float[] COMBO_DAMAGE = {
            1.0f,  // hit 1
            2.0f,  // hit 2
            4.0f,  // hit 3
            8.0f,  // hit 4
            16.0f, // hit 5
            32.0f  // hit 6 (CRIT, then resets)
    };

    /** Create a Larry's Sharpened Bone item stack. */
    public static ItemStack create() {
        ItemStack stack = new ItemStack(Items.BONE);
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean(BONE_NBT_KEY, true);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("Точенная кость Ларри").formatted(Formatting.GOLD, Formatting.BOLD));
        stack.set(DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(java.util.List.of(
                Text.literal("Легендарное оружие").formatted(Formatting.YELLOW),
                Text.literal("Серия ударов: 1 → 2 → 4 → 8 → 16 → 32").formatted(Formatting.GRAY),
                Text.literal("Чем сильнее серия, тем меньше времени").formatted(Formatting.GRAY),
                Text.literal("на следующий удар").formatted(Formatting.GRAY)
        )));
        return stack;
    }

    /** Check if an item stack is a Larry's Sharpened Bone. */
    public static boolean isLarryBone(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!stack.isOf(Items.BONE)) return false;
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) return false;
        return customData.copyNbt().getBoolean(BONE_NBT_KEY, false);
    }

    /**
     * Get the damage for the current combo stage.
     * @param comboStage 0-5 (0=first hit, 5=CRIT)
     * @return damage value
     */
    public static float getDamageForStage(int comboStage) {
        if (comboStage < 0 || comboStage >= COMBO_DAMAGE.length) return 1.0f;
        return COMBO_DAMAGE[comboStage];
    }

    /**
     * Get the time window for the current combo stage.
     * @param comboStage 0-5 (0=after first hit, etc.)
     * @return time window in milliseconds
     */
    public static long getTimeWindowForStage(int comboStage) {
        if (comboStage < 0 || comboStage >= COMBO_WINDOWS_MS.length) return 16000;
        return COMBO_WINDOWS_MS[comboStage];
    }

    /**
     * Get the next combo stage after a hit.
     * After stage 5 (32 dmg CRIT), resets to stage 1 (2 dmg).
     */
    public static int getNextStage(int currentStage) {
        if (currentStage >= 5) return 1; // After CRIT, reset to 2 dmg
        return currentStage + 1;
    }
}
