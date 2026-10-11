package com.villagermod;

import net.fabricmc.api.ModInitializer;

/**
 * Serverside-only mod that rebalances the villager economy + adds the
 * Alchemist profession (Brewer / Supplier variations).
 *
 * IMPORTANT: The Alchemist is NOT registered as a new VillagerProfession
 * in the vanilla registry — that would sync to clients and kick Bedrock
 * players ("requires Fabric API"). Instead, we reuse the vanilla
 * LEATHERWORKER profession and detect Alchemists via a custom NBT tag
 * on the villager entity. The mixin on fillRecipes checks this tag.
 */
public class VillagerMod implements ModInitializer {

    public static final String MOD_ID = "villagermod";

    /** NBT tag marking a villager as an Alchemist. */
    public static final String ALCHEMIST_TAG = "villagermod_alchemist";

    @Override
    public void onInitialize() {
        // No registry registration — Alchemist is detected via NBT tag,
        // not via a custom VillagerProfession entry.
    }
}
