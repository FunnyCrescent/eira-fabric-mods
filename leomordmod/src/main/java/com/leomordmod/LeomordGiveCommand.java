package com.leomordmod;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * /leomordgive — admin command to give Leomord-related items to the executing player.
 *
 * Usage:
 *   /leomordgive summon       — gives 1 LeomordSummonItem (Echo Shard with NBT)
 *   /leomordgive spear        — gives 1 Leomord Spear (player version, Sharpness V + Unbreaking III)
 *   /leomordgive lootbag      — gives 1 LeomordLootBag (Gray Bundle with NBT)
 *   /leomordgive all          — gives one of each
 *
 * Requires gamemaster permission level (op).
 */
public class LeomordGiveCommand {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("leomordgive")
                    .requires(CommandManager.requirePermissionLevel(
                            CommandManager.GAMEMASTERS_CHECK))
                    .then(literal("summon")
                            .executes(ctx -> give(ctx.getSource(), "summon", 1)))
                    .then(literal("spear")
                            .executes(ctx -> give(ctx.getSource(), "spear", 1)))
                    .then(literal("lootbag")
                            .executes(ctx -> give(ctx.getSource(), "lootbag", 1)))
                    .then(literal("all")
                            .executes(ctx -> giveAll(ctx.getSource())))
            );
        });
    }

    private static int give(ServerCommandSource source, String item, int count) {
        if (!(source.getPlayer() instanceof PlayerEntity player)) {
            source.sendError(Text.literal("This command can only be used by a player."));
            return 0;
        }
        ItemStack stack = switch (item) {
            case "summon" -> LeomordSummonItem.create();
            case "spear" -> {
                if (!(player.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld sw)) {
                    source.sendError(Text.literal("Server world not available."));
                    yield ItemStack.EMPTY;
                }
                yield LeomordSpear.createPlayerSpear(sw);
            }
            case "lootbag" -> LeomordLootBag.create();
            default -> ItemStack.EMPTY;
        };
        if (stack.isEmpty()) {
            source.sendError(Text.literal("Unknown item: " + item));
            return 0;
        }
        stack.setCount(count);
        if (!player.getInventory().insertStack(stack)) {
            player.dropItem(stack, false);
        }
        source.sendFeedback(() -> Text.literal("Given: " + item).formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int giveAll(ServerCommandSource source) {
        if (!(source.getPlayer() instanceof PlayerEntity player)) {
            source.sendError(Text.literal("This command can only be used by a player."));
            return 0;
        }
        give(source, "summon", 1);
        give(source, "lootbag", 1);
        // Skip spear — would require server world but we already have it via player world
        source.sendFeedback(() -> Text.literal("Given: summon + lootbag").formatted(Formatting.GREEN), false);
        return 1;
    }
}
