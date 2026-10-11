package com.leomordmod.mixin;

import com.leomordmod.LeomordProjectiles;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PlayerStunMixin — implements a clean "stun" without Slowness.
 *
 * The user complained that Slowness causes FOV distortion (the screen
 * zooms in/out when the speed attribute changes). This mixin implements
 * a separate stun mechanism:
 *
 *   - LeomordProjectiles.applyStun(player, ticks) puts the player UUID
 *     into a server-side stun map with an expiry tick.
 *   - This mixin runs every tick in PlayerEntity.tickMovement() at TAIL
 *     and, if the player is stunned, zeroes their XZ velocity. The Y
 *     velocity is preserved (gravity still works, player can fall).
 *   - Combined with Blindness + Weakness + Mining Fatigue potions
 *     (applied by LeomordProjectiles.applyStun) — those don't affect
 *     FOV but prevent the player from attacking/breaking blocks/seeing
 *     clearly while stunned.
 *
 * The player can still try to jump in place, but horizontal movement is
 * fully cancelled server-side. Client rubber-bands if it tries to move.
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerStunMixin {

    @Inject(method = "tickMovement", at = @At("TAIL"))
    private void leomordmod$stunTick(CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (self.getEntityWorld().isClient()) return;

        if (LeomordProjectiles.isStunned(self.getUuid())) {
            // Zero XZ velocity — keep Y for gravity
            var cur = self.getVelocity();
            self.setVelocity(0.0, cur.y, 0.0);
            self.velocityDirty = true;
            // Decrement stun timer; Blindness + Weakness + Mining Fatigue
            // are managed by LeomordProjectiles (re-applied as needed).
            LeomordProjectiles.tickStun(self.getUuid());
        }
    }
}
