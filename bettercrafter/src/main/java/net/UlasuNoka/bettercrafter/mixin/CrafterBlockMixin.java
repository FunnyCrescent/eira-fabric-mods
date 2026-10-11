package net.UlasuNoka.bettercrafter.mixin;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.CrafterBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.CrafterBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.block.WireOrientation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin on CrafterBlock that prevents the crafter from:
 *
 *  1) Triggering (via neighbor update / redstone) when there's no valid
 *     recipe, OR when any input slot contains exactly 1 item (so you don't
 *     lose your last "template" item).
 *
 *  2) Crafting when any input slot contains exactly 1 item.
 *
 * Ported from the original 1.21.4 version to 1.21.11. The API is almost
 * identical — only the neighborUpdate signature uses WireOrientation
 * (same as 1.21.4) and the World/ServerWorld split is the same.
 */
@Mixin(CrafterBlock.class)
public abstract class CrafterBlockMixin {

    /**
     * Inject at HEAD of neighborUpdate. If the crafter would be triggered
     * but either (a) there's no valid recipe, or (b) any slot has exactly
     * 1 item — cancel the trigger and set TRIGGERED to false.
     */
    @Inject(method = "neighborUpdate", at = @At("HEAD"), cancellable = true)
    private void bettercrafter$neighborUpdateInject(BlockState state, World world, BlockPos pos,
                                                     Block sourceBlock, WireOrientation wireOrientation,
                                                     boolean notify, CallbackInfo ci) {
        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof CrafterBlockEntity crafter)) return;

        // Check if the crafter is currently triggered (CRAFTING property)
        boolean isTriggered = state.get(CrafterBlock.TRIGGERED);
        if (!isTriggered) return;

        // Check if there's a valid recipe
        var recipeOpt = CrafterBlock.getCraftingRecipe((ServerWorld) world, crafter.createRecipeInput());

        if (recipeOpt.isEmpty()) {
            // No recipe — cancel trigger
            world.setBlockState(pos, state.with(CrafterBlock.TRIGGERED, false));
            crafter.setTriggered(false);
            ci.cancel();
            return;
        }

        // Recipe exists — check if any slot has exactly 1 item
        DefaultedList<ItemStack> stacks = crafter.getHeldStacks();
        for (ItemStack stack : stacks) {
            if (stack.getCount() == 1) {
                // A slot has only 1 item — cancel to preserve the "template"
                world.setBlockState(pos, state.with(CrafterBlock.TRIGGERED, false));
                crafter.setTriggered(false);
                ci.cancel();
                return;
            }
        }
    }

    /**
     * Inject at HEAD of craft. If any input slot has exactly 1 item,
     * cancel the craft operation to preserve the last item.
     */
    @Inject(method = "craft", at = @At("HEAD"), cancellable = true)
    private static void bettercrafter$craftInject(BlockState state, ServerWorld world, BlockPos pos,
                                                   CallbackInfo ci) {
        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof CrafterBlockEntity crafter)) return;

        DefaultedList<ItemStack> stacks = crafter.getHeldStacks();
        for (ItemStack stack : stacks) {
            if (stack.getCount() == 1) {
                ci.cancel();
                return;
            }
        }
    }
}
