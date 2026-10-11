package com.totemmod;

import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Grave barrel management — tracks placed graves and removes them
 * when empty or after 30 minutes.
 */
public final class GraveManager {

    private GraveManager() {}

    private static final Map<BlockPos, GraveData> GRAVES = new ConcurrentHashMap<>();

    public static void registerGrave(BlockPos pos, net.minecraft.registry.RegistryKey<net.minecraft.world.World> dim,
                                      long removeAtTick, UUID owner) {
        GRAVES.put(pos, new GraveData(pos, dim, removeAtTick, owner));
    }

    public static void tick(MinecraftServer server) {
        var iter = GRAVES.entrySet().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            GraveData data = entry.getValue();

            ServerWorld world = server.getWorld(data.dim);
            if (world == null) {
                iter.remove();
                continue;
            }

            // Check if barrel is empty → remove
            if (world.getBlockEntity(data.pos) instanceof BarrelBlockEntity barrel) {
                boolean empty = true;
                for (int i = 0; i < barrel.size(); i++) {
                    if (!barrel.getStack(i).isEmpty()) {
                        empty = false;
                        break;
                    }
                }
                if (empty) {
                    world.removeBlock(data.pos, false);
                    iter.remove();
                    continue;
                }
            } else {
                iter.remove();
                continue;
            }

            // Check 30-minute timeout
            if (world.getTime() >= data.removeAtTick) {
                world.removeBlock(data.pos, false);
                iter.remove();
            }
        }
    }

    private static final class GraveData {
        final BlockPos pos;
        final net.minecraft.registry.RegistryKey<net.minecraft.world.World> dim;
        final long removeAtTick;
        final UUID owner;

        GraveData(BlockPos pos, net.minecraft.registry.RegistryKey<net.minecraft.world.World> dim,
                  long removeAtTick, UUID owner) {
            this.pos = pos;
            this.dim = dim;
            this.removeAtTick = removeAtTick;
            this.owner = owner;
        }
    }
}
