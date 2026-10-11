package com.settlementdestroyer.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.EvokerFangsEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin for LivingEntity — bypasses invulnerability frames when the damage
 * source is an EvokerFangsEntity.
 *
 * In vanilla, when an entity takes damage, it gets 10 ticks (0.5 sec) of
 * invulnerability against the same damage type. This means when 3 fangs
 * spawn at the same position, only the first one deals damage — the other
 * two are blocked by invulnerability.
 *
 * This mixin resets lastDamageTime and lastDamageTaken to 0 before fang
 * damage is applied, so every fang hits independently.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityFangInvulnMixin {

    @Shadow
    private long lastDamageTime;  // field_6226 — when entity was last damaged

    @Shadow
    private float lastDamageTaken;  // field_6253 — last damage amount for comparison

    @Inject(method = "damage", at = @At("HEAD"))
    private void settlementdestroyer$resetFangInvuln(ServerWorld world, DamageSource source,
                                                      float amount,
                                                      CallbackInfoReturnable<Boolean> cir) {
        // Check if the damage source is from an EvokerFangsEntity
        if (source.getSource() instanceof EvokerFangsEntity) {
            // Reset invulnerability — set lastDamageTime to 0 (ancient) so the
            // check "world.getTime() - lastDamageTime < maxHurtTime" always fails,
            // allowing the fang damage to go through.
            this.lastDamageTime = 0L;
            this.lastDamageTaken = 0.0F;
        }
    }
}
