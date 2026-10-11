package net.UlasuNoka.bettercrafter;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BetterCrafter — improves the crafter block by preventing it from
 * triggering or crafting when any input slot contains exactly 1 item.
 *
 * This lets you keep "template" items in the crafter: put 1 of each
 * ingredient in its slot, and the crafter won't fire until you add more
 * items via hopper. The last item is never consumed.
 */
public class BetterCrafter implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("bettercrafter");

    @Override
    public void onInitialize() {
        LOGGER.info("BetterCrafter initialized for 1.21.11");
    }
}
