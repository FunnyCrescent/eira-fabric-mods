package com.totemmod.mixin;

import com.totemmod.GraveManager;
import com.totemmod.TotemMod;
import com.totemmod.TotemType;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Mixin on ServerPlayerEntity.onDeath:
 * Totem of Inventory → store items, give Heart of the Sea.
 * No totem → create grave barrel, announce coordinates.
 */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerDeathMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("totemmod");

    @Inject(method = "onDeath", at = @At("HEAD"))
    private void totemmod$handleDeath(DamageSource source, CallbackInfo ci) {
        ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
        PlayerInventory inventory = self.getInventory();

        // Check for Totem of Inventory
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (TotemType.isInventory(stack)) {
                LOGGER.info("[totemmod] Found Totem of Inventory at slot {} — consuming", i);
                stack.decrement(1);
                if (stack.isEmpty()) {
                    inventory.setStack(i, ItemStack.EMPTY);
                }
                TotemMod.storeInventory(self);
                self.sendMessage(Text.literal("§b§lТотем Сохранения Инвентаря активирован!")
                        .formatted(Formatting.AQUA, Formatting.BOLD), false);
                self.sendMessage(Text.literal("§eПКМ по Сердцу Возврата — вернуть вещи (5 минут)")
                        .formatted(Formatting.YELLOW), false);
                return;
            }
        }

        // No totem — create grave barrel
        try {
            createGrave(self, (ServerWorld) self.getEntityWorld());
        } catch (Exception e) {
            LOGGER.warn("[totemmod] Failed to create grave", e);
        }
    }

    private static void createGrave(ServerPlayerEntity player, ServerWorld world) {
        BlockPos pos = player.getBlockPos();

        BlockPos barrelPos = pos;
        if (!world.getBlockState(barrelPos).isAir() && !world.getBlockState(barrelPos).isReplaceable()) {
            barrelPos = pos.up();
        }

        world.setBlockState(barrelPos, Blocks.BARREL.getDefaultState());

        if (world.getBlockEntity(barrelPos) instanceof BarrelBlockEntity barrel) {
            PlayerInventory inv = player.getInventory();
            List<ItemStack> items = new ArrayList<>();
            for (int i = 0; i < inv.size(); i++) {
                ItemStack stack = inv.getStack(i);
                if (!stack.isEmpty()) {
                    items.add(stack.copy());
                }
            }
            for (int i = 0; i < inv.size(); i++) {
                inv.setStack(i, ItemStack.EMPTY);
            }
            for (int i = 0; i < items.size() && i < 27; i++) {
                barrel.setStack(i, items.get(i));
            }
            for (int i = 27; i < items.size(); i++) {
                player.dropStack(world, items.get(i));
            }
            barrel.markDirty();
        }

        long removeAtTick = world.getTime() + 20L * 60 * 30;
        GraveManager.registerGrave(barrelPos, world.getRegistryKey(), removeAtTick, player.getUuid());

        String coords = barrelPos.getX() + ", " + barrelPos.getY() + ", " + barrelPos.getZ();
        Text announce = Text.literal("§6☠ Могила " + player.getName().getString() + ": " + coords + " §7(X, Y, Z)");
        world.getServer().getPlayerManager().broadcast(announce, false);

        player.sendMessage(Text.literal("§cВаши вещи в могиле: " + coords)
                .formatted(Formatting.RED), false);
    }
}
