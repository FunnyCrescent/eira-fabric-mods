package com.hellimp.mixin;

import com.hellimp.HellImpEntity;
import com.hellimp.HellImpMod;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.VexEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.dimension.DimensionOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin on VexEntity.initialize — when a Vex spawns naturally in the Nether,
 * there's a 0.5% chance to replace it with a HellImp.
 *
 * HellImp extends VexEntity, so we just create a HellImp at the same position
 * and discard the original Vex.
 */
@Mixin(VexEntity.class)
public abstract class VexEntitySpawnMixin {

    @Inject(method = "initialize", at = @At("HEAD"), cancellable = true)
    private void hellimp$maybeReplaceWithHellImp(
            ServerWorldAccess world,
            LocalDifficulty difficulty,
            SpawnReason spawnReason,
            EntityData entityData,
            CallbackInfoReturnable<EntityData> cir) {
        VexEntity self = (VexEntity) (Object) this;

        // Don't replace HellImp with HellImp (avoid recursion)
        if (self instanceof HellImpEntity) return;

        // Only in the Nether
        if (!(world instanceof ServerWorld serverWorld)) return;
        if (!serverWorld.getRegistryKey().equals(DimensionOptions.NETHER)) return;

        // Only on natural spawn (not from commands, eggs, etc.)
        if (spawnReason != SpawnReason.NATURAL) return;

        // 0.5% chance to replace
        if (serverWorld.getRandom().nextDouble() >= 0.005) return;

        // Create HellImp at the same position
        HellImpEntity imp = HellImpMod.HELL_IMP.create(serverWorld, SpawnReason.NATURAL);
        if (imp == null) return;

        imp.refreshPositionAndAngles(self.getX(), self.getY(), self.getZ(),
                self.getYaw(), self.getPitch());

        // Discard the original Vex
        self.discard();
        // Spawn the HellImp
        serverWorld.spawnEntity(imp);

        // Return null to signal vanilla not to initialize the original Vex
        cir.setReturnValue(entityData);
    }
}
