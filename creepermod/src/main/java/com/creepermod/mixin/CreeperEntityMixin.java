package com.creepermod.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import net.minecraft.world.explosion.ExplosionBehavior;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mixin on CreeperEntity that makes creeper explosions:
 *  - NOT destroy any blocks (custom ExplosionBehavior.canDestroyBlock → false)
 *  - Deal DOUBLE damage (power × 2, which approximately doubles entity damage)
 *
 * The @Redirect intercepts the call to ServerWorld.createExplosion(Entity, DDD,
 * F, ExplosionSourceType) inside CreeperEntity#explode() and replaces it with
 * the full overload that accepts an ExplosionBehavior. The custom behavior
 * overrides canDestroyBlock to always return false, preventing any block
 * destruction. The power parameter is doubled before being passed through.
 *
 * Result: creepers still explode, still damage entities (2× as much), still
 * make explosion sounds and particles — but the terrain is untouched.
 */
@Mixin(CreeperEntity.class)
public abstract class CreeperEntityMixin {

    @Redirect(
        method = "explode",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/world/ServerWorld;createExplosion(Lnet/minecraft/entity/Entity;DDDFLnet/minecraft/world/World$ExplosionSourceType;)V"
        )
    )
    private void creepermod$noBlocksDoubleDamage(
            ServerWorld world, Entity entity,
            double x, double y, double z,
            float power,
            World.ExplosionSourceType sourceType) {

        // Custom behavior: never destroy blocks, but damage entities normally.
        ExplosionBehavior behavior = new ExplosionBehavior() {
            @Override
            public boolean canDestroyBlock(Explosion explosion, BlockView blockView,
                                           BlockPos pos, BlockState state, float p) {
                return false;
            }
        };

        // Double the power → roughly double the entity damage.
        // (Explosion damage is proportional to power in the vanilla formula.)
        float doubledPower = power * 2.0f;

        // Call the full overload: createExplosion(Entity, DamageSource, ExplosionBehavior,
        //   double, double, double, float, boolean, ExplosionSourceType)
        // DamageSource = null (the method creates one internally)
        // boolean = false (no fire, same as vanilla creeper)
        world.createExplosion(entity, null, behavior, x, y, z, doubledPower, false, sourceType);
    }
}
