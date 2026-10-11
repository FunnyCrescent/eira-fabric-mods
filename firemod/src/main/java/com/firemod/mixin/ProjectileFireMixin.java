package com.firemod.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin on PersistentProjectileEntity that makes flame arrows ignite blocks
 * when they hit them.
 *
 * Vanilla behavior: flame arrows only set entities on fire. When they hit a
 * block, nothing happens.
 *
 * Our change: when a flame arrow (or any burning projectile) hits a block,
 * set fire to the block it hit and try to ignite adjacent flammable blocks.
 *
 * How we detect flame arrows: the arrow entity itself has fireTicks > 0
 * (set by the Flame enchantment in vanilla). We also check the block the
 * arrow hit — if it's flammable, we ignite it.
 */
@Mixin(PersistentProjectileEntity.class)
public abstract class ProjectileFireMixin {

    @Inject(method = "onBlockHit", at = @At("RETURN"))
    private void firemod$igniteOnBlockHit(BlockHitResult blockHitResult, CallbackInfo ci) {
        PersistentProjectileEntity self = (PersistentProjectileEntity) (Object) this;
        if (self.getEntityWorld().isClient()) return;
        if (!(self.getEntityWorld() instanceof ServerWorld serverWorld)) return;

        // Check if this projectile is on fire (flame arrows have fireTicks > 0)
        boolean isFlaming = self.getFireTicks() > 0 || self.isOnFire();

        BlockPos hitPos = blockHitResult.getBlockPos();
        Direction face = blockHitResult.getSide();

        if (isFlaming) {
            // Place fire on the face the arrow hit
            BlockPos firePos = hitPos.offset(face);
            BlockState fireState = serverWorld.getBlockState(firePos);
            if (fireState.isAir()) {
                serverWorld.setBlockState(firePos, Blocks.FIRE.getDefaultState());
            }

            // Also try to ignite adjacent flammable blocks
            for (Direction dir : Direction.values()) {
                BlockPos adjacent = hitPos.offset(dir);
                BlockState adjState = serverWorld.getBlockState(adjacent);
                if (adjState.isBurnable()) {
                    BlockPos above = adjacent.up();
                    if (serverWorld.getBlockState(above).isAir()) {
                        // 50% chance to ignite each adjacent flammable block
                        if (serverWorld.random.nextBoolean()) {
                            serverWorld.setBlockState(above, Blocks.FIRE.getDefaultState());
                        }
                    }
                }
            }
        }
    }
}
