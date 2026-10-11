package com.vulcadavmod;

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
 * Registers /vulcgive — admin command to give Vulcadav mod items for testing.
 *
 * Usage:
 *   /vulcgive box       — Wolf Box (Волчья Шкатулка, summons Vulcadav)
 *   /vulcgive bow       — Wolf Bow (Волчий Лук)
 *   /vulcgive rod       — Wolf Rod (Волчья Удочка)
 *   /vulcgive berry     — Wolf Berry (Волчья Ягода)
 *   /vulcgive compass   — Wolf Compass (Волчий Компас)
 *   /vulcgive bossbag   — Boss Loot Bag (Мешок Босса)
 *   /vulcgive all       — all of the above
 *
 * Requires permission level 2 (op).
 */
public class VulcGiveCommand {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("vulcgive")
                    .requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
                    .then(literal("box")
                            .executes(ctx -> give(ctx, WolfBox.create())))
                    .then(literal("bow")
                            .executes(ctx -> give(ctx, WolfBow.create())))
                    .then(literal("rod")
                            .executes(ctx -> give(ctx, WolfRod.create())))
                    .then(literal("berry")
                            .executes(ctx -> give(ctx, WolfBerry.create())))
                    .then(literal("compass")
                            .executes(ctx -> give(ctx, WolfCompass.create())))
                    .then(literal("bossbag")
                            .executes(ctx -> give(ctx, LootBag.createBossBag())))
                    .then(literal("all")
                            .executes(ctx -> giveAll(ctx)))
            );
        });
    }

    private static int give(CommandContext<ServerCommandSource> ctx, ItemStack stack) throws CommandSyntaxException {
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
        ItemStack[] items = {
                WolfBox.create(),
                WolfBow.create(),
                WolfRod.create(),
                WolfBerry.create(),
                WolfCompass.create(),
                LootBag.createBossBag()
        };
        for (ItemStack stack : items) {
            if (!player.getInventory().insertStack(stack)) {
                player.dropItem(stack, false);
            }
        }
        player.sendMessage(Text.literal("Выданы все предметы Вулкадава!")
                .formatted(Formatting.GREEN), false);
        return 1;
    }
}
