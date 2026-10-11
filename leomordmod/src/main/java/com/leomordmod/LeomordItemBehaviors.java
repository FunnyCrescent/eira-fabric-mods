package com.leomordmod;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Leomord Item Behaviors — handles right-click + melee attack events for
 * the 5 drop items from Leomord's loot bag.
 *
 * Implementation uses Fabric API events (no mixins needed for most behaviors):
 *   - AttackEntityCallback: LiftSword (knock up target) + ExplosiveSpear
 *     (melee explosion) + CarrionCrossbow combo x2 damage
 *   - UseItemCallback: FireSymbol (launch fireball in look direction OR
 *     under player for high bounce)
 *
 * For projectile-based behaviors (CarrionCrossbow arrows, FireBow fireballs,
 * ExplosiveSpear thrown), we use the existing PersistentProjectileHitMixin
 * (which intercepts onEntityHit/onBlockHit) and add new tag handlers there.
 */
public final class LeomordItemBehaviors {

    private LeomordItemBehaviors() {}

    /** Per-player FireSymbol cooldown: UUID → tick when cooldown expires. */
    private static final java.util.Map<UUID, Long> fireSymbolCooldown = new ConcurrentHashMap<>();

    /** Per-player CarrionCrossbow combo: UUID → tick when x2 next-hit expires. */
    private static final java.util.Map<UUID, Long> carrionCombo = new ConcurrentHashMap<>();

    public static void register() {
        registerMeleeAttackHandler();
        registerFireSymbolHandler();
    }

    // ====================================================================
    // MELEE ATTACKS — LiftSword + ExplosiveSpear + CarrionCrossbow combo
    // ====================================================================

    private static void registerMeleeAttackHandler() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
            if (!(entity instanceof LivingEntity target)) return ActionResult.PASS;
            if (!target.isAlive()) return ActionResult.PASS;
            if (!(world instanceof ServerWorld sw)) return ActionResult.PASS;

            ItemStack weapon = player.getStackInHand(hand);

            // === LiftSword: knock target up on melee hit ===
            if (LiftSword.isLiftSword(weapon)) {
                Vec3d cur = target.getVelocity();
                target.setVelocity(cur.x * 0.3, 1.5, cur.z * 0.3);
                target.velocityDirty = true;
                sw.playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.ENTITY_BREEZE_WIND_BURST.value(),
                        SoundCategory.PLAYERS, 1.0f, 1.5f);
                // Fall through to vanilla damage by returning PASS
            }

            // === ExplosiveSpear: explosion on melee hit ===
            if (ExplosiveSpear.isExplosiveSpear(weapon)) {
                // Trigger explosion at target's position (no block break)
                LeomordProjectiles.triggerExplosion(sw, player,
                        target.getX(), target.getY() + 0.5, target.getZ());
            }

            // === CarrionCrossbow combo x2 damage ===
            Long comboExpiry = carrionCombo.get(player.getUuid());
            if (comboExpiry != null) {
                long currentTick = sw.getServer().getTicks();
                if (currentTick < comboExpiry) {
                    // Apply x2 damage to this target
                    float originalDamage = (float) player.getAttributeValue(
                            net.minecraft.entity.attribute.EntityAttributes.ATTACK_DAMAGE);
                    float doubled = originalDamage * 2.0f;
                    target.damage(sw, sw.getDamageSources().playerAttack(player), doubled);
                    sw.playSound(null, target.getX(), target.getY(), target.getZ(),
                            SoundEvents.ENTITY_PLAYER_ATTACK_CRIT,
                            SoundCategory.PLAYERS, 1.0f, 1.0f);
                    // Consume the combo
                    carrionCombo.remove(player.getUuid());
                    // Return SUCCESS to skip vanilla damage (we already applied x2)
                    return ActionResult.SUCCESS;
                } else {
                    // Combo expired — clean up
                    carrionCombo.remove(player.getUuid());
                }
            }

            return ActionResult.PASS;
        });
    }

    /** Called from LeomordProjectiles.handleCarrionArrowHit when a Carrion
     *  Crossbow arrow hits an entity — marks the firing player for x2 combo. */
    public static void markCarrionCombo(UUID playerUuid, long expiryTick) {
        carrionCombo.put(playerUuid, expiryTick);
    }

    // ====================================================================
    // FIRE SYMBOL — right-click with empty hand to launch fireball
    // ====================================================================

    private static void registerFireSymbolHandler() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
            if (!(world instanceof ServerWorld sw)) return ActionResult.PASS;

            ItemStack heldStack = player.getStackInHand(hand);

            // FireSymbol triggers when player right-clicks with EMPTY hand
            // (or with a non-usable item) while FireSymbol is in their
            // accessory inventory.
            if (!heldStack.isEmpty()) {
                // If they're holding an item that has a use action, defer to vanilla
                if (heldStack.getItem() != Items.AIR && heldStack.getUseAction() != net.minecraft.item.consume.UseAction.NONE) {
                    return ActionResult.PASS;
                }
            }

            // Check if player has FireSymbol in accessory inventory
            if (!hasFireSymbolEquipped(player)) return ActionResult.PASS;

            // Check cooldown
            long currentTick = sw.getServer().getTicks();
            Long cooldownUntil = fireSymbolCooldown.get(player.getUuid());
            if (cooldownUntil != null && currentTick < cooldownUntil) {
                // Still on cooldown
                return ActionResult.PASS;
            }

            // Determine fireball direction:
            // - If player is looking STRAIGHT DOWN (pitch > 80°): spawn fireball
            //   below the player, bounces them up very high
            // - Otherwise: spawn fireball in look direction
            float pitch = player.getPitch();
            if (pitch > 80.0f) {
                // Self-launch mode — fireball spawns below player with downward velocity
                SmallFireballEntity fb = new SmallFireballEntity(
                        sw, player.getX(), player.getY() - 0.5, player.getZ(),
                        new Vec3d(0, -1.5, 0));
                fb.setOwner(player);
                sw.spawnEntity(fb);
                // Bounce player up very high
                Vec3d cur = player.getVelocity();
                player.setVelocity(cur.x * 0.2, 2.5, cur.z * 0.2);  // very high
                player.velocityDirty = true;
                sw.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 2.0f, 0.5f);
            } else {
                // Forward mode — fireball in look direction
                Vec3d look = player.getRotationVec(1.0f);
                SmallFireballEntity fb = new SmallFireballEntity(
                        sw, player, new Vec3d(look.x * 1.5, look.y * 1.5, look.z * 1.5));
                fb.setPosition(player.getX() + look.x, player.getEyeY() + look.y * 0.5, player.getZ() + look.z);
                fb.setOwner(player);
                sw.spawnEntity(fb);
                sw.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1.5f, 1.0f);
            }

            // Set cooldown (45 seconds = 900 ticks)
            fireSymbolCooldown.put(player.getUuid(), currentTick + FireSymbol.COOLDOWN_TICKS);

            return ActionResult.SUCCESS;
        });
    }

    /**
     * Check if the player has a FireSymbol in their accessory inventory
     * (requires hellimp mod to be loaded).
     */
    private static boolean hasFireSymbolEquipped(PlayerEntity player) {
        try {
            // Use hellimp's AccessoriesSystem to get the player's accessory inventory
            net.minecraft.inventory.SimpleInventory accessoryInv =
                    com.hellimp.AccessoriesSystem.getInventory(player);
            if (accessoryInv == null) return false;
            for (int i = 0; i < accessoryInv.size(); i++) {
                ItemStack stack = accessoryInv.getStack(i);
                if (FireSymbol.isFireSymbol(stack)) return true;
            }
            return false;
        } catch (Throwable t) {
            // hellimp not loaded, or some error — no fireball ability
            return false;
        }
    }
}
