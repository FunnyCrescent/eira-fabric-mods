package com.vulcadavmod;

import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Grave system — when a player dies (without Totem of Inventory),
 * their items are placed in a chest at the death location.
 * Coordinates are announced to ALL players in chat.
 */
public final class GraveSystem {

    private GraveSystem() {}

    public static final String GRAVE_TAG = "GraveChest";

    /**
     * Create a grave at the player's death location.
     * Moves all inventory items into a chest, announces coordinates.
     * Called from totemmod's ServerPlayerDeathMixin when NO Totem of Inventory
     * is found (fallback death behavior).
     */
    public static void createGrave(ServerPlayerEntity player, ServerWorld world) {
        BlockPos pos = player.getBlockPos();

        // Find a safe position for the chest (replace air or replaceable block)
        BlockPos chestPos = pos;
        if (!world.getBlockState(chestPos).isAir() && !world.getBlockState(chestPos).isReplaceable()) {
            chestPos = pos.up();
        }

        // Place chest
        world.setBlockState(chestPos, Blocks.CHEST.getDefaultState());

        // Get chest block entity
        if (world.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
            // Mark as grave

            // Move all items from player inventory into chest
            PlayerInventory inv = player.getInventory();
            List<ItemStack> items = new ArrayList<>();
            for (int i = 0; i < inv.size(); i++) {
                ItemStack stack = inv.getStack(i);
                if (!stack.isEmpty()) {
                    items.add(stack.copy());
                }
            }

            // Clear player inventory
            for (int i = 0; i < inv.size(); i++) {
                inv.setStack(i, ItemStack.EMPTY);
            }

            // Put items in chest
            for (int i = 0; i < items.size() && i < 27; i++) {
                chest.setStack(i, items.get(i));
            }

            // If more than 27 stacks, drop the rest
            for (int i = 27; i < items.size(); i++) {
                player.dropStack(world, items.get(i));
            }

            chest.markDirty();
        }

        // Announce to ALL players
        String coords = chestPos.getX() + ", " + chestPos.getY() + ", " + chestPos.getZ();
        Text announce = Text.literal("§6☠ Могила " + player.getName().getString()
                + ": " + coords + " §7(X, Y, Z)").formatted(Formatting.GOLD);
        world.getServer().getPlayerManager().broadcast(announce, false);

        player.sendMessage(Text.literal("§cВаши вещи в могиле: " + coords)
                .formatted(Formatting.RED), false);
    }
}
