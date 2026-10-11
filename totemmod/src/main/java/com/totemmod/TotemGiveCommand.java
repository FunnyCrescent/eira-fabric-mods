package com.totemmod;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static net.minecraft.server.command.CommandManager.literal;
import net.minecraft.server.command.CommandManager;

/**
 * Registers the /totemgive command — a server-side admin command that lets
 * op'd players give themselves any of the 5 custom totem types for testing.
 *
 * Usage:
 *   /totemgive blank        — gives a Blank Totem (Заготовка)
 *   /totemgive inventory    — gives a Totem of Inventory (Тотем Сохранения Инвентаря)
 *   /totemgive end          — gives a Totem of End (Тотем Энда)
 *   /totemgive nether       — gives a Totem of Nether (Тотем Незера)
 *   /totemgive sea          — gives a Totem of Sea (Тотем Моря)
 *   /totemgive all          — gives one of each
 *
 * Requires permission level 2 (op).
 */
public class TotemGiveCommand {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("totemgive")
                    .requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
                    .then(literal("blank")
                            .executes(ctx -> giveTotem(ctx, TotemType.createBlank(1))))
                    .then(literal("inventory")
                            .executes(ctx -> giveTotem(ctx, TotemType.createInventory(1))))
                    .then(literal("end")
                            .executes(ctx -> giveTotem(ctx, TotemType.createEnd(1))))
                    .then(literal("nether")
                            .executes(ctx -> giveTotem(ctx, TotemType.createNether(1))))
                    .then(literal("sea")
                            .executes(ctx -> giveTotem(ctx, TotemType.createSea(1))))
                    .then(literal("enderchest")
                            .executes(ctx -> giveTotem(ctx, PortableEnderChest.create())))
                    .then(literal("all")
                            .executes(ctx -> giveAll(ctx)))
            );
        });
    }

    private static int giveTotem(CommandContext<ServerCommandSource> ctx, ItemStack stack) throws CommandSyntaxException {
        PlayerEntity player = ctx.getSource().getPlayerOrThrow();
        if (!player.getInventory().insertStack(stack)) {
            player.dropItem(stack, false);
        }
        player.sendMessage(Text.literal("Выдан: " + stack.getName().getString())
                .formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int giveAll(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        PlayerEntity player = ctx.getSource().getPlayerOrThrow();
        ItemStack[] totems = {
                TotemType.createBlank(1),
                TotemType.createInventory(1),
                TotemType.createEnd(1),
                TotemType.createNether(1),
                TotemType.createSea(1),
                PortableEnderChest.create()
        };
        for (ItemStack stack : totems) {
            if (!player.getInventory().insertStack(stack)) {
                player.dropItem(stack, false);
            }
        }
        player.sendMessage(Text.literal("Выданы все 5 тотемов + Ключ Эндерсундука!")
                .formatted(Formatting.GREEN), false);
        return 1;
    }
}
