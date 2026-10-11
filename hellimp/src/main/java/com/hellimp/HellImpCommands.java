package com.hellimp;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static net.minecraft.server.command.CommandManager.literal;

/**
 * /aks — opens the Accessories GUI.
 *
 * On Java: opens a ScreenHandler with 6 (or 7) accessory slots + player inventory.
 * On Bedrock: GUI may not work properly (Geyser doesn't fully translate
 * custom screen handlers). Bedrock players can still obtain and hold accessories,
 * but cannot place them in slots via GUI.
 */
public class HellImpCommands {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("aks")
                    .executes(ctx -> openAccessories(ctx.getSource()))
            );
        });
    }

    private static int openAccessories(ServerCommandSource source) {
        if (!(source.getEntity() instanceof PlayerEntity player)) {
            source.sendError(Text.literal("Команда доступна только игрокам"));
            return 0;
        }

        // Check if player has accessory inventory loaded
        SimpleInventory inv = AccessoriesSystem.getInventory(player);
        if (inv == null) {
            source.sendError(Text.literal("Инвентарь аксессуаров не загружен"));
            return 0;
        }

        // Open the GUI
        NamedScreenHandlerFactory factory = new SimpleNamedScreenHandlerFactory(
                (syncId, playerInventory, p) -> new AccessoriesScreenHandler(syncId, playerInventory),
                Text.literal("Аксессуары").formatted(Formatting.DARK_PURPLE, Formatting.BOLD)
        );
        player.openHandledScreen(factory);

        source.sendFeedback(() -> Text.literal("Открыты слоты аксессуаров ("
                + AccessoriesSystem.getSlotCount(player) + "/7)").formatted(Formatting.GREEN), false);
        return 1;
    }
}
