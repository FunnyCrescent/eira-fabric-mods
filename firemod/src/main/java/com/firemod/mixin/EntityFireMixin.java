package com.firemod.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin on Entity that makes burning entities ignite blocks around them.
 *
 * Vanilla behavior: burning entities don't set blocks on fire (only players
 * with certain items or mobs that shoot fireballs do).
 *
 * Our change: every 2 seconds (40 ticks) while an entity is on fire, check
 * the block the entity is standing on. If it's flammable, there's a small
 * chance (20%) to ignite it. This makes burning zombies walking through
 * forests a real hazard, but doesn't make fire spread insanely fast.
 */
@Mixin(Entity.class)
public abstract class EntityFireMixin {

    @Inject(method = "tick", at = @At("RETURN"))
    private void firemod$igniteBlocksWhenBurning(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        World world = self.getEntityWorld();
        if (world.isClient()) return;

        // Only run every 40 ticks (2 seconds) — vanilla fire spread rate
        if (self.age % 40 != 0) return;

        // Check if the entity is on fire
        if (self.getFireTicks() <= 0) return;

        if (!(world instanceof ServerWorld serverWorld)) return;

        // Check the block the entity is standing on and one block around
        BlockPos center = self.getBlockPos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos target = center.add(dx, 0, dz);
                net.minecraft.block.BlockState state = serverWorld.getBlockState(target);

                if (state.isBurnable()) {
                    BlockPos above = target.up();
                    if (serverWorld.getBlockState(above).isAir()) {
                        // 20% chance per check (every 2 seconds)
                        if (serverWorld.random.nextInt(5) == 0) {
                            serverWorld.setBlockState(above,
                                    net.minecraft.block.Blocks.FIRE.getDefaultState());
                        }
                    }
                }
            }
        }
    }
}
