package com.adminmod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Serverside-only admin mod.
 *
 *  1) AUTO-RESTART: every day at 05:00 MSK (UTC+3), the server performs a
 *     graceful shutdown. The hosting panel is expected to auto-restart the
 *     server after it stops.
 *
 *     Warnings are broadcast in chat at 5 minutes, 1 minute, and 30 seconds
 *     before the restart.
 *
 *  2) MANUAL TRIGGER: the mod polls config/adminmod/restart.now every second.
 *     If the file exists, it is deleted and the server restarts after a
 *     10-second warning. This lets the AI assistant trigger a restart by
 *     uploading the file via SFTP — no RCON or SSH required.
 */
public class AdminMod implements ModInitializer {

    public static final String MOD_ID = "adminmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final ZoneId MSK = ZoneId.of("Europe/Moscow");
    private static final int RESTART_HOUR = 5;
    private static final int RESTART_MINUTE = 0;

    private static final Path TRIGGER_FILE = Paths.get("config/adminmod/restart.now");
    private static final Path SCHEDULE_FILE = Paths.get("config/adminmod/next_restart.txt");

    private static final int[] WARNINGS_SECONDS = {300, 60, 30};
    private static final int[] FIRED_WARNINGS = new int[WARNINGS_SECONDS.length]; // 0 = not fired, 1 = fired

    private static MinecraftServer server;
    private static final AtomicBoolean restartInProgress = new AtomicBoolean(false);
    private static long nextRestartEpochSecond = 0;
    private static final AtomicLong shutdownTargetTick = new AtomicLong(-1);

    @Override
    public void onInitialize() {
        try {
            Files.createDirectories(Paths.get("config/adminmod"));
        } catch (IOException e) {
            LOGGER.error("[adminmod] Failed to create config dir", e);
        }

        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            server = s;
            restartInProgress.set(false);
            shutdownTargetTick.set(-1);
            for (int i = 0; i < FIRED_WARNINGS.length; i++) FIRED_WARNINGS[i] = 0;
            scheduleNextRestart();
            LOGGER.info("[adminmod] Auto-restart scheduled for {} MSK",
                    ZonedDateTime.ofInstant(java.time.Instant.ofEpochSecond(nextRestartEpochSecond), MSK));
        });

        ServerTickEvents.END_SERVER_TICK.register(this::onTick);

        LOGGER.info("[adminmod] Initialized. Auto-restart at {:02d}:{:02d} MSK. Trigger file: {}",
                RESTART_HOUR, RESTART_MINUTE, TRIGGER_FILE);
    }

    private void scheduleNextRestart() {
        ZonedDateTime now = ZonedDateTime.now(MSK);
        ZonedDateTime next = now.withHour(RESTART_HOUR).withMinute(RESTART_MINUTE).withSecond(0).withNano(0);
        if (!next.isAfter(now)) {
            next = next.plusDays(1);
        }
        nextRestartEpochSecond = next.toEpochSecond();
        try {
            Files.writeString(SCHEDULE_FILE,
                    "Next scheduled restart: " + next + " MSK\nEpoch: " + nextRestartEpochSecond + "\n");
        } catch (IOException e) {
            LOGGER.warn("[adminmod] Could not write schedule file", e);
        }
    }

    private void onTick(MinecraftServer server) {
        // If a shutdown is scheduled, check if it's time
        long target = shutdownTargetTick.get();
        if (target > 0 && server.getTicks() >= target) {
            performShutdown(server);
            return;
        }

        if (restartInProgress.get()) return;

        // Check trigger file once per second
        if (server.getTicks() % 20 == 0) {
            if (Files.exists(TRIGGER_FILE)) {
                try { Files.deleteIfExists(TRIGGER_FILE); }
                catch (IOException e) { LOGGER.warn("[adminmod] Could not delete trigger file", e); }
                LOGGER.info("[adminmod] Manual restart triggered via file");
                scheduleRestart(server, 10);
                return;
            }
        }

        // Check auto-restart schedule
        long now = ZonedDateTime.now(MSK).toEpochSecond();
        long secondsUntil = nextRestartEpochSecond - now;

        // Fire warnings
        for (int i = 0; i < WARNINGS_SECONDS.length; i++) {
            if (FIRED_WARNINGS[i] == 0 && secondsUntil <= WARNINGS_SECONDS[i] && secondsUntil > 0) {
                FIRED_WARNINGS[i] = 1;
                broadcastWarning(WARNINGS_SECONDS[i]);
            }
        }

        // Fire restart
        if (secondsUntil <= 0) {
            LOGGER.info("[adminmod] Auto-restart time reached");
            scheduleRestart(server, 0);
        }
    }

    private void scheduleRestart(MinecraftServer server, int warningSeconds) {
        if (!restartInProgress.compareAndSet(false, true)) return;

        if (warningSeconds > 0) {
            broadcast("§c§l[Сервер] Перезагрузка через " + warningSeconds + " секунд!");
        }

        shutdownTargetTick.set(server.getTicks() + warningSeconds * 20L);
    }

    private void performShutdown(MinecraftServer server) {
        shutdownTargetTick.set(-1); // prevent re-entry
        broadcast("§c[Сервер] Перезагрузка...");
        server.getPlayerManager().getPlayerList().forEach(p ->
                p.networkHandler.disconnect(Text.literal("Сервер перезагружается, зайдите через минуту!")
                        .formatted(Formatting.YELLOW)));
        LOGGER.info("[adminmod] Executing graceful shutdown");
        server.execute(() -> {
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            server.shutdown();
        });
    }

    private void broadcastWarning(int secondsUntil) {
        String timeStr = secondsUntil >= 60
                ? (secondsUntil / 60) + " минут"
                : secondsUntil + " секунд";
        broadcast("§c§l[Сервер] Перезагрузка через " + timeStr + "!");
    }

    private void broadcast(String message) {
        if (server == null) return;
        Text msg = Text.literal(message);
        server.getPlayerManager().broadcast(msg, false);
    }
}
