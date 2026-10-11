package com.larrymod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.minecraft.server.command.CommandManager.literal;
import net.minecraft.server.command.CommandManager;

/**
 * Larry mod — custom hostile mob with new EntityType.
 *
 * LarryEntity is registered as "larrymod:larry" in the entity type registry.
 * A mixin on EntitySpawnS2CPacket replaces the type with minecraft:skeleton
 * for client rendering — no client mod required, works on Bedrock via Geyser.
 *
 * Natural spawning is added via BiomeModifications — Larry spawns on the
 * surface in any biome, day or night, with low weight (like chicken jockey).
 */
public class LarryMod implements ModInitializer {

    public static final String MOD_ID = "larrymod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final int MAX_GROUP_SIZE = 8;

    /** The Larry entity type — registered in the vanilla entity type registry. */
    public static final EntityType<LarryEntity> LARRY = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of(MOD_ID, "larry"),
            EntityType.Builder.create(LarryEntity::new, SpawnGroup.MONSTER)
                    .dimensions(0.6f, 1.99f)
                    .eyeHeight(1.74f)
                    .build(RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(MOD_ID, "larry"))));

    @Override
    public void onInitialize() {
        // Register attributes
        FabricDefaultAttributeRegistry.register(LARRY, LarryEntity.createLarryAttributes());

        // Register natural spawning — surface, day and night, low weight
        BiomeModifications.create(Identifier.of(MOD_ID, "spawns"))
                .add(net.fabricmc.fabric.api.biome.v1.ModificationPhase.ADDITIONS,
                        BiomeSelectors.all(),
                        ctx -> ctx.getSpawnSettings().addSpawn(
                                SpawnGroup.MONSTER,
                                new net.minecraft.world.biome.SpawnSettings.SpawnEntry(
                                        LARRY, 1, 1),
                                1));

        // /summon larry command
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("summon")
                    .then(literal("larry")
                            .requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
                            .executes(ctx -> summonLarry(ctx.getSource()))
                    )
            );
            // /larrybone — gives the legendary weapon
            dispatcher.register(literal("larrybone")
                    .requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
                    .executes(ctx -> giveLarryBone(ctx.getSource()))
            );
        });

        LOGGER.info("[larrymod] Larry entity registered — new EntityType, skeleton disguise via protocol mixin");

        // Register combo handler for Larry's Sharpened Bone
        LarryBoneComboHandler.register();
    }

    private int summonLarry(ServerCommandSource source) {
        ServerWorld world = source.getWorld();
        LarryEntity larry = LARRY.create(world, net.minecraft.entity.SpawnReason.COMMAND);
        if (larry == null) {
            source.sendError(Text.literal("Failed to create Larry"));
            return 0;
        }

        // Find safe spawn position — scan upward for first air block with solid ground below
        net.minecraft.util.math.Vec3d pos = source.getPosition();
        int x = (int) Math.floor(pos.x);
        int z = (int) Math.floor(pos.z);
        int y = (int) Math.floor(pos.y);

        // Scan from player's Y upward for a safe spot
        for (int dy = 0; dy <= 5; dy++) {
            int checkY = y + dy;
            var ground = world.getBlockState(new net.minecraft.util.math.BlockPos(x, checkY - 1, z));
            var feet = world.getBlockState(new net.minecraft.util.math.BlockPos(x, checkY, z));
            var head = world.getBlockState(new net.minecraft.util.math.BlockPos(x, checkY + 1, z));
            if (!ground.isAir() && feet.isAir() && head.isAir()) {
                y = checkY;
                break;
            }
        }

        larry.refreshPositionAndAngles(x + 0.5, y, z + 0.5, 0, 0);
        larry.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, new ItemStack(net.minecraft.item.Items.STONE_SWORD));
        larry.setCustomName(Text.literal("Ларри").formatted(Formatting.WHITE));
        larry.setCustomNameVisible(true);
        larry.setPersistent();

        world.spawnEntity(larry);

        source.sendFeedback(() -> Text.literal("Larry spawned!").formatted(Formatting.GREEN), false);
        return 1;
    }

    private int giveLarryBone(ServerCommandSource source) {
        if (source.getPlayer() == null) {
            source.sendError(Text.literal("Command must be run by a player"));
            return 0;
        }
        ItemStack bone = LarryBone.create();
        if (!source.getPlayer().getInventory().insertStack(bone)) {
            source.getPlayer().dropItem(bone, false);
        }
        source.sendFeedback(() -> Text.literal("Точенная кость Ларри выдана!").formatted(Formatting.GOLD), false);
        return 1;
    }
}
