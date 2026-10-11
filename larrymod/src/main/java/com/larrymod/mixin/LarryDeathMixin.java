package com.larrymod.mixin;

import com.larrymod.LarryEntity;
import com.larrymod.LarryMod;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(HostileEntity.class)
public abstract class LarryDeathMixin {

    @Inject(method = "dropLoot", at = @At("HEAD"), cancellable = true)
    private void larrymod$cancelVanillaLoot(ServerWorld world, DamageSource source, boolean causedByPlayer, CallbackInfo ci) {
        if ((Object) this instanceof LarryEntity) {
            ci.cancel();
        }
    }

    @Inject(method = "onDeath", at = @At("RETURN"))
    private void larrymod$customDrops(DamageSource source, CallbackInfo ci) {
        HostileEntity self = (HostileEntity) (Object) this;
        if (!(self instanceof LarryEntity larry)) return;

        World world = self.getEntityWorld();
        if (!(world instanceof ServerWorld serverWorld)) return;

        long remaining = serverWorld.getEntitiesByType(LarryMod.LARRY,
                l -> l != larry && l.isAlive() && l.getGroupId().equals(larry.getGroupId())).size();

        if (remaining > 0) return;

        // Get last attacker info
        UUID lastAttacker = larry.getLastAttackerUuid();
        boolean swordKill = larry.wasLastAttackerUsingSword();

        // Pre-generate loot values
        int boneCount = serverWorld.getRandom().nextInt(9);
        boolean hasBone = swordKill && larry.didGroupGrow()
                && serverWorld.getRandom().nextDouble() < 0.025;

        // Create loot bag with sword info in the description
        ItemStack bag = com.vulcadavmod.LootBag.createLarryBag(swordKill, larry.didGroupGrow(), boneCount, hasBone);

        // Give bag directly to last attacker if online, otherwise drop on ground
        if (lastAttacker != null) {
            ServerPlayerEntity killer = serverWorld.getServer().getPlayerManager().getPlayer(lastAttacker);
            if (killer != null) {
                // Give directly to inventory
                if (!killer.getInventory().insertStack(bag)) {
                    // Inventory full — drop at killer's location
                    ItemEntity itemEntity = new ItemEntity(serverWorld,
                            killer.getX(), killer.getY() + 0.5, killer.getZ(), bag);
                    serverWorld.spawnEntity(itemEntity);
                }
                killer.sendMessage(net.minecraft.text.Text.literal("§6Вы получили Мешок Ларри! ПКМ — открыть")
                        .formatted(net.minecraft.util.Formatting.GOLD), false);
                return;
            }
        }

        // Fallback: drop at death location
        ItemEntity itemEntity = new ItemEntity(serverWorld,
                self.getX(), self.getY() + 0.5, self.getZ(), bag);
        itemEntity.setVelocity(
                (serverWorld.random.nextDouble() - 0.5) * 0.3,
                0.3,
                (serverWorld.random.nextDouble() - 0.5) * 0.3);
        serverWorld.spawnEntity(itemEntity);
    }
}
