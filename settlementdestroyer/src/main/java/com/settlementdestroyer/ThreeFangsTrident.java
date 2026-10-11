package com.settlementdestroyer;

import com.settlementdestroyer.mixin.PersistentProjectileEntityInvoker;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.EvokerFangsEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Three Fangs — custom Trident with built-in Loyalty III.
 *
 * Mechanics (latest revision):
 *   - Throw: when trident hits entity or block, spawn 3 Evoker Fangs at impact
 *     point. If hit a block (ground), additionally spawn 3 fangs at EVERY
 *     enemy within 25 blocks (massive AoE).
 *   - Melee (left-click entity): spawn 3 waves of 3-fang paths in front of
 *     player (3 + 3 + 3 = 9 fangs total).
 *   - Cooldown on melee: 1.5 sec.
 *   - All fangs have warmup=0 → instant damage, no delay.
 */
public final class ThreeFangsTrident {

    private ThreeFangsTrident() {}

    public static final String NBT_KEY = "ThreeFangs";
    public static final double THROW_AOE_RADIUS = 25.0;
    private static final java.util.Map<java.util.UUID, Long> meleeCooldownUntil =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final long MELEE_COOLDOWN_TICKS = 30L; // 1.5 sec

    public static ItemStack create() {
        ItemStack stack = new ItemStack(Items.TRIDENT);
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean(NBT_KEY, true);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("Три Челюсти")
                        .formatted(Formatting.DARK_PURPLE, Formatting.BOLD));
        stack.set(DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(List.of(
                Text.literal("Легендарное оружие").formatted(Formatting.YELLOW),
                Text.literal("Возвращается при броске").formatted(Formatting.GRAY),
                Text.literal("Бросок: 3 челюсти + AoE по врагам в 25 бл.").formatted(Formatting.GRAY),
                Text.literal("Удар: 3 волны по 3 челюсти").formatted(Formatting.GRAY)
        )));
        stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    public static void ensureLoyaltyEnchantment(ServerWorld world, ItemStack stack) {
        if (!isThreeFangs(stack)) return;
        var enchants = stack.get(DataComponentTypes.ENCHANTMENTS);
        if (enchants != null && !enchants.isEmpty()) return;

        Registry<Enchantment> enchRegistry = world.getRegistryManager()
                .getOrThrow(RegistryKeys.ENCHANTMENT);
        RegistryEntry<Enchantment> loyalty = enchRegistry.getOrThrow(
                net.minecraft.enchantment.Enchantments.LOYALTY);
        EnchantmentHelper.apply(stack, builder -> builder.add(loyalty, 3));
    }

    public static boolean isThreeFangs(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!stack.isOf(Items.TRIDENT)) return false;
        NbtComponent cd = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (cd == null || cd.isEmpty()) return false;
        return cd.copyNbt().getBoolean(NBT_KEY, false);
    }

    /**
     * Spawn 3 Evoker Fangs at the given position (used on trident throw hit).
     * Warmup=0 → instant damage, no delay.
     */
    public static void spawnFangsOnThrowHit(ServerWorld world, double x, double y, double z,
                                            Entity owner) {
        for (int i = 0; i < 3; i++) {
            double ox = (i == 0) ? -0.5 : (i == 1) ? 0.5 : 0;
            double oz = (i == 0) ? -0.3 : (i == 1) ? -0.3 : 0.6;
            LivingEntity livingOwner =
                    (owner instanceof LivingEntity) ? (LivingEntity) owner : null;
            // warmup=0 → instant damage (was 20 = 1 second delay)
            EvokerFangsEntity fangs = new EvokerFangsEntity(
                    world, x + ox, y, z + oz, 0f, 0, livingOwner);
            world.spawnEntity(fangs);
        }
        world.playSound(null, x, y, z,
                SoundEvents.ENTITY_EVOKER_FANGS_ATTACK,
                net.minecraft.sound.SoundCategory.HOSTILE, 1.0f, 1.0f);
    }

    /**
     * Spawn 3 Evoker Fangs at the given position (instant, warmup=0).
     * Used by the AoE effect on ground hit.
     */
    public static void spawnFangsCluster(ServerWorld world, double x, double y, double z,
                                         Entity owner) {
        for (int i = 0; i < 3; i++) {
            double ox = (i == 0) ? -0.5 : (i == 1) ? 0.5 : 0;
            double oz = (i == 0) ? -0.3 : (i == 1) ? -0.3 : 0.6;
            LivingEntity livingOwner =
                    (owner instanceof LivingEntity) ? (LivingEntity) owner : null;
            EvokerFangsEntity fangs = new EvokerFangsEntity(
                    world, x + ox, y, z + oz, 0f, 0, livingOwner);
            world.spawnEntity(fangs);
        }
    }

    /**
     * Melee attack — spawn 3 waves of 3-fang paths in front of the player.
     * Each wave is a 3-fang path going forward. Total = 9 fangs.
     * Respects a 1.5 second cooldown per player.
     */
    public static void spawnFangPath(ServerWorld world, net.minecraft.entity.player.PlayerEntity player) {
        long now = world.getServer().getTicks();
        Long until = meleeCooldownUntil.get(player.getUuid());
        if (until != null && now < until) return;
        meleeCooldownUntil.put(player.getUuid(), now + MELEE_COOLDOWN_TICKS);

        Vec3d look = player.getRotationVec(1.0f);
        double yaw = Math.toDegrees(Math.atan2(-look.x, look.z));

        // 3 waves of 3 fangs each:
        //   Wave 1: forward 1, 2, 3 blocks
        //   Wave 2: forward 2, 3, 4 blocks (slightly farther)
        //   Wave 3: forward 3, 4, 5 blocks (farthest)
        for (int wave = 0; wave < 3; wave++) {
            for (int i = 1; i <= 3; i++) {
                double distance = wave + i; // 1,2,3 → 2,3,4 → 3,4,5
                double fx = player.getX() + look.x * distance;
                double fz = player.getZ() + look.z * distance;
                EvokerFangsEntity fangs = new EvokerFangsEntity(
                        world, fx, player.getY(), fz, (float) yaw, 0, player);
                world.spawnEntity(fangs);
            }
        }

        player.playSound(SoundEvents.ENTITY_EVOKER_FANGS_ATTACK, 1.5f, 0.8f);
    }

    /**
     * Tick handler — scans for in-flight Three Fangs tridents.
     * For each one:
     *   1. Lazily applies Loyalty III if not yet applied.
     *   2. When trident lands (isInGround = true), spawn 3 fangs at impact point.
     *      Additionally, if the trident hit a BLOCK (ground), spawn 3 fangs at
     *      every enemy within 25 blocks (massive AoE).
     */
    public static void tick(ServerWorld world) {
        for (TridentEntity trident : world.getEntitiesByType(
                net.minecraft.entity.EntityType.TRIDENT, e -> e.isAlive())) {
            PersistentProjectileEntityInvoker accessor = (PersistentProjectileEntityInvoker) trident;
            ItemStack stack = accessor.settlementdestroyer$asItemStack();
            if (!isThreeFangs(stack)) continue;

            ensureLoyaltyEnchantment(world, stack);

            if (ALREADY_FIRED.contains(trident.getUuid())) continue;

            boolean inGround = accessor.settlementdestroyer$isInGround();
            if (inGround) {
                // Ground impact → spawn 3 fangs at impact point
                double ix = trident.getX();
                double iy = trident.getY();
                double iz = trident.getZ();
                spawnFangsOnThrowHit(world, ix, iy, iz, trident.getOwner());

                // AoE: spawn 3 fangs at every LivingEntity within 25 blocks
                // (excluding the owner). This makes the trident a "nuke" on
                // ground impact — clearing crowds of mobs.
                //
                // /dtr mode check: skip players in AoE if DTR is OFF.
                Entity owner = trident.getOwner();
                boolean hitsPlayers = DtrCommand.tridentHitsPlayers();
                net.minecraft.util.math.Box aoeBox = new net.minecraft.util.math.Box(
                        ix - THROW_AOE_RADIUS, iy - 5, iz - THROW_AOE_RADIUS,
                        ix + THROW_AOE_RADIUS, iy + 5, iz + THROW_AOE_RADIUS);
                for (Entity e : world.getOtherEntities(owner, aoeBox)) {
                    if (e instanceof LivingEntity && e.isAlive()) {
                        // Skip players in AoE if DTR is OFF
                        if (e instanceof net.minecraft.entity.player.PlayerEntity && !hitsPlayers) {
                            continue;
                        }
                        double dx = e.getX() - ix;
                        double dz = e.getZ() - iz;
                        if (dx * dx + dz * dz <= THROW_AOE_RADIUS * THROW_AOE_RADIUS) {
                            spawnFangsCluster(world, e.getX(), e.getY(), e.getZ(), owner);
                        }
                    }
                }

                ALREADY_FIRED.add(trident.getUuid());
            }
        }
    }

    private static final java.util.Set<java.util.UUID> ALREADY_FIRED =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    public static boolean hasFired(java.util.UUID uuid) {
        return ALREADY_FIRED.contains(uuid);
    }

    public static void markFired(java.util.UUID uuid) {
        ALREADY_FIRED.add(uuid);
    }
}
