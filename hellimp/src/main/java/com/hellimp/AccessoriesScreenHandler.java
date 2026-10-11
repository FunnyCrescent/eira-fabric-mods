package com.hellimp;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

/**
 * ScreenHandler for the Accessories GUI.
 *
 * Displays 6 (or 7) accessory slots + 27 player inventory slots + 9 hotbar slots.
 *
 * Only accessory items can be placed in accessory slots — see insertItem
 * override in AccessorySlot.
 */
public class AccessoriesScreenHandler extends ScreenHandler {

    private final SimpleInventory accessoryInventory;
    private final int slotCount;

    public AccessoriesScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, AccessoriesSystem.getInventory(playerInventory.player),
                AccessoriesSystem.getSlotCount(playerInventory.player));
    }

    public AccessoriesScreenHandler(int syncId, PlayerInventory playerInventory,
                                     SimpleInventory accessoryInventory, int slotCount) {
        super(ScreenHandlerType.GENERIC_9X3, syncId); // 9x3 = 27 slots for player inv
        this.accessoryInventory = accessoryInventory;
        this.slotCount = slotCount;

        // Accessory slots — top row (slots 0..slotCount-1)
        // We use a 9-wide layout. Accessory slots are 0..slotCount-1,
        // remaining slots are barriers (handled by client).
        for (int i = 0; i < 9; i++) {
            if (i < slotCount) {
                this.addSlot(new AccessorySlot(accessoryInventory, i, 8 + i * 18, 18));
            } else {
                // Locked slot — use a slot that doesn't allow insertion
                this.addSlot(new LockedSlot(accessoryInventory, i, 8 + i * 18, 18));
            }
        }

        // Player inventory — 3 rows of 9 (slots 9..35)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        8 + col * 18, 40 + row * 18));
            }
        }

        // Hotbar — 1 row of 9 (slots 36..44)
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 98));
        }
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (slot == null || !slot.hasStack()) return ItemStack.EMPTY;

        ItemStack original = slot.getStack().copy();
        ItemStack stack = slot.getStack();

        if (slotIndex < slotCount) {
            // Moving FROM accessory slot TO player inventory
            if (!this.insertItem(stack, slotCount, this.slots.size(), false)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Moving FROM player inventory TO accessory slot (only if accessory)
            if (AccessoriesSystem.isAccessory(stack)) {
                if (!this.insertItem(stack, 0, slotCount, false)) {
                    return ItemStack.EMPTY;
                }
            }
        }

        if (stack.isEmpty()) {
            slot.setStack(ItemStack.EMPTY);
        } else {
            slot.markDirty();
        }

        return original;
    }

    /**
     * Custom slot that only accepts accessory items.
     */
    private static class AccessorySlot extends Slot {
        public AccessorySlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return AccessoriesSystem.isAccessory(stack);
        }

        @Override
        public int getMaxItemCount() {
            return 1;
        }
    }

    /**
     * Locked slot — does not allow insertion (for unused accessory slots).
     */
    private static class LockedSlot extends Slot {
        public LockedSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return false;
        }

        @Override
        public boolean canTakeItems(net.minecraft.entity.player.PlayerEntity player) {
            return false;
        }
    }
}
