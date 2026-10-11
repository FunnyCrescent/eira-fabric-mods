package com.vulcadavmod;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Wolf Rod mechanic:
 * 1. Hit target with fishing rod bobber → marks the target (command tag)
 * 2. Hit marked target with Wolf Rod (melee) → teleports target to a
 *    10×10×10 bedrock box 100 blocks above with 10 invulnerable wolves
 * 3. After 5 seconds → remove box, restore original blocks
 */
public final class WolfRodHandler {

    private WolfRodHandler() {}

    public static final String MARK_TAG = "wolfrod_marked";

    /** Active prison chambers: target UUID → chamber data */
    private static final Map<UUID, Chamber> activeChambers = new HashMap<>();

    private static final class Chamber {
        final UUID targetUuid;
        final BlockPos origin;
        final List<BlockPos> changedBlocks;
        final List<BlockState> originalStates;
        final long startTime;
        final List<UUID> wolfUuids;

        Chamber(UUID targetUuid, BlockPos origin) {
            this.targetUuid = targetUuid;
            this.origin = origin;
            this.changedBlocks = new ArrayList<>();
            this.originalStates = new ArrayList<>();
            this.startTime = System.currentTimeMillis();
            this.wolfUuids = new ArrayList<>();
        }
    }

    public static void register() {
        // Detect melee hit on marked target with Wolf Rod
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;

            ItemStack weapon = player.getStackInHand(hand);
            if (!WolfRod.isWolfRod(weapon)) return ActionResult.PASS;

            // Check if target is marked
            if (!entity.getCommandTags().contains(MARK_TAG)) {
                return ActionResult.PASS; // Not marked — do nothing
            }

            // Remove mark
            entity.removeCommandTag(MARK_TAG);

            // Create prison chamber
            if (!(world instanceof ServerWorld serverWorld)) return ActionResult.PASS;
            createChamber(serverWorld, entity);

            return ActionResult.SUCCESS;
        });
    }

    /** Create a 10×10×10 bedrock box above the target with 10 invulnerable wolves. */
    private static void createChamber(ServerWorld world, Entity target) {
        int cx = target.getBlockX();
        int cy = Math.min(target.getBlockY() + 100, 305);
        int cz = target.getBlockZ();

        Chamber chamber = new Chamber(target.getUuid(), new BlockPos(cx, cy, cz));

        // Build 10×10×10 box (walls = bedrock, interior = air)
        for (int x = 0; x < 10; x++) {
            for (int y = 0; y < 10; y++) {
                for (int z = 0; z < 10; z++) {
                    BlockPos pos = new BlockPos(cx + x, cy + y, cz + z);
                    boolean isWall = (x == 0 || x == 9 || y == 0 || y == 9 || z == 0 || z == 9);

                    // Save original state
                    BlockState original = world.getBlockState(pos);
                    if (!original.isAir() || isWall) {
                        chamber.changedBlocks.add(pos);
                        chamber.originalStates.add(original);
                    }

                    // Set block
                    if (isWall) {
                        world.setBlockState(pos, Blocks.BEDROCK.getDefaultState());
                    } else {
                        world.setBlockState(pos, Blocks.AIR.getDefaultState());
                    }
                }
            }
        }

        // Teleport target into center of chamber
        target.refreshPositionAndAngles(cx + 4.5, cy + 1, cz + 4.5, 0, 0);

        // Spawn 10 invulnerable wolves inside
        for (int i = 0; i < 10; i++) {
            WolfEntity wolf = EntityType.WOLF.create(world, net.minecraft.entity.SpawnReason.TRIGGERED);
            if (wolf == null) continue;

            double ox = world.random.nextDouble() * 6 + 1;
            double oz = world.random.nextDouble() * 6 + 1;
            wolf.refreshPositionAndAngles(cx + ox, cy + 1, cz + oz, world.random.nextFloat() * 360, 0);
            wolf.setInvulnerable(true);
            wolf.setPersistent();
            wolf.addCommandTag("wolfrod_prison");
            if (target instanceof net.minecraft.entity.LivingEntity livingTarget) {
                wolf.setTarget(livingTarget);
            }
            world.spawnEntity(wolf);
            chamber.wolfUuids.add(wolf.getUuid());
        }

        activeChambers.put(target.getUuid(), chamber);

        // Play sound
        world.playSound(null, target.getBlockPos(),
                net.minecraft.sound.SoundEvents.ENTITY_RAVAGER_ROAR,
                net.minecraft.sound.SoundCategory.HOSTILE, 2.0f, 0.5f);
    }

    /**
     * Tick — remove chambers after 5 seconds and restore blocks.
     * Uses FORCE_STATE | NOTIFY_ALL to bypass block-protection checks
     * (some worlds silently reject setBlockState without FORCE_STATE).
     * Also does a fallback scan for wolves with the prison tag (catches
     * chunk-unloaded wolves that UUID lookup misses).
     */
    public static void tick(ServerWorld world) {
        long now = System.currentTimeMillis();
        long timeout = 5000; // 5 seconds

        activeChambers.entrySet().removeIf(entry -> {
            Chamber chamber = entry.getValue();

            if (now - chamber.startTime < timeout) return false;

            // Time's up — remove wolves by UUID lookup
            for (UUID wolfUuid : chamber.wolfUuids) {
                Entity wolf = world.getEntity(wolfUuid);
                if (wolf != null) {
                    wolf.discard();
                }
            }
            // Fallback: scan for any wolf with the prison tag inside the chamber area
            // (handles chunk-unloaded wolves that came back)
            for (WolfEntity wolf : world.getEntitiesByType(EntityType.WOLF,
                    w -> w.getCommandTags().contains("wolfrod_prison")
                            && w.getBlockX() >= chamber.origin.getX() - 5
                            && w.getBlockX() <= chamber.origin.getX() + 200)) {
                wolf.discard();
            }

            // Restore blocks — use FORCE_STATE to bypass block-protection checks
            for (int i = 0; i < chamber.changedBlocks.size(); i++) {
                world.setBlockState(chamber.changedBlocks.get(i), chamber.originalStates.get(i),
                        net.minecraft.block.Block.FORCE_STATE | net.minecraft.block.Block.NOTIFY_ALL);
            }

            // Teleport target back to original position
            Entity target = world.getEntity(chamber.targetUuid);
            if (target != null && target.isAlive()) {
                target.refreshPositionAndAngles(
                        chamber.origin.getX() + 0.5,
                        chamber.origin.getY(),
                        chamber.origin.getZ() + 0.5,
                        0, 0);
            }

            return true;
        });
    }

    /** Mark a target when hit by fishing rod bobber. */
    public static void markTarget(Entity target) {
        if (!target.getCommandTags().contains(MARK_TAG)) {
            target.addCommandTag(MARK_TAG);
        }
    }
}
