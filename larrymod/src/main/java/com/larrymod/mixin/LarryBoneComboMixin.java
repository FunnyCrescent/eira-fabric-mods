package com.larrymod.mixin;

import com.larrymod.LarryBone;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mixin on ServerPlayerEntity.attack that implements the Larry's Sharpened
 * Bone combo system.
 *
 * IMPORTANT: ServerPlayerEntity overrides PlayerEntity.attack, so the mixin
 * must target ServerPlayerEntity, not PlayerEntity.
 *
 * Combo: 1 → 2 → 4 → 8 → 16 → 32 (CRIT, resets to 2)
 * Time windows: 16s → 8s → 4s → 2s → 0.5s → reset
 */
@Mixin(ServerPlayerEntity.class)
public abstract class LarryBoneComboMixin {

    private static final Map<UUID, ComboState> comboStates = new HashMap<>();

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void larrymod$handleBoneAttack(Entity target, CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        if (player.getEntityWorld().isClient()) return;

        ItemStack mainHand = player.getStackInHand(Hand.MAIN_HAND);
        if (!LarryBone.isLarryBone(mainHand)) return;

        if (!(target instanceof LivingEntity livingTarget)) return;
        if (!(player.getEntityWorld() instanceof ServerWorld world)) return;

        UUID playerId = player.getUuid();
        long currentTime = System.currentTimeMillis();
        ComboState state = comboStates.get(playerId);

        if (state == null) {
            // First hit ever — stage 0 (1 damage)
            state = new ComboState(0, currentTime);
            comboStates.put(playerId, state);
        } else {
            long timeWindow = LarryBone.getTimeWindowForStage(state.stage);
            long elapsed = currentTime - state.lastHitTime;
            if (elapsed > timeWindow) {
                // Window expired — reset to stage 0
                state.stage = 0;
            } else {
                // Within window — advance stage
                state.stage = LarryBone.getNextStage(state.stage);
            }
            state.lastHitTime = currentTime;
        }

        float damage = LarryBone.getDamageForStage(state.stage);

        // Apply damage directly to the target
        DamageSource source = world.getDamageSources().playerAttack(player);
        target.damage(world, source, damage);

        // Combo feedback in action bar
        String comboText;
        if (state.stage == 5) {
            comboText = "§e§lКРИТ! §c" + (int) damage + " урона §7(серия сброшена)";
            // After CRIT, reset to stage 1 (2 damage for next hit)
            state.stage = 1;
            state.lastHitTime = currentTime;
        } else {
            int nextStage = state.stage + 1;
            float nextDamage = LarryBone.getDamageForStage(nextStage);
            long nextWindow = LarryBone.getTimeWindowForStage(state.stage);
            String timeStr = nextWindow >= 1000
                    ? (nextWindow / 1000) + " сек"
                    : (nextWindow / 100.0) + " сек";
            comboText = "§6Удар " + (state.stage + 1) + ": §c" + (int) damage + " урона "
                    + "§7→ след: " + (int) nextDamage + " (" + timeStr + ")";
        }
        player.sendMessage(Text.literal(comboText), true);

        // Swing hand for animation
        player.swingHand(Hand.MAIN_HAND);

        // Cancel vanilla attack — we handled it
        ci.cancel();
    }

    private static class ComboState {
        int stage;
        long lastHitTime;

        ComboState(int stage, long lastHitTime) {
            this.stage = stage;
            this.lastHitTime = lastHitTime;
        }
    }
}
