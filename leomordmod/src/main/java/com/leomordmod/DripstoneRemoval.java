package com.leomordmod;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.Blocks;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * DripstoneRemoval — schedules removal of pointed dripstone blocks placed
 * by LeomordHorseEntity's landing impact.
 *
 * Each "schedule" call adds an entry: target positions + tick when to remove.
 * The tick() method (called from ServerTickEvents.END_SERVER_TICK) processes
 * entries whose time has come, removing the dripstone blocks.
 *
 * We only remove blocks that are STILL pointed dripstone (we don't remove
 * blocks that the player broke or that other code replaced).
 */
public final class DripstoneRemoval {

    private DripstoneRemoval() {}

    private static final List<Entry> scheduled = new ArrayList<>();

    /** Schedule removal of the given block positions after `delayTicks` ticks. */
    public static void schedule(ServerWorld world, List<BlockPos> positions, int delayTicks) {
        long expiryTick = world.getServer().getTicks() + delayTicks;
        scheduled.add(new Entry(new ArrayList<>(positions), expiryTick));
    }

    /** Tick — call from ServerTickEvents.END_SERVER_TICK. Removes expired dripstones. */
    public static void tick(ServerWorld world) {
        long currentTick = world.getServer().getTicks();
        Iterator<Entry> it = scheduled.iterator();
        while (it.hasNext()) {
            Entry entry = it.next();
            if (currentTick < entry.expiryTick) continue;

            // Time to remove these dripstones
            for (BlockPos pos : entry.positions) {
                // Only remove if the block is STILL pointed dripstone
                // (don't remove blocks the player broke and replaced)
                if (world.getBlockState(pos).getBlock() == Blocks.POINTED_DRIPSTONE) {
                    world.removeBlock(pos, false);
                }
            }
            it.remove();
        }
        // Memory leak protection
        if (scheduled.size() > 200) scheduled.clear();
    }

    public static void clear() {
        scheduled.clear();
    }

    private static final class Entry {
        final List<BlockPos> positions;
        final long expiryTick;
        Entry(List<BlockPos> positions, long expiryTick) {
            this.positions = positions;
            this.expiryTick = expiryTick;
        }
    }
}
