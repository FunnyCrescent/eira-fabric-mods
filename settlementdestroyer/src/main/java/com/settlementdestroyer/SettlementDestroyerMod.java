package com.settlementdestroyer;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.mob.RavagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.entity.SpawnReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.minecraft.server.command.CommandManager.literal;
import net.minecraft.server.command.CommandManager;

/**
 * Settlement Destroyer mod — 2-phase Ravager boss with fang attacks and dash.
 *
 * All serverside-only. Boss is a custom RavagerEntity (no disguise — vanilla
 * Ravager is used so Bedrock clients see a Ravager via Geyser). Custom name +
 * BossBar + 200 HP. Custom items use vanilla items with NBT markers so
 * vanilla/Bedrock clients can see and use them.
 *
 * Author: Eira
 */
public class SettlementDestroyerMod implements ModInitializer {

    public static final String MOD_ID = "settlementdestroyer";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Settlement Destroyer entity type — extends RavagerEntity. */
    public static final EntityType<SettlementDestroyerEntity> SETTLEMENT_DESTROYER = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of(MOD_ID, "settlement_destroyer"),
            EntityType.Builder.create(SettlementDestroyerEntity::new, SpawnGroup.CREATURE)
                    .dimensions(1.95f, 2.2f) // standard Ravager size
                    .build(RegistryKey.of(RegistryKeys.ENTITY_TYPE,
                            Identifier.of(MOD_ID, "settlement_destroyer"))));

    @Override
    public void onInitialize() {
        // Register attributes for the custom Ravager
        net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(
                SETTLEMENT_DESTROYER, SettlementDestroyerEntity.createSettlementDestroyerAttributes());

        // === Summon item — right-click on block to spawn boss ===
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;

            ItemStack stack = player.getStackInHand(hand);
            if (!DestroyerSummonItem.isSummonItem(stack)) return ActionResult.PASS;

            // Only works when right-clicking a block (raycast)
            var hitResult = player.raycast(5.0, 0.0f, false);
            if (!(hitResult instanceof net.minecraft.util.hit.BlockHitResult)) {
                return ActionResult.PASS;
            }

            ServerWorld serverWorld = (ServerWorld) world;
            SettlementDestroyerEntity boss = SETTLEMENT_DESTROYER.create(
                    serverWorld, SpawnReason.TRIGGERED);
            if (boss == null) return ActionResult.FAIL;

            // Spawn boss a few blocks in front of the player so it doesn't
            // immediately trample them.
            float yaw = player.getYaw();
            double dx = -Math.sin(Math.toRadians(yaw)) * 3.0;
            double dz = Math.cos(Math.toRadians(yaw)) * 3.0;
            boss.refreshPositionAndAngles(
                    player.getX() + dx,
                    player.getY(),
                    player.getZ() + dz,
                    player.getYaw() + 180, 0);
            serverWorld.spawnEntity(boss);

            // Visual effect — lightning bolt (cosmetic, no damage)
            net.minecraft.entity.LightningEntity bolt =
                    net.minecraft.entity.EntityType.LIGHTNING_BOLT.create(
                            serverWorld, SpawnReason.TRIGGERED);
            if (bolt != null) {
                bolt.refreshPositionAndAngles(
                        player.getX() + dx, player.getY(), player.getZ() + dz, 0, 0);
                // Make it visual-only (no fire, no block damage)
                bolt.setCosmetic(true);
                serverWorld.spawnEntity(bolt);
            }

            stack.decrement(1);

            // Announce to ALL players
            Text announce = Text.literal("§4§lРазрушитель Поселений пробудился!")
                    .formatted(Formatting.DARK_RED);
            serverWorld.getServer().getPlayerManager().broadcast(announce, false);

            return ActionResult.SUCCESS;
        });

        // === Loot Bag — right-click to open ===
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;

            ItemStack stack = player.getStackInHand(hand);
            if (!LootBag.isLootBag(stack)) return ActionResult.PASS;

            LootBag.open(stack, player);
            return ActionResult.CONSUME;
        });

        // === Three Fangs — melee attack spawns fang path ===
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;

            ItemStack stack = player.getStackInHand(hand);
            if (!ThreeFangsTrident.isThreeFangs(stack)) return ActionResult.PASS;

            // Spawn 5-block fang path in front of player
            ThreeFangsTrident.spawnFangPath((ServerWorld) world, player);
            return ActionResult.PASS;
        });

        // === /summon settlement_destroyer admin command ===
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("summon")
                    .then(literal("settlement_destroyer")
                            .requires(CommandManager.requirePermissionLevel(
                                    CommandManager.GAMEMASTERS_CHECK))
                            .executes(ctx -> summonBoss(ctx.getSource()))
                    )
            );
        });

        // === /destroyergive admin command ===
        DestroyerGiveCommand.register();

        // === /dtr — toggle trident PvP mode ===
        DtrCommand.register();

        // === Three Fangs throw handler (tick-based, sees trident projectiles) ===
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerWorld world : server.getWorlds()) {
                ThreeFangsTrident.tick(world);
            }
        });

        LOGGER.info("[settlementdestroyer] Boss registered — Ravager base, 200 HP, 2 phases");
    }

    private int summonBoss(ServerCommandSource source) {
        ServerWorld world = source.getWorld();
        SettlementDestroyerEntity boss = SETTLEMENT_DESTROYER.create(
                world, SpawnReason.COMMAND);
        if (boss == null) {
            source.sendError(Text.literal("Failed to create Settlement Destroyer"));
            return 0;
        }
        boss.refreshPositionAndAngles(
                source.getPosition().x, source.getPosition().y,
                source.getPosition().z, 0, 0);
        world.spawnEntity(boss);
        source.sendFeedback(() -> Text.literal("Settlement Destroyer summoned!")
                .formatted(Formatting.DARK_RED), false);
        return 1;
    }
}
