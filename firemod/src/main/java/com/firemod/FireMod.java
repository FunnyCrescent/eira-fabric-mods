package com.firemod;

import net.fabricmc.api.ModInitializer;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.FireBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Serverside-only mod that enhances fire behavior:
 *
 *  1) Fire spreads at vanilla speed (not faster) — we don't accelerate it.
 *     But we clean up floating wood/leaves so trees burn completely without
 *     leaving charred remnants in the air.
 *
 *  2) Additional flammable blocks: vanilla doesn't register trapdoors, chests,
 *     beds, fences, etc. as flammable. We register them so fire can spread
 *     to and destroy them.
 *
 *  3) Burning entities (including flaming arrows/snowballs) ignite blocks
 *     around them — implemented in EntityFireMixin.
 *
 * Burn chances (higher = burns faster):
 *   - Logs/Planks/Wood: 5 (same as vanilla logs)
 *   - Leaves: 30 (same as vanilla leaves)
 *   - Fences, fence gates: 5
 *   - Wooden doors, trapdoors: 5
 *   - Chests, barrels, bookshelves: 5
 *   - Beds: 20 (very flammable)
 *   - Banners, signs: 30
 *   - Crafting tables, jukeboxes, note blocks: 5
 *   - Bamboo, scaffolding: 20
 *   - Beehives, mushroom blocks: 5
 *
 * NOT burned (stays safe):
 *   - Iron doors, iron trapdoors
 *   - Ender chests (obsidian-based)
 *   - Shulker boxes
 *   - Copper/iron/gold/diamond/netherite blocks
 */
public class FireMod implements ModInitializer {

    public static final String MOD_ID = "firemod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        registerFlammables();
        LOGGER.info("[firemod] Enhanced fire: extra flammable blocks registered, floating wood cleanup active");
    }

    private void registerFlammables() {
        FireBlock fireBlock = (FireBlock) Blocks.FIRE;

        // Spread chance, burn chance — higher = more flammable
        // Vanilla: logs=5/5, planks=5/20, leaves=30/60, wool=30/60

        // Fences and walls
        register(fireBlock, Blocks.OAK_FENCE, 5, 20);
        register(fireBlock, Blocks.SPRUCE_FENCE, 5, 20);
        register(fireBlock, Blocks.BIRCH_FENCE, 5, 20);
        register(fireBlock, Blocks.JUNGLE_FENCE, 5, 20);
        register(fireBlock, Blocks.ACACIA_FENCE, 5, 20);
        register(fireBlock, Blocks.DARK_OAK_FENCE, 5, 20);
        register(fireBlock, Blocks.CHERRY_FENCE, 5, 20);
        register(fireBlock, Blocks.MANGROVE_FENCE, 5, 20);
        register(fireBlock, Blocks.BAMBOO_FENCE, 5, 20);
        register(fireBlock, Blocks.CRIMSON_FENCE, 5, 20);
        register(fireBlock, Blocks.WARPED_FENCE, 5, 20);

        // Fence gates
        registerAll(fireBlock, 5, 20,
                Blocks.OAK_FENCE_GATE, Blocks.SPRUCE_FENCE_GATE, Blocks.BIRCH_FENCE_GATE,
                Blocks.JUNGLE_FENCE_GATE, Blocks.ACACIA_FENCE_GATE, Blocks.DARK_OAK_FENCE_GATE,
                Blocks.CHERRY_FENCE_GATE, Blocks.MANGROVE_FENCE_GATE, Blocks.BAMBOO_FENCE_GATE,
                Blocks.CRIMSON_FENCE_GATE, Blocks.WARPED_FENCE_GATE);

        // Wooden doors
        registerAll(fireBlock, 5, 20,
                Blocks.OAK_DOOR, Blocks.SPRUCE_DOOR, Blocks.BIRCH_DOOR,
                Blocks.JUNGLE_DOOR, Blocks.ACACIA_DOOR, Blocks.DARK_OAK_DOOR,
                Blocks.CHERRY_DOOR, Blocks.MANGROVE_DOOR, Blocks.BAMBOO_DOOR,
                Blocks.CRIMSON_DOOR, Blocks.WARPED_DOOR);

        // Wooden trapdoors
        registerAll(fireBlock, 5, 20,
                Blocks.OAK_TRAPDOOR, Blocks.SPRUCE_TRAPDOOR, Blocks.BIRCH_TRAPDOOR,
                Blocks.JUNGLE_TRAPDOOR, Blocks.ACACIA_TRAPDOOR, Blocks.DARK_OAK_TRAPDOOR,
                Blocks.CHERRY_TRAPDOOR, Blocks.MANGROVE_TRAPDOOR, Blocks.BAMBOO_TRAPDOOR,
                Blocks.CRIMSON_TRAPDOOR, Blocks.WARPED_TRAPDOOR);

        // Wooden buttons
        registerAll(fireBlock, 5, 20,
                Blocks.OAK_BUTTON, Blocks.SPRUCE_BUTTON, Blocks.BIRCH_BUTTON,
                Blocks.JUNGLE_BUTTON, Blocks.ACACIA_BUTTON, Blocks.DARK_OAK_BUTTON,
                Blocks.CHERRY_BUTTON, Blocks.MANGROVE_BUTTON, Blocks.BAMBOO_BUTTON,
                Blocks.CRIMSON_BUTTON, Blocks.WARPED_BUTTON);

        // Wooden pressure plates
        registerAll(fireBlock, 5, 20,
                Blocks.OAK_PRESSURE_PLATE, Blocks.SPRUCE_PRESSURE_PLATE, Blocks.BIRCH_PRESSURE_PLATE,
                Blocks.JUNGLE_PRESSURE_PLATE, Blocks.ACACIA_PRESSURE_PLATE, Blocks.DARK_OAK_PRESSURE_PLATE,
                Blocks.CHERRY_PRESSURE_PLATE, Blocks.MANGROVE_PRESSURE_PLATE, Blocks.BAMBOO_PRESSURE_PLATE,
                Blocks.CRIMSON_PRESSURE_PLATE, Blocks.WARPED_PRESSURE_PLATE);

        // Wooden slabs
        registerAll(fireBlock, 5, 20,
                Blocks.OAK_SLAB, Blocks.SPRUCE_SLAB, Blocks.BIRCH_SLAB,
                Blocks.JUNGLE_SLAB, Blocks.ACACIA_SLAB, Blocks.DARK_OAK_SLAB,
                Blocks.CHERRY_SLAB, Blocks.MANGROVE_SLAB, Blocks.BAMBOO_SLAB,
                Blocks.CRIMSON_SLAB, Blocks.WARPED_SLAB);

        // Wooden stairs
        registerAll(fireBlock, 5, 20,
                Blocks.OAK_STAIRS, Blocks.SPRUCE_STAIRS, Blocks.BIRCH_STAIRS,
                Blocks.JUNGLE_STAIRS, Blocks.ACACIA_STAIRS, Blocks.DARK_OAK_STAIRS,
                Blocks.CHERRY_STAIRS, Blocks.MANGROVE_STAIRS, Blocks.BAMBOO_STAIRS,
                Blocks.CRIMSON_STAIRS, Blocks.WARPED_STAIRS);

        // Chests and barrels
        register(fireBlock, Blocks.CHEST, 5, 20);
        register(fireBlock, Blocks.TRAPPED_CHEST, 5, 20);
        register(fireBlock, Blocks.BARREL, 5, 20);

        // Bookshelves and lecterns
        register(fireBlock, Blocks.BOOKSHELF, 5, 20);
        register(fireBlock, Blocks.LECTERN, 5, 20);
        register(fireBlock, Blocks.CHISELED_BOOKSHELF, 5, 20);

        // Crafting tables, jukeboxes, note blocks
        register(fireBlock, Blocks.CRAFTING_TABLE, 5, 20);
        register(fireBlock, Blocks.JUKEBOX, 5, 20);
        register(fireBlock, Blocks.NOTE_BLOCK, 5, 20);

        // Beds (very flammable)
        registerAll(fireBlock, 20, 60,
                Blocks.WHITE_BED, Blocks.ORANGE_BED, Blocks.MAGENTA_BED, Blocks.LIGHT_BLUE_BED,
                Blocks.YELLOW_BED, Blocks.LIME_BED, Blocks.PINK_BED, Blocks.GRAY_BED,
                Blocks.LIGHT_GRAY_BED, Blocks.CYAN_BED, Blocks.PURPLE_BED, Blocks.BLUE_BED,
                Blocks.BROWN_BED, Blocks.GREEN_BED, Blocks.RED_BED, Blocks.BLACK_BED);

        // Banners (very flammable)
        registerAll(fireBlock, 30, 60,
                Blocks.WHITE_BANNER, Blocks.ORANGE_BANNER, Blocks.MAGENTA_BANNER, Blocks.LIGHT_BLUE_BANNER,
                Blocks.YELLOW_BANNER, Blocks.LIME_BANNER, Blocks.PINK_BANNER, Blocks.GRAY_BANNER,
                Blocks.LIGHT_GRAY_BANNER, Blocks.CYAN_BANNER, Blocks.PURPLE_BANNER, Blocks.BLUE_BANNER,
                Blocks.BROWN_BANNER, Blocks.GREEN_BANNER, Blocks.RED_BANNER, Blocks.BLACK_BANNER);

        // Standing and wall signs
        registerAll(fireBlock, 30, 60,
                Blocks.OAK_SIGN, Blocks.SPRUCE_SIGN, Blocks.BIRCH_SIGN,
                Blocks.JUNGLE_SIGN, Blocks.ACACIA_SIGN, Blocks.DARK_OAK_SIGN,
                Blocks.CHERRY_SIGN, Blocks.MANGROVE_SIGN, Blocks.BAMBOO_SIGN,
                Blocks.CRIMSON_SIGN, Blocks.WARPED_SIGN,
                Blocks.OAK_WALL_SIGN, Blocks.SPRUCE_WALL_SIGN, Blocks.BIRCH_WALL_SIGN,
                Blocks.JUNGLE_WALL_SIGN, Blocks.ACACIA_WALL_SIGN, Blocks.DARK_OAK_WALL_SIGN,
                Blocks.CHERRY_WALL_SIGN, Blocks.MANGROVE_WALL_SIGN, Blocks.BAMBOO_WALL_SIGN,
                Blocks.CRIMSON_WALL_SIGN, Blocks.WARPED_WALL_SIGN);

        // Hanging signs
        registerAll(fireBlock, 30, 60,
                Blocks.OAK_HANGING_SIGN, Blocks.SPRUCE_HANGING_SIGN, Blocks.BIRCH_HANGING_SIGN,
                Blocks.JUNGLE_HANGING_SIGN, Blocks.ACACIA_HANGING_SIGN, Blocks.DARK_OAK_HANGING_SIGN,
                Blocks.CHERRY_HANGING_SIGN, Blocks.MANGROVE_HANGING_SIGN, Blocks.BAMBOO_HANGING_SIGN,
                Blocks.CRIMSON_HANGING_SIGN, Blocks.WARPED_HANGING_SIGN);

        // Bamboo and scaffolding
        register(fireBlock, Blocks.BAMBOO, 20, 40);
        register(fireBlock, Blocks.SCAFFOLDING, 20, 40);

        // Beehives and nests
        register(fireBlock, Blocks.BEEHIVE, 5, 20);
        register(fireBlock, Blocks.BEE_NEST, 5, 20);

        // Mushroom blocks
        register(fireBlock, Blocks.RED_MUSHROOM_BLOCK, 5, 20);
        register(fireBlock, Blocks.BROWN_MUSHROOM_BLOCK, 5, 20);
        register(fireBlock, Blocks.MUSHROOM_STEM, 5, 20);

        // Dried kelp
        register(fireBlock, Blocks.DRIED_KELP_BLOCK, 10, 40);

        // Cartography, fletching, smithing, loom, grindstone (wooden parts)
        register(fireBlock, Blocks.CARTOGRAPHY_TABLE, 5, 20);
        register(fireBlock, Blocks.FLETCHING_TABLE, 5, 20);
        register(fireBlock, Blocks.SMITHING_TABLE, 5, 20);
        register(fireBlock, Blocks.LOOM, 5, 20);
        register(fireBlock, Blocks.GRINDSTONE, 5, 20);

        // Composter (wooden)
        register(fireBlock, Blocks.COMPOSTER, 5, 20);

        // Daylight detector
        register(fireBlock, Blocks.DAYLIGHT_DETECTOR, 5, 20);

        // Wooden trapdoors already registered above
        // Bows and crossbows are items, not blocks

        // Beetroots, carrots, potatoes, wheat (crops — flammable)
        registerAll(fireBlock, 30, 60,
                Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS,
                Blocks.MELON_STEM, Blocks.PUMPKIN_STEM, Blocks.ATTACHED_MELON_STEM,
                Blocks.ATTACHED_PUMPKIN_STEM, Blocks.TORCHFLOWER_CROP, Blocks.PITCHER_CROP);

        // Sweet berry bush, spore blossom
        register(fireBlock, Blocks.SWEET_BERRY_BUSH, 30, 60);

        // Lily pad, vines, grass (already partially in vanilla)
        register(fireBlock, Blocks.LILY_PAD, 30, 60);
        register(fireBlock, Blocks.VINE, 30, 60);
        register(fireBlock, Blocks.CAVE_VINES, 30, 60);
        register(fireBlock, Blocks.CAVE_VINES_PLANT, 30, 60);
        register(fireBlock, Blocks.SPORE_BLOSSOM, 30, 60);
        register(fireBlock, Blocks.AZALEA, 30, 60);
        register(fireBlock, Blocks.FLOWERING_AZALEA, 30, 60);
        register(fireBlock, Blocks.AZALEA_LEAVES, 30, 60);
        register(fireBlock, Blocks.FLOWERING_AZALEA_LEAVES, 30, 60);
        register(fireBlock, Blocks.MOSS_CARPET, 30, 60);
        register(fireBlock, Blocks.BIG_DRIPLEAF, 30, 60);
        register(fireBlock, Blocks.BIG_DRIPLEAF_STEM, 30, 60);
        register(fireBlock, Blocks.SMALL_DRIPLEAF, 30, 60);
        register(fireBlock, Blocks.HANGING_ROOTS, 30, 60);
        register(fireBlock, Blocks.MANGROVE_ROOTS, 30, 60);
        register(fireBlock, Blocks.PINK_PETALS, 30, 60);

        LOGGER.info("[firemod] Registered {} extra flammable blocks", count);
    }

    private static int count = 0;

    private void register(FireBlock fireBlock, Block block, int burn, int spread) {
        try {
            fireBlock.registerFlammableBlock(block, burn, spread);
            count++;
        } catch (Exception e) {
            // Block might already be registered — ignore
        }
    }

    private void registerAll(FireBlock fireBlock, int burn, int spread, Block... blocks) {
        for (Block block : blocks) {
            register(fireBlock, block, burn, spread);
        }
    }
}
