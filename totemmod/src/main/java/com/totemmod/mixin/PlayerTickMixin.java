package com.totemmod.mixin;

import com.totemmod.TotemType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin on PlayerEntity that implements the Totem of Sea effect:
 * while the player holds a Totem of Sea in off-hand AND is underwater:
 *   - Infinite water breathing (no drowning)
 *   - Haste II (mining speed as on land)
 *   - Dolphin's Grace (faster swimming)
 *
 * Effects ONLY apply underwater (in water or touching water).
 * On land, the totem does nothing — no permanent Haste.
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerTickMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void totemmod$seaTotemTick(CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (self.getEntityWorld().isClient()) return;

        // Only run every 10 ticks (2x per second) to reduce overhead
        if (self.age % 10 != 0) return;

        ItemStack offhand = self.getStackInHand(Hand.OFF_HAND);
        if (!TotemType.isSea(offhand)) return;

        // Only apply effects when in water (submerged or touching water)
        boolean inWater = self.isTouchingWater() || self.isSubmergedInWater();

        if (inWater) {
            // Water Breathing — prevents drowning
            self.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.WATER_BREATHING, 30, 0, true, false, false));

            // Haste II — mining speed as on land (vanilla water mining is 5x slower)
            self.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.HASTE, 30, 1, true, false, false));

            // Dolphin's Grace — faster swimming
            self.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.DOLPHINS_GRACE, 30, 0, true, false, false));

            // Restore air
            if (self.getAir() < self.getMaxAir()) {
                self.setAir(self.getMaxAir());
            }
        }
    }
}
