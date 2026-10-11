package com.vulcadavmod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.minecraft.server.command.CommandManager.literal;
import net.minecraft.server.command.CommandManager;

/**
 * Vulcadav mod — boss fight with 3 phases, loot bags, wolf berry.
 *
 * All serverside-only. Vulcadav is a custom FoxEntity disguised as WOLF
 * via createSpawnPacket override. Boss bar via ServerBossBar.
 */
public class VulcadavMod implements ModInitializer {

    public static final String MOD_ID = "vulcadavmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Vulcadav entity type — extends FoxEntity, disguised as WOLF. */
    public static final EntityType<VulcadavEntity> VULCADAV = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of(MOD_ID, "vulcadav"),
            EntityType.Builder.create(VulcadavEntity::new, SpawnGroup.CREATURE)
                    .dimensions(0.6f, 0.85f) // standard wolf size
                    .build(RegistryKey.of(RegistryKeys.ENTITY_TYPE,
                            Identifier.of(MOD_ID, "vulcadav"))));

    @Override
    public void onInitialize() {
        // Register attributes
        net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(VULCADAV, VulcadavEntity.createVulcadavAttributes());

        // Wolf Box — right-click to summon Vulcadav + give Wolf Compass
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;

            ItemStack stack = player.getStackInHand(hand);
            if (!WolfBox.isWolfBox(stack)) return ActionResult.PASS;

            // Summon Vulcadav
            ServerWorld serverWorld = (ServerWorld) world;
            VulcadavEntity boss = VULCADAV.create(serverWorld, net.minecraft.entity.SpawnReason.TRIGGERED);
            if (boss == null) return ActionResult.FAIL;

            boss.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(),
                    player.getYaw(), 0);
            serverWorld.spawnEntity(boss);

            stack.decrement(1);

            // Give Wolf Compass to the player who activated the box
            ItemStack compass = WolfCompass.create();
            if (!player.getInventory().insertStack(compass)) {
                player.dropItem(compass, false);
            }

            // Announce to ALL players
            Text announce = Text.literal("§c§lВулкадав пробудился! Охота началась!")
                    .formatted(Formatting.RED);
            serverWorld.getServer().getPlayerManager().broadcast(announce, false);

            player.sendMessage(Text.literal("§cВы получили Волчий Компас — он укажет путь к боссу")
                    .formatted(Formatting.RED), false);
            return ActionResult.SUCCESS;
        });

        // Loot Bag — right-click to open
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;

            ItemStack stack = player.getStackInHand(hand);
            if (!LootBag.isLootBag(stack)) return ActionResult.PASS;

            LootBag.open(stack, player);
            return ActionResult.CONSUME; // Prevent vanilla Bundle.use() from also running
        });

        // /summon vulcadav command
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("summon")
                    .then(literal("vulcadav")
                            .requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
                            .executes(ctx -> summonVulcadav(ctx.getSource()))
                    )
            );
        });

        LOGGER.info("[vulcadavmod] Vulcadav boss registered — FoxEntity base, WOLF disguise, graves enabled");

        // Register Wolf Bow handler (arrow hit → spawn wolves)
        WolfBowHandler.register();

        // Register Wolf Rod handler (bobber → mark → hit → prison)
        WolfRodHandler.register();

        // Register /vulcgive admin command
        VulcGiveCommand.register();

        // Tick handlers for wolf cleanup, prison removal, compass update
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerWorld world : server.getWorlds()) {
                WolfBowHandler.tick(world);
                WolfRodHandler.tick(world);

                // Update Wolf Compass — find Vulcadav and update compasses
                if (server.getTicks() % 100 == 0) { // every 5 seconds
                    var vulcadavs = world.getEntitiesByType(VULCADAV, e -> e.isAlive());
                    if (!vulcadavs.isEmpty()) {
                        WolfCompass.updateAll(world, vulcadavs.get(0));
                    } else {
                        // No Vulcadav alive — remove all compasses
                    }
                }
            }
        });
    }

    private int summonVulcadav(ServerCommandSource source) {
        ServerWorld world = source.getWorld();
        VulcadavEntity boss = VULCADAV.create(world, net.minecraft.entity.SpawnReason.COMMAND);
        if (boss == null) {
            source.sendError(Text.literal("Failed to create Vulcadav"));
            return 0;
        }
        boss.refreshPositionAndAngles(source.getPosition().x, source.getPosition().y,
                source.getPosition().z, 0, 0);
        world.spawnEntity(boss);
        source.sendFeedback(() -> Text.literal("Vulcadav summoned!").formatted(Formatting.RED), false);
        return 1;
    }
}
