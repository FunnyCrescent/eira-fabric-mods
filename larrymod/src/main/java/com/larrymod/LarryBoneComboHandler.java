package com.larrymod;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Larry's Sharpened Bone combo system — implemented via AttackEntityCallback
 * instead of mixin. This is more reliable and works on both Java and Bedrock.
 *
 * Combo: 1 → 2 → 4 → 8 → 16 → 32 (CRIT, resets to 2)
 * Time windows: 16s → 8s → 4s → 2s → 0.5s
 */
public final class LarryBoneComboHandler {

    private LarryBoneComboHandler() {}

    private static final Map<UUID, ComboState> comboStates = new HashMap<>();

    public static void register() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;

            ItemStack weapon = player.getStackInHand(hand);
            if (!LarryBone.isLarryBone(weapon)) return ActionResult.PASS;

            if (!(entity instanceof LivingEntity)) return ActionResult.PASS;
            if (!(world instanceof ServerWorld serverWorld)) return ActionResult.PASS;

            // Handle combo
            UUID playerId = player.getUuid();
            long currentTime = System.currentTimeMillis();
            ComboState state = comboStates.get(playerId);

            if (state == null) {
                state = new ComboState(0, currentTime);
                comboStates.put(playerId, state);
            } else {
                long timeWindow = LarryBone.getTimeWindowForStage(state.stage);
                long elapsed = currentTime - state.lastHitTime;
                if (elapsed > timeWindow) {
                    state.stage = 0; // Reset
                } else {
                    state.stage = LarryBone.getNextStage(state.stage);
                }
                state.lastHitTime = currentTime;
            }

            float damage = LarryBone.getDamageForStage(state.stage);

            // Apply damage
            DamageSource source = serverWorld.getDamageSources().playerAttack(player);
            entity.damage(serverWorld, source, damage);

            // Feedback
            String comboText;
            if (state.stage == 5) {
                comboText = "§e§lКРИТ! §c" + (int) damage + " урона §7(серия сброшена)";
                state.stage = 1;
                state.lastHitTime = currentTime;
            } else {
                int nextStage = state.stage + 1;
                float nextDamage = LarryBone.getDamageForStage(nextStage);
                long nextWindow = LarryBone.getTimeWindowForStage(state.stage);
                String timeStr = nextWindow >= 1000
                        ? (nextWindow / 1000) + " сек"
                        : String.format("%.1f сек", nextWindow / 1000.0);
                comboText = "§6Удар " + (state.stage + 1) + ": §c" + (int) damage
                        + " урона §7→ след: " + (int) nextDamage + " (" + timeStr + ")";
            }
            player.sendMessage(Text.literal(comboText), true);
            player.swingHand(Hand.MAIN_HAND);

            // Cancel vanilla attack
            return ActionResult.SUCCESS;
        });
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
