package com.totemmod.mixin;

import com.totemmod.TotemType;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mixin on LivingEntity handling:
 *
 *  1) ALL custom totems (Blank, Inventory, End, Nether, Sea) are masked
 *     from tryUseDeathProtector — none of them trigger vanilla revive.
 *     Only a real Totem of Undying revives.
 *
 *  2) Totem of End: cancels void damage (OUT_OF_WORLD) and teleports
 *     the player to the nearest solid block in a SPHERE around them.
 *     Uses ServerPlayerEntity.requestTeleport for proper client sync.
 *
 *  3) Totem of Nether: cancels fire/lava damage, extinguishes.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    /** Per-player teleport cooldown — prevents repeated void teleports
     * that cause the "stuck to block" bug. After a teleport, the player
     * is immune to void damage for 5 seconds (100 ticks). */
    private static final Map<UUID, Long> teleportCooldowns = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 5000;

    @Redirect(
        method = "tryUseDeathProtector",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/item/ItemStack;get(Lnet/minecraft/component/ComponentType;)Ljava/lang/Object;"
        )
    )
    private Object totemmod$hideDeathProtectionForCustomTotems(ItemStack stack, ComponentType<?> type) {
        Object value = stack.get(type);
        if (value != null
                && type == DataComponentTypes.DEATH_PROTECTION
                && stack.isOf(Items.TOTEM_OF_UNDYING)
                && TotemType.isAnyCustom(stack)) {
            return null;
        }
        return value;
    }

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void totemmod$artifactDamagePrevention(ServerWorld world, DamageSource source, float amount,
                                                    CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (source == null) return;

        ItemStack offhand = self.getStackInHand(Hand.OFF_HAND);

        // Totem of End: void damage protection + teleport
        if (TotemType.isEnd(offhand) && source.isOf(DamageTypes.OUT_OF_WORLD)) {
            // Check cooldown — if recently teleported, just cancel damage
            // without teleporting again (prevents "stuck to block" bug)
            if (self instanceof PlayerEntity player) {
                UUID uuid = player.getUuid();
                Long lastTeleport = teleportCooldowns.get(uuid);
                long now = System.currentTimeMillis();
                if (lastTeleport != null && (now - lastTeleport) < COOLDOWN_MS) {
                    // Still on cooldown — just cancel damage, don't teleport
                    cir.setReturnValue(false);
                    return;
                }
            }
            cir.setReturnValue(false);
            if (!world.isClient()) {
                teleportToSafety(self, world);
                // Set cooldown
                if (self instanceof PlayerEntity player) {
                    teleportCooldowns.put(player.getUuid(), System.currentTimeMillis());
                }
            }
            return;
        }

        // Totem of Nether: fire/lava damage protection
        if (TotemType.isNether(offhand) && source.isIn(DamageTypeTags.IS_FIRE)) {
            cir.setReturnValue(false);
            self.extinguish();
            self.setFireTicks(0);
            return;
        }
    }

    /**
     * Teleport the entity to the nearest solid block in a SPHERE around them.
     *
     * Scans in expanding spherical shells from radius 1 to INFINITY.
     * For each shell, checks all blocks at that distance from the entity.
     * The first solid block with 2 air above it is the teleport destination.
     *
     * Uses ServerPlayerEntity.requestTeleport for proper client sync.
     */
    private void teleportToSafety(LivingEntity entity, ServerWorld world) {
        double entityX = entity.getX();
        double entityY = entity.getY();
        double entityZ = entity.getZ();

        // Search in expanding spherical shells — no limit
        for (int radius = 1; radius < Integer.MAX_VALUE; radius++) {
            // Scan the shell at this radius
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        // Only check blocks approximately at this radius (shell)
                        int distSq = dx*dx + dy*dy + dz*dz;
                        if (distSq < (radius-1)*(radius-1) || distSq > radius*radius) continue;

                        int x = (int) Math.floor(entityX) + dx;
                        int y = (int) Math.floor(entityY) + dy;
                        int z = (int) Math.floor(entityZ) + dz;

                        // Skip blocks outside world height
                        if (y < world.getBottomY() || y > 320) continue;

                        BlockPos check = new BlockPos(x, y, z);
                        if (!world.getBlockState(check).isAir()
                                && world.getBlockState(check.up()).isAir()
                                && world.getBlockState(check.up().up()).isAir()) {
                            // Found a safe spot — teleport here
                            double targetX = x + 0.5;
                            double targetY = y + 1.0;
                            double targetZ = z + 0.5;

                            if (entity instanceof ServerPlayerEntity player) {
                                // Use requestTeleport for proper client sync
                                player.requestTeleport(targetX, targetY, targetZ);
                                // Clear any lingering movement/velocity
                                player.setVelocity(Vec3d.ZERO);
                                player.velocityDirty = true;
                                // Fall distance reset so they don't take fall damage
                                player.fallDistance = 0.0f;
                            } else {
                                entity.refreshPositionAndAngles(targetX, targetY, targetZ,
                                        entity.getYaw(), entity.getPitch());
                                entity.setVelocity(Vec3d.ZERO);
                            }
                            return;
                        }
                    }
                }
            }

            // Safety: if radius exceeds 1000, fall back to spawn
            if (radius > 1000) break;
        }

        // Fallback: teleport to world spawn
        WorldProperties.SpawnPoint spawn = world.getSpawnPoint();
        BlockPos spawnPos = spawn.getPos();
        if (entity instanceof ServerPlayerEntity player) {
            player.requestTeleport(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
        } else {
            entity.refreshPositionAndAngles(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
                    entity.getYaw(), entity.getPitch());
        }
        entity.setVelocity(Vec3d.ZERO);
    }
}
