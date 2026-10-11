package com.hellimp;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static net.minecraft.server.command.CommandManager.literal;

/**
 * /hellimpgive <item> — admin command to give custom items.
 *
 * Items:
 *   - cloak   (Элитовый Плащ)
 *   - food    (Аксессуар Бесконечной Еды)
 *   - gravity (Аксессуар Антигравитации)
 *   - heart   (Сердце Демона)
 *   - all     (all of the above)
 */
public class HellImpGiveCommand {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("hellimpgive")
                    .requires(CommandManager.requirePermissionLevel(
                            CommandManager.GAMEMASTERS_CHECK))
                    .then(literal("cloak").executes(ctx ->
                            give(ctx.getSource(), "cloak")))
                    .then(literal("food").executes(ctx ->
                            give(ctx.getSource(), "food")))
                    .then(literal("gravity").executes(ctx ->
                            give(ctx.getSource(), "gravity")))
                    .then(literal("heart").executes(ctx ->
                            give(ctx.getSource(), "heart")))
                    .then(literal("all").executes(ctx ->
                            give(ctx.getSource(), "all")))
            );
        });
    }

    private static int give(ServerCommandSource source, String item) {
        if (!(source.getEntity() instanceof PlayerEntity player)) {
            source.sendError(Text.literal("Command must be run by a player"));
            return 0;
        }
        int count = 0;
        if (item.equals("cloak") || item.equals("all")) {
            giveItem(player, ElytraCloak.create());
            count++;
        }
        if (item.equals("food") || item.equals("all")) {
            giveItem(player, InfiniteFoodAccessory.create());
            count++;
        }
        if (item.equals("gravity") || item.equals("all")) {
            giveItem(player, AntiGravityAccessory.create());
            count++;
        }
        if (item.equals("heart") || item.equals("all")) {
            giveItem(player, HeartOfDemon.create());
            count++;
        }
        final int c = count;
        source.sendFeedback(() -> Text.literal("Gave " + c + " item(s)")
                .formatted(Formatting.GREEN), false);
        return count;
    }

    private static void giveItem(PlayerEntity player, ItemStack stack) {
        if (!player.getInventory().insertStack(stack)) {
            player.dropItem(stack, false);
        }
    }
}
