package com.hellimp;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Accessories System — 6 slots (7 with Heart of Demon).
 *
 * Slots are stored in-memory (per-player) and saved to the player's NBT data
 * via PlayerEntityNbtMixin (writeCustomData/readCustomData).
 *
 * On player death: accessories are NOT dropped — they're not in the player's
 * vanilla inventory, they're in a separate SimpleInventory.
 *
 * GUI: opened via /aks command.
 *
 * Only registered accessory items can be placed in slots. Future mods register
 * their accessories via AccessoriesSystem.registerAccessory("nbtKey").
 */
public class AccessoriesSystem {

    public static final int BASE_SLOTS = 6;
    public static final int MAX_SLOTS = 7;

    /** Per-player accessory inventories (size = MAX_SLOTS). */
    private static final Map<UUID, SimpleInventory> playerInventories = new HashMap<>();
    /** Per-player "extended" flag (true if Heart of Demon used). */
    private static final Map<UUID, Boolean> extendedSlots = new HashMap<>();

    /** Registered accessory NBT keys — items with these keys are accessories. */
    private static final java.util.Set<String> registeredAccessories = new java.util.HashSet<>();

    public static void registerAccessory(String nbtKey) {
        registeredAccessories.add(nbtKey);
    }

    public static boolean isAccessory(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var cd = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
        if (cd == null || cd.isEmpty()) return false;
        NbtCompound nbt = cd.copyNbt();
        for (String key : registeredAccessories) {
            if (nbt.getBoolean(key, false)) return true;
        }
        return false;
    }

    /**
     * Get or create the player's accessory inventory.
     */
    public static SimpleInventory getInventory(PlayerEntity player) {
        return playerInventories.computeIfAbsent(player.getUuid(),
                uuid -> new SimpleInventory(MAX_SLOTS));
    }

    public static boolean hasExtendedSlots(PlayerEntity player) {
        return extendedSlots.getOrDefault(player.getUuid(), false);
    }

    public static void setExtendedSlots(PlayerEntity player, boolean extended) {
        extendedSlots.put(player.getUuid(), extended);
    }

    public static int getSlotCount(PlayerEntity player) {
        return hasExtendedSlots(player) ? MAX_SLOTS : BASE_SLOTS;
    }

    /**
     * Save player's accessory data to WriteView (called from PlayerEntityNbtMixin).
     * Saves: extended flag (boolean), and for each slot: item id + custom data.
     */
    public static void saveToView(PlayerEntity player, net.minecraft.storage.WriteView view) {
        SimpleInventory inv = playerInventories.get(player.getUuid());
        boolean extended = extendedSlots.getOrDefault(player.getUuid(), false);
        view.putBoolean("hellimp_extended", extended);
        if (inv == null) return;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty()) continue;
            var cd = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
            if (cd == null) continue;
            String idStr = stack.getItem().toString();
            String dataStr = cd.copyNbt().toString();
            view.putString("hellimp_slot_" + i + "_id", idStr);
            view.putString("hellimp_slot_" + i + "_data", dataStr);
        }
    }

    /**
     * Load player's accessory data from ReadView (called from PlayerEntityNbtMixin).
     */
    public static void loadFromView(PlayerEntity player, net.minecraft.storage.ReadView view) {
        boolean extended = view.getBoolean("hellimp_extended", false);
        extendedSlots.put(player.getUuid(), extended);
        SimpleInventory inv = getInventory(player);
        inv.clear();
        for (int i = 0; i < inv.size(); i++) {
            String idStr = view.getString("hellimp_slot_" + i + "_id", "");
            if (idStr.isEmpty()) continue;
            net.minecraft.item.Item item = net.minecraft.registry.Registries.ITEM.get(
                    net.minecraft.util.Identifier.of(idStr));
            if (item == null) continue;
            ItemStack stack = new ItemStack(item);
            String dataStr = view.getString("hellimp_slot_" + i + "_data", "");
            if (!dataStr.isEmpty()) {
                try {
                    NbtCompound data = net.minecraft.nbt.StringNbtReader.readCompound(dataStr);
                    stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
                            net.minecraft.component.type.NbtComponent.of(data));
                } catch (Exception ignored) {}
            }
            inv.setStack(i, stack);
        }
    }

    public static void register() {
        // No registration needed — system is static
    }
}
