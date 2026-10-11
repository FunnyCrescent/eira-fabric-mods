package com.totemmod.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Accessor for the private copyInventoryAndExperienceFrom method on
 * ServerPlayerEntity. Used by the Totem of Inventory to copy inventory
 * from the old (dead) player to the new (respawned) player.
 */
@Mixin(ServerPlayerEntity.class)
public interface ServerPlayerAccessor {

    @Invoker("copyInventoryAndExperienceFrom")
    void totemmod$copyInventoryAndExperience(PlayerEntity oldPlayer);
}
