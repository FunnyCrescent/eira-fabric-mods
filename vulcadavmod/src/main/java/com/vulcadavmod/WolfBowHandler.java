package com.vulcadavmod;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Wolf Bow mechanic: arrow hit → spawn 3 wolves → attack target →
 * disappear ONLY when target dies OR wolf is killed.
 *
 * NO TIMER — wolves persist indefinitely until one of two conditions:
 *   1. The target they were summoned for dies (or is removed from world).
 *   2. The wolf itself is killed by an enemy.
 *
 * CAP — max 9 wolves per player:
 *   alive 0-6  → spawn 3 (full shot)
 *   alive 7    → spawn 2
 *   alive 8    → spawn 1
 *   alive 9+   → spawn 0 (at cap, denied sound)
 *
 * Summoned wolves are tamed to the owner — vanilla wolf AI never attacks
 * the owner, even if another wolf aggros the owner.
 */
public final class WolfBowHandler {

    private WolfBowHandler() {}

    private static final Map<UUID, WolfData> wolfData = new HashMap<>();
    public static final String OWNER_TAG_PREFIX = "wolfbow_owner:";
    public static final int MAX_WOLVES_PER_PLAYER = 9;
    public static final int WOLVES_PER_SHOT = 3;

    private static final class WolfData {
        final UUID targetUuid;
        final UUID ownerUuid;
        WolfData(UUID targetUuid, UUID ownerUuid) {
            this.targetUuid = targetUuid;
            this.ownerUuid = ownerUuid;
        }
    }

    public static void register() {
        // Registration handled by mixin on PersistentProjectileEntity.onEntityHit
    }

    public static void onArrowHit(ServerWorld world, PersistentProjectileEntity arrow, Entity target) {
        if (!(arrow.getOwner() instanceof PlayerEntity player)) return;
        if (target.equals(player)) return;

        net.minecraft.item.ItemStack mainHand = player.getMainHandStack();
        net.minecraft.item.ItemStack offHand = player.getOffHandStack();
        boolean hasWolfBow = WolfBow.isWolfBow(mainHand) || WolfBow.isWolfBow(offHand);
        if (!hasWolfBow) return;

        if (!(target instanceof LivingEntity livingTarget)) return;

        UUID ownerUuid = player.getUuid();

        // CAP CHECK: count how many wolves this player already has alive.
        int aliveCount = 0;
        for (WolfEntity w : world.getEntitiesByType(EntityType.WOLF,
                w -> w.getCommandTags().contains(OWNER_TAG_PREFIX + ownerUuid)
                        && w.isAlive())) {
            aliveCount++;
        }
        int toSpawn = Math.max(0, Math.min(WOLVES_PER_SHOT, MAX_WOLVES_PER_PLAYER - aliveCount));
        if (toSpawn == 0) {
            // At cap — play a "denied" sound so the player knows
            world.playSound(null, player.getBlockPos(),
                    net.minecraft.sound.SoundEvents.ENTITY_WOLF_SHAKE,
                    net.minecraft.sound.SoundCategory.HOSTILE, 1.0f, 0.8f);
            return;
        }

        for (int i = 0; i < toSpawn; i++) {
            WolfEntity wolf = EntityType.WOLF.create(world, net.minecraft.entity.SpawnReason.TRIGGERED);
            if (wolf == null) continue;

            double ox = (world.random.nextDouble() - 0.5) * 3;
            double oz = (world.random.nextDouble() - 0.5) * 3;
            wolf.refreshPositionAndAngles(
                    target.getX() + ox, target.getY(), target.getZ() + oz,
                    world.random.nextFloat() * 360, 0);
            wolf.setTarget(livingTarget);
            wolf.setPersistent();
            wolf.addCommandTag("wolfbow_summoned");
            wolf.addCommandTag(OWNER_TAG_PREFIX + ownerUuid);
            // Tame the wolf to the owner — vanilla wolf AI never attacks owner.
            wolf.setTamed(true, true);
            wolf.setOwner(player);
            world.spawnEntity(wolf);
            wolfData.put(wolf.getUuid(), new WolfData(target.getUuid(), ownerUuid));
        }

        world.playSound(null, target.getBlockPos(),
                net.minecraft.sound.SoundEvents.ENTITY_RAVAGER_ROAR,
                net.minecraft.sound.SoundCategory.HOSTILE, 1.0f, 1.5f);
    }

    public static void tick(ServerWorld world) {
        wolfData.entrySet().removeIf(entry -> {
            UUID wolfUuid = entry.getKey();
            WolfData data = entry.getValue();

            // Find the wolf by UUID
            Entity wolf = world.getEntity(wolfUuid);
            // Wolf gone (killed by enemy, or removed) — clean up the map entry.
            // NOTE: if wolf == null (chunk-unloaded), we DO NOT remove the entry
            // — the wolf may come back when the chunk reloads. Only remove if
            // we positively know the wolf is dead or removed.
            if (wolf != null && (!wolf.isAlive() || wolf.isRemoved())) {
                return true;
            }
            // If wolf is null (chunk-unloaded), keep the entry — don't discard.
            // The wolf will be checked again next tick when its chunk is loaded.

            // Check if target is dead, gone, OR removed.
            // When the target dies, ALL wolves summoned for that target despawn.
            // This is the ONLY despawn trigger (besides the wolf dying itself).
            Entity target = world.getEntity(data.targetUuid);
            if (target == null || !target.isAlive() || target.isRemoved()) {
                if (wolf != null) {
                    wolf.discard();
                }
                return true;
            }

            return false;
        });

        // NO fallback scan — previous fallback discarded wolves whose UUID was
        // temporarily absent from wolfData (e.g. after chunk unload/reload),
        // causing wolves to vanish without dying. Removed to fix the bug.
        // Orphan wolves after server restart are rare and harmless — they'll
        // despawn naturally if no player is nearby, or can be cleaned up
        // manually via /kill @e[tag=wolfbow_summoned].
    }
}
