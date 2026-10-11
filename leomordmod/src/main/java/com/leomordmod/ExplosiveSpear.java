package com.leomordmod;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Взрывное Копьё (Explosive Spear) — drop from Leomord loot bag.
 *
 * Behavior (to be implemented via mixin):
 *   - On melee damage to target: explosion (TNT radius, no block break)
 *   - On thrown hit (spear projectile): explosion (TNT radius, no block break)
 *
 * Base item: vanilla NETHERITE_SPEAR with Fire Aspect II (matches Leomord's
 * boss spear enchant) for visual consistency + bonus damage.
 */
public final class ExplosiveSpear {

    private ExplosiveSpear() {}

    public static final String NBT_KEY = "LeomordExplosiveSpear";

    public static ItemStack create(ServerWorld world) {
        ItemStack stack = new ItemStack(Items.NETHERITE_SPEAR);
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean(NBT_KEY, true);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        stack.set(DataComponentTypes.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE);
        stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("Взрывное Копьё")
                        .formatted(Formatting.DARK_RED, Formatting.BOLD));
        stack.set(DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(List.of(
                Text.literal("Дроп с Леоморда").formatted(Formatting.GOLD),
                Text.literal("Взрыв при попадании").formatted(Formatting.DARK_RED),
                Text.literal("Не ломает блоки").formatted(Formatting.GRAY)
        )));
        stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        applyEnchantments(world, stack);
        return stack;
    }

    public static boolean isExplosiveSpear(ItemStack stack) {
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
            b.add(fireAspect, 2);
            b.add(unbreaking, 3);
        });
    }
}
