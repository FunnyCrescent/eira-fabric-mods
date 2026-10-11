package com.settlementdestroyer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static net.minecraft.server.command.CommandManager.literal;
import static net.minecraft.server.command.CommandManager.argument;

/**
 * /destroyergive <item> — admin command to give custom items.
 *
 * Items:
 *   - summon   (Знак Разрушителя)
 *   - lootbag  (Мешок Разрушителя)
 *   - trident  (Три Челюсти)
 *   - all      (all three)
 */
public final class DestroyerGiveCommand {

    private DestroyerGiveCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("destroyergive")
                    .requires(CommandManager.requirePermissionLevel(
                            CommandManager.GAMEMASTERS_CHECK))
                    .then(literal("summon").executes(ctx ->
                            give(ctx.getSource(), "summon")))
                    .then(literal("lootbag").executes(ctx ->
                            give(ctx.getSource(), "lootbag")))
                    .then(literal("trident").executes(ctx ->
                            give(ctx.getSource(), "trident")))
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
        final int[] countRef = {0};
        if (item.equals("summon") || item.equals("all")) {
            giveItem(player, DestroyerSummonItem.create());
            countRef[0]++;
        }
        if (item.equals("lootbag") || item.equals("all")) {
            giveItem(player, LootBag.createBossBag());
            countRef[0]++;
        }
        if (item.equals("trident") || item.equals("all")) {
            ItemStack trident = ThreeFangsTrident.create();
            // Lazy enchantment application: needs world reference
            if (player.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld sw) {
                ThreeFangsTrident.ensureLoyaltyEnchantment(sw, trident);
            }
            giveItem(player, trident);
            countRef[0]++;
        }
        final int count = countRef[0];
        source.sendFeedback(() -> Text.literal("Gave " + count + " item(s)")
                .formatted(Formatting.GREEN), false);
        return count;
    }

    private static void giveItem(PlayerEntity player, ItemStack stack) {
        if (!player.getInventory().insertStack(stack)) {
            player.dropItem(stack, false);
        }
    }
}
