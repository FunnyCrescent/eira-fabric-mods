package com.elytramod.mixin;

import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.structure.EndCityGenerator;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.ServerWorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mixin on EndCityGenerator.Piece that replaces the Elytra in End ship item
 * frames with a Totem of Inventory (a vanilla totem_of_undying carrying the
 * TotemInventory NBT marker, custom_name, and lore).
 *
 * Vanilla behavior: when the End city ship is generated, an item frame is
 * placed with a vanilla Elytra inside. We redirect the
 * `new ItemStack(Items.ELYTRA)` call inside handleMetadata() and return a
 * Totem of Inventory stack instead. The item frame is still placed and
 * spawned — only its contents change.
 *
 * This is the ONLY way to get a Totem of Inventory without crafting one
 * (the craft requires 4 Blank Totems + 1 Respawn Anchor, which is expensive).
 */
@Mixin(EndCityGenerator.Piece.class)
public abstract class EndCityPieceMixin {

    @Redirect(
        method = "handleMetadata",
        at = @At(
            value = "NEW",
            target = "(Lnet/minecraft/item/ItemConvertible;)Lnet/minecraft/item/ItemStack;"
        )
    )
    private ItemStack elytramod$replaceElytraWithTotem(net.minecraft.item.ItemConvertible item) {
        if (item == Items.ELYTRA) {
            // Return a Totem of Inventory instead of an Elytra
            return com.elytramod.ElytraMod.createTotemOfInventory();
        }
        return new ItemStack(item);
    }
}
