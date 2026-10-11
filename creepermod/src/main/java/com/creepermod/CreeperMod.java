package com.creepermod;

import net.fabricmc.api.ModInitializer;

/**
 * Serverside-only mod: creeper explosions no longer destroy blocks, but deal
 * double damage to entities.
 *
 * Implemented via a single mixin on CreeperEntity#explode() that redirects
 * the ServerWorld.createExplosion call to use a custom ExplosionBehavior
 * (canDestroyBlock → false) with doubled power.
 */
public class CreeperMod implements ModInitializer {

    public static final String MOD_ID = "creepermod";

    @Override
    public void onInitialize() {
        // All behavior is in the mixin — nothing to register here.
    }
}
