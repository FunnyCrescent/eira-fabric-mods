package com.villagermod.mixin;

import net.minecraft.entity.ai.brain.task.GiveGiftsToHeroTask;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Disables the "give gifts to hero" villager AI task. Even if a player has
 * the Hero of the Village status effect, villagers will never run the gift
 * task — no free items dropped at the player's feet.
 *
 * (We also disable Raid#addHero elsewhere so the hero set is always empty,
 * but this mixin is a belt-and-suspenders guard against any other code path
 * that might trigger the gift task.)
 */
@Mixin(GiveGiftsToHeroTask.class)
public abstract class GiveGiftsToHeroTaskMixin {

    @Inject(method = "shouldRun", at = @At("HEAD"), cancellable = true)
    private void villagermod$neverRun(ServerWorld world, VillagerEntity entity,
                                      CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
