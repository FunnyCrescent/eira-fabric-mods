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
 * Elytra Cloak (Элитовый Плащ) — accessory that blocks all fall damage.
 *
 * Recipe: 1 Elytra + 8 Phantom Membranes → Elytra Cloak
 * Texture: Feather (vanilla item, Bedrock-visible via Geyser)
 *
 * Effect: While in an accessory slot, the player takes no fall damage.
 *
 * Implementation: Feather with NBT marker "ElytraCloak". Registered as an
 * accessory via AccessoriesSystem.registerAccessory().
 * LivingEntityFallDamageMixin checks if any accessory slot contains an
 * Elytra Cloak → cancels fall damage.
 */
public final class ElytraCloak {

    private ElytraCloak() {}

    public static final String NBT_KEY = "ElytraCloak";

    public static ItemStack create() {
        ItemStack stack = new ItemStack(Items.FEATHER);
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean(NBT_KEY, true);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("Элитовый Плащ")
                        .formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
        stack.set(DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(List.of(
                Text.literal("Аксессуар").formatted(Formatting.GOLD),
                Text.literal("Блокирует весь урон от падения").formatted(Formatting.GRAY),
                Text.literal("Поместите в слот аксессуара через /aks").formatted(Formatting.YELLOW)
        )));
        stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    public static boolean isElytraCloak(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!stack.isOf(Items.FEATHER)) return false;
        NbtComponent cd = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (cd == null || cd.isEmpty()) return false;
        return cd.copyNbt().getBoolean(NBT_KEY, false);
    }
}
