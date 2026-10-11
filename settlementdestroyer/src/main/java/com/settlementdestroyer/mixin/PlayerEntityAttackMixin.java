package com.settlementdestroyer.mixin;

import com.settlementdestroyer.ThreeFangsTrident;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin for PlayerEntity — intercepts attack(Entity) to spawn 3 waves of
 * Evoker Fangs when the player melee-attacks with the Three Fangs trident.
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityAttackMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("settlementdestroyer");

    @Inject(method = "attack", at = @At("HEAD"))
    private void settlementdestroyer$onAttack(Entity target, CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        World world = self.getEntityWorld();
        if (world.isClient()) return;
        if (!(world instanceof ServerWorld serverWorld)) return;

        ItemStack stack = self.getStackInHand(Hand.MAIN_HAND);
        LOGGER.info("[MELEE] PlayerEntity.attack called! target={}, holding={}, isThreeFangs={}",
                target.getName().getString(), stack.getItem(), ThreeFangsTrident.isThreeFangs(stack));
        if (!ThreeFangsTrident.isThreeFangs(stack)) return;

        LOGGER.info("[MELEE] spawning fang path (3 waves of 3)!");
        ThreeFangsTrident.spawnFangPath(serverWorld, self);
    }
}
