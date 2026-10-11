package com.leomordmod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.minecraft.server.command.CommandManager.literal;
import net.minecraft.server.command.CommandManager;

/**
 * Leomord mod entrypoint.
 *
 * Registers:
 *   - LeomordEntity (custom EntityType, extends WitherSkeletonEntity)
 *   - LeomordHorseEntity (custom EntityType, extends SkeletonHorseEntity)
 *
 * Event handlers:
 *   - UseItemCallback — LeomordSummonItem (summon boss) + LeomordLootBag (open)
 *   - ServerTickEvents.END_SERVER_TICK — LeomordProjectiles.tick() (arrow scanner)
 *
 * Commands:
 *   - /summon leomord
 *   - /leomordgive summon|spear|lootbag|all
 */
public class LeomordMod implements ModInitializer {

    public static final String MOD_ID = "leomordmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Leomord boss entity — extends WitherSkeletonEntity, disguised as WITHER_SKELETON. */
    public static final EntityType<LeomordEntity> LEOMORD = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of(MOD_ID, "leomord"),
            EntityType.Builder.create(LeomordEntity::new, SpawnGroup.MONSTER)
                    .dimensions(0.7f, 2.4f) // standard wither skeleton size
                    .build(RegistryKey.of(RegistryKeys.ENTITY_TYPE,
                            Identifier.of(MOD_ID, "leomord"))));

    /** Leomord Horse — phase 1 mount (SkeletonHorse with custom Fox Jump). */
    public static final EntityType<LeomordHorseEntity> LEOMORD_HORSE = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of(MOD_ID, "leomord_horse"),
            EntityType.Builder.create(LeomordHorseEntity::new, SpawnGroup.CREATURE)
                    .dimensions(1.3964844f, 1.6f) // standard skeleton horse size
                    .build(RegistryKey.of(RegistryKeys.ENTITY_TYPE,
                            Identifier.of(MOD_ID, "leomord_horse"))));

    @Override
    public void onInitialize() {
        // === Register attributes ===
        net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry
                .register(LEOMORD, LeomordEntity.createLeomordAttributes());
        net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry
                .register(LEOMORD_HORSE, LeomordHorseEntity.createLeomordHorseAttributes());

        // === Summon item — right-click on block to spawn boss + horse ===
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;

            ItemStack stack = player.getStackInHand(hand);
            if (!LeomordSummonItem.isSummonItem(stack)) return ActionResult.PASS;

            // Must right-click on a block (raycast)
            var hitResult = player.raycast(5.0, 0.0f, false);
            if (!(hitResult instanceof BlockHitResult)) {
                return ActionResult.PASS;
            }

            ServerWorld serverWorld = (ServerWorld) world;
            LeomordEntity boss = LEOMORD.create(serverWorld, SpawnReason.TRIGGERED);
            if (boss == null) return ActionResult.FAIL;

            // Spawn boss a few blocks in front of the player
            float yaw = player.getYaw();
            double dx = -Math.sin(Math.toRadians(yaw)) * 3.0;
            double dz = Math.cos(Math.toRadians(yaw)) * 3.0;
            boss.refreshPositionAndAngles(
                    player.getX() + dx,
                    player.getY(),
                    player.getZ() + dz,
                    player.getYaw() + 180, 0);
            serverWorld.spawnEntity(boss);
            // Apply equipment (Crown + Netherite + Vex trim + armor nerfs)
            // AND spawn the phase-1 horse and start riding
            boss.onSummoned(serverWorld);

            // Visual effect — lightning bolt (cosmetic)
            net.minecraft.entity.LightningEntity bolt =
                    net.minecraft.entity.EntityType.LIGHTNING_BOLT.create(
                            serverWorld, SpawnReason.TRIGGERED);
            if (bolt != null) {
                bolt.refreshPositionAndAngles(
                        player.getX() + dx, player.getY(), player.getZ() + dz, 0, 0);
                bolt.setCosmetic(true);
                serverWorld.spawnEntity(bolt);
            }

            stack.decrement(1);

            // Announce to ALL players
            Text announce = Text.literal("§4§lЛеоморд пробуждён! Вождь Падших скачет!")
                    .formatted(Formatting.DARK_RED);
            serverWorld.getServer().getPlayerManager().broadcast(announce, false);

            return ActionResult.SUCCESS;
        });

        // === Loot Bag — right-click to open ===
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;

            ItemStack stack = player.getStackInHand(hand);
            if (!LeomordLootBag.isLootBag(stack)) return ActionResult.PASS;

            LeomordLootBag.open(stack, player);
            return ActionResult.CONSUME; // Prevent vanilla Bundle UI
        });

        // === /summon leomord admin command ===
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("summon")
                    .then(literal("leomord")
                            .requires(CommandManager.requirePermissionLevel(
                                    CommandManager.GAMEMASTERS_CHECK))
                            .executes(ctx -> summonBoss(ctx.getSource()))
                    )
            );
        });

        // === /leomordgive admin command ===
        LeomordGiveCommand.register();

        // === Item behavior handlers (LiftSword, ExplosiveSpear, CarrionCrossbow
        //     combo, FireSymbol fireball ability) ===
        LeomordItemBehaviors.register();

        // === Register FireSymbol as a hellimp accessory (if hellimp is loaded) ===
        try {
            if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("hellimp")) {
                com.hellimp.AccessoriesSystem.registerAccessory(FireSymbol.NBT_KEY);
                LOGGER.info("[leomordmod] FireSymbol registered as hellimp accessory");
            } else {
                LOGGER.info("[leomordmod] hellimp not loaded — FireSymbol won't have accessory slot");
            }
        } catch (Throwable t) {
            LOGGER.warn("[leomordmod] Failed to register FireSymbol accessory: {}", t.toString());
        }

        // === Tick handler — scan for tagged arrows (Get Over Here + LeomordArrow)
        //     + dripstone removal scheduling + stun timers + projectile tagger ===
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerWorld world : server.getWorlds()) {
                LeomordProjectiles.tick(world);
                DripstoneRemoval.tick(world);
                LeomordProjectileTagger.tick(world);  // tag player-fired projectiles from Leomord items
            }
        });

        LOGGER.info("[leomordmod] Leomord boss registered — WitherSkeleton base, 3 phases, fire-immune, Vex-trimmed armor");
    }

    private int summonBoss(ServerCommandSource source) {
        ServerWorld world = source.getWorld();
        LeomordEntity boss = LEOMORD.create(world, SpawnReason.COMMAND);
        if (boss == null) {
            source.sendError(Text.literal("Failed to create Leomord"));
            return 0;
        }
        boss.refreshPositionAndAngles(
                source.getPosition().x, source.getPosition().y,
                source.getPosition().z, 0, 0);
        world.spawnEntity(boss);
        boss.onSummoned(world);
        source.sendFeedback(() -> Text.literal("Leomord summoned!")
                .formatted(Formatting.DARK_RED), false);
        return 1;
    }
}
