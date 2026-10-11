package com.settlementdestroyer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static net.minecraft.server.command.CommandManager.literal;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * /dtr — Toggle "Three Fangs trident hits players when thrown" mode.
 *
 * Default: OFF (trident fangs do NOT damage players on throw)
 * /dtr — toggles between ON and OFF, broadcasts to all players.
 *
 * When ON:  thrown trident fangs damage players (PvP-enabled)
 * When OFF: thrown trident fangs do NOT damage players (PvE-safe)
 *
 * Fanged mobs are ALWAYS damaged regardless of mode.
 */
public final class DtrCommand {

    private DtrCommand() {}

    /** Global state — true = trident fangs damage players on throw. */
    private static final AtomicBoolean tridentHitsPlayers = new AtomicBoolean(false);

    public static boolean tridentHitsPlayers() {
        return tridentHitsPlayers.get();
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("dtr")
                    .executes(ctx -> toggle(ctx.getSource()))
            );
        });
    }

    private static int toggle(ServerCommandSource source) {
        boolean oldValue = tridentHitsPlayers.get();
        boolean newValue = !oldValue;
        tridentHitsPlayers.set(newValue);
        Text message;
        if (newValue) {
            message = Text.literal("§c[Tри Челюсти] §fРежим PvP §aВКЛЮЧЁН§f — челюсти при броске бьют игроков.")
                    .formatted(Formatting.WHITE);
        } else {
            message = Text.literal("§c[Tри Челюсти] §fРежим PvP §7ВЫКЛЮЧЕН§f — челюсти при броске не бьют игроков.")
                    .formatted(Formatting.WHITE);
        }
        source.getServer().getPlayerManager().broadcast(message, false);
        return 1;
    }
}
