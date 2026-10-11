package com.fireworkmod;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Serverside-only mod that buffs the simple firework rocket recipe:
 * 1 gunpowder + 1 paper = 8 firework rockets (instead of vanilla 3).
 *
 * The advanced recipe (firework_rocket.json — crafting_special_firework_rocket)
 * is unchanged; it already produces 1 rocket per star + flight modifier.
 *
 * Implemented via a JSON recipe override at
 * data/minecraft/recipe/firework_rocket_simple.json.
 */
public class FireworkMod implements ModInitializer {

    public static final String MOD_ID = "fireworkmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[fireworkmod] Simple firework rocket recipe now yields 8 rockets (was 3)");
    }
}
