package com.totemmod.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin on WardenEntity that makes the Warden break a 3x3x3 cube of blocks
 * around itself when it is suffocating (stuck inside blocks).
 *
 * Vanilla Wardens can get stuck inside blocks when spawned or teleported.
 * Normally they take suffocation damage and die. With this mixin, the Warden
 * instead destroys the blocks around it to free itself — making it a much
 * more formidable opponent.
 *
 * The check runs every 20 ticks (1 second). If the Warden's eye position is
 * inside a non-air block that causes suffocation, it breaks all non-air
 * blocks in a 3x3x3 cube centered on its block position. Unbreakable blocks
 * (bedrock, barrier, etc. — hardness < 0) are skipped.
 */
@Mixin(WardenEntity.class)
public abstract class WardenMixin {

    @Inject(method = "mobTick", at = @At("HEAD"))
    private void totemmod$breakBlocksWhenStuck(ServerWorld world, CallbackInfo ci) {
        WardenEntity self = (WardenEntity) (Object) this;
        if (world.isClient()) return;

        // Only check every 20 ticks (1 second)
        if (self.age % 20 != 0) return;

        // Check if the Warden is suffocating (eye position inside a solid block)
        Vec3d eyePos = self.getEyePos();
        BlockPos eyeBlock = BlockPos.ofFloored(eyePos);
        BlockState eyeState = world.getBlockState(eyeBlock);

        if (eyeState.isAir()) return;
        if (!eyeState.shouldSuffocate(world, eyeBlock)) return;

        // The Warden is stuck — break a 3x3x3 cube around it
        BlockPos center = self.getBlockPos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = center.add(dx, dy, dz);
                    BlockState state = world.getBlockState(pos);
                    if (state.isAir()) continue;
                    // Don't break unbreakable blocks (bedrock, barrier, etc.)
                    if (state.getHardness(world, pos) < 0) continue;
                    // Break the block (drop items)
                    world.breakBlock(pos, true, self);
                }
            }
        }
    }
}
