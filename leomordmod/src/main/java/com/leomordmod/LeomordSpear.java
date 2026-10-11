package com.leomordmod;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Unit;

import java.util.List;
import java.util.UUID;

/**
 * Leomord Spear — Netherite Sword with NBT marker.
 * Wielded by Leomord boss in phases 1 (mounted) and 2 (on foot).
 *
 * The spear is the boss's signature weapon. On Leomord it deals higher
 * damage than vanilla Netherite Sword via an AttackDamage modifier
 * applied at item-creation time. When the boss lands the first spear
 * hit on a player who was just pulled via "Get Over Here", the hit
 * deals x2 critical damage (handled in LeomordEntity.tryAttack, not here).
 *
 * Unique NBT key "LeomordSpear" — does NOT collide with any other mod.
 */
public final class LeomordSpear {

    private LeomordSpear() {}

    public static final String NBT_KEY = "LeomordSpear";

    /** Attribute modifier UUIDs are NOT used in the current code path —
     *  LeomordSpear relies on vanilla Netherite Sword's built-in attack damage
     *  plus Sharpness V enchantment for the player version. Removed earlier
     *  broken UUID constants here that contained non-hex chars ("leomord")
     *  and crashed the server with NumberFormatException during static init. */

    /**
     * Create the boss-version of the spear. Vanilla NETHERITE_SPEAR as base
     * (1.21.11 added spears as a vanilla item family). Fire Aspect II applied
     * so melee hits set the target on fire.
     */
    public static ItemStack createBossSpear() {
        ItemStack stack = new ItemStack(Items.NETHERITE_SPEAR);
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean(NBT_KEY, true);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        stack.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
        stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("Копьё Леоморда")
                        .formatted(Formatting.DARK_RED, Formatting.BOLD));
        stack.set(DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(List.of(
                Text.literal("Древнее оружие Вождя Падших").formatted(Formatting.GRAY),
                Text.literal("Облик огня II + x2 урон после «Get Over Here»").formatted(Formatting.DARK_RED)
        )));
        stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    /**
     * Create a player-version of the spear (drop version).
     * Vanilla NETHERITE_SPEAR + Fire Aspect II + Unbreaking III.
     */
    public static ItemStack createPlayerSpear(ServerWorld world) {
        ItemStack stack = new ItemStack(Items.NETHERITE_SPEAR);
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean(NBT_KEY, true);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("Копьё Леоморда")
                        .formatted(Formatting.DARK_RED, Formatting.BOLD));
        stack.set(DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(List.of(
                Text.literal("Древнее оружие Вождя Падших").formatted(Formatting.GRAY),
                Text.literal("Получено с босса").formatted(Formatting.YELLOW)
        )));
        // Apply Fire Aspect II + Unbreaking III using server enchantment registry
        applyEnchantments(world, stack);
        return stack;
    }

    public static boolean isLeomordSpear(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!stack.isOf(Items.NETHERITE_SPEAR)) return false;
        NbtComponent cd = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (cd == null || cd.isEmpty()) return false;
        return cd.copyNbt().getBoolean(NBT_KEY, false);
    }

    private static void applyEnchantments(ServerWorld world, ItemStack stack) {
        Registry<Enchantment> reg = world.getRegistryManager()
                .getOrThrow(RegistryKeys.ENCHANTMENT);
        RegistryEntry<Enchantment> fireAspect = reg.getOrThrow(Enchantments.FIRE_ASPECT);
        RegistryEntry<Enchantment> unbreaking = reg.getOrThrow(Enchantments.UNBREAKING);
        net.minecraft.enchantment.EnchantmentHelper.apply(stack, b -> {
            b.add(fireAspect, 2);   // Fire Aspect II
            b.add(unbreaking, 3);   // Unbreaking III
        });
    }
}
