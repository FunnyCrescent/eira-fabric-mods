package com.firemod.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FireBlock;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin on FireBlock that enhances fire behavior.
 *
 * Design goals:
 *  1) Fire spreads at VANILLA speed (not faster) — just lets vanilla handle
 *     spread, then cleans up so no logs/leaves are left floating.
 *  2) When a flammable block is destroyed by fire (vanilla does this), we
 *     make sure the block is fully replaced by fire, not just air — so the
 *     fire keeps spreading to neighbors.
 *  3) Register ADDITIONAL flammable blocks that vanilla doesn't burn:
 *     trapdoors, chests (wooden), beds, fences, fence gates, doors (wooden),
 *     bookshelves, crafting tables, jukeboxes, note blocks, banners, signs,
 *     barrels, beehives, mushroom blocks, scaffolding, bamboo, etc.
 *
 * What we DON'T burn:
 *  - Iron doors, iron trapdoors
 *  - Copper/iron/gold/diamond/netherite blocks
 *  - Stone, cobblestone, bricks, etc.
 *  - Ender chests (they're made of obsidian)
 *  - Shulker boxes (they're ender-based)
 */
@Mixin(FireBlock.class)
public abstract class FireBlockMixin {

    /**
     * After vanilla fire tick, scan a small area around the fire.
     * If any logs or leaves are adjacent to fire AND have no support
     * (no solid block below within 2 blocks), remove them — this prevents
     * floating tree remnants. Vanilla fire already burns blocks, but sometimes
     * leaves half-burned logs in the air.
     */
    @Inject(method = "scheduledTick", at = @At("RETURN"))
    private void firemod$cleanupFloatingWood(BlockState state, ServerWorld world, BlockPos pos,
                                              Random random, CallbackInfo ci) {
        // Only run occasionally to avoid lag (every ~5 seconds on average)
        if (random.nextInt(100) != 0) return;

        // Scan 3x3x3 around fire for floating logs/leaves
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos target = pos.add(dx, dy, dz);
                    BlockState targetState = world.getBlockState(target);

                    if (isWoodOrLeaves(targetState) && isFloating(world, target)) {
                        // Remove the floating block and place fire
                        world.removeBlock(target, false);
                        if (world.getBlockState(target).isAir()) {
                            world.setBlockState(target, Blocks.FIRE.getDefaultState());
                        }
                    }
                }
            }
        }
    }

    /**
     * Check if a wood/leaves block is "floating" — no solid support below
     * within 2 blocks. This catches partially-burned trees.
     */
    private boolean isFloating(ServerWorld world, BlockPos pos) {
        for (int dy = 1; dy <= 2; dy++) {
            BlockPos below = pos.down(dy);
            BlockState belowState = world.getBlockState(below);
            if (!belowState.isAir() && !isWoodOrLeaves(belowState)) {
                return false; // Found solid support
            }
        }
        return true;
    }

    private boolean isWoodOrLeaves(BlockState state) {
        return state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.LEAVES)
                || state.isIn(BlockTags.PLANKS);
    }
}
