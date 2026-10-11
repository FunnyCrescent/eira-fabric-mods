package com.vulcadavmod;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Wolf Compass — shows Vulcadav's location.
 * - Given to player who activated Wolf Box
 * - Updated every 5 seconds with boss coordinates in item name
 * - Kept after death (keepInventory-style)
 * - Removed when Vulcadav dies
 */
public final class WolfCompass {

    private WolfCompass() {}

    public static final String NBT_KEY = "WolfCompass";

    public static ItemStack create() {
        ItemStack stack = new ItemStack(Items.COMPASS);
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean(NBT_KEY, true);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("Волчий Компас").formatted(Formatting.RED, Formatting.BOLD));
        stack.set(DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(List.of(
                Text.literal("Указывает путь к Вулкадаву").formatted(Formatting.GRAY),
                Text.literal("Исчезнет после убийства босса").formatted(Formatting.DARK_RED)
        )));
        return stack;
    }

    public static boolean isWolfCompass(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!stack.isOf(Items.COMPASS)) return false;
        NbtComponent cd = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (cd == null || cd.isEmpty()) return false;
        return cd.copyNbt().getBoolean(NBT_KEY, false);
    }

    /**
     * Update all Wolf Compasses in all players' inventories with Vulcadav's position.
     * Called from ServerTickEvents every 5 seconds.
     */
    public static void updateAll(ServerWorld world, Entity vulcadav) {
        if (vulcadav == null || !vulcadav.isAlive()) return;

        int x = vulcadav.getBlockX();
        int y = vulcadav.getBlockY();
        int z = vulcadav.getBlockZ();

        for (ServerPlayerEntity player : world.getServer().getPlayerManager().getPlayerList()) {
            for (int i = 0; i < player.getInventory().size(); i++) {
                ItemStack stack = player.getInventory().getStack(i);
                if (isWolfCompass(stack)) {
                    // Update name with coordinates
                    stack.set(DataComponentTypes.CUSTOM_NAME,
                            Text.literal("Волчий Компас: " + x + ", " + y + ", " + z)
                                    .formatted(Formatting.RED, Formatting.BOLD));
                }
            }
        }
    }

    /**
     * Remove all Wolf Compasses from all players (when Vulcadav dies).
     */
    public static void removeAll(ServerWorld world) {
        for (ServerPlayerEntity player : world.getServer().getPlayerManager().getPlayerList()) {
            for (int i = 0; i < player.getInventory().size(); i++) {
                ItemStack stack = player.getInventory().getStack(i);
                if (isWolfCompass(stack)) {
                    player.getInventory().setStack(i, ItemStack.EMPTY);
                }
            }
            player.sendMessage(Text.literal("§cВолчий Компас исчез — Вулкадав повержен!")
                    .formatted(Formatting.RED), false);
        }
    }
}
