package com.modlogmod;

import net.fabricmc.api.ModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.RollingRandomAccessFileAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.filter.RegexFilter;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Serverside-only mod that wires up a SEPARATE log file for our other two
 * mods (totemmod and villagermod), in addition to their normal appearance in
 * the main server log.
 *
 * Output file: logs/mods.log
 *   - Contains every log message whose logger name starts with
 *     "com.totemmod" or "com.villagermod".
 *   - Same pattern as the main server log: [%d{HH:mm:ss}] [%t/%level] %msg%n
 *   - Rolls daily + on server restart, gzipped archives under
 *     logs/%d{yyyy-MM-dd}-%i-mods.log.gz
 *
 * Implementation:
 *  - Registers a RollingRandomAccessFileAppender named "ModsFile" on the
 *    root logger config.
 *  - The appender carries a RegexFilter that matches logger names beginning
 *    with "com.totemmod" or "com.villagermod" (Log4j2 RegexFilter runs on
 *    the formatted message, but we put the logger name into the pattern via
 *    %c so the regex can match it).
 *
 * Important: this must load BEFORE totemmod and villagermod, otherwise early
 * log messages from those mods would be missed. Fabric entrypoint ordering
 * is alphabetic by mod id by default ("modlogmod" < "totemmod" < "villagermod"),
 * so this works out of the box.
 */
public class ModLogMod implements ModInitializer {

    public static final String MOD_ID = "modlogmod";

    /** Loggers whose names start with any of these prefixes go to mods.log. */
    private static final String[] WATCHED_PREFIXES = {
            "com.totemmod",
            "com.villagermod"
    };

    /** Output path (relative to the server working directory). */
    private static final String FILE_NAME = "logs/mods.log";
    private static final String FILE_PATTERN = "logs/%d{yyyy-MM-dd}-%i-mods.log.gz";

    /** Pattern — includes %c (logger name) at the start so the filter can match it. */
    private static final String PATTERN = "[%d{HH:mm:ss}] [%t/%level] [%c] %msg%n";

    @Override
    public void onInitialize() {
        try {
            wireUpModsFileAppender();
            org.apache.logging.log4j.Logger log = LogManager.getLogger("com.modlogmod");
            log.info("mods.log appender registered — capturing loggers starting with: {}",
                    String.join(", ", WATCHED_PREFIXES));
        } catch (Throwable t) {
            // Don't let a logging failure crash the server.
            System.err.println("[modlogmod] Failed to wire up mods.log appender: " + t);
            t.printStackTrace();
        }
    }

    private void wireUpModsFileAppender() {
        // Build a regex that matches any line whose [%c] segment starts with
        // one of our watched prefixes. The pattern puts the logger name in
        // square brackets at position 3 ([time] [thread/level] [logger] msg),
        // so we look for "[com.totemmod" or "[com.villagermod" with optional
        // child packages after the dot.
        StringBuilder regex = new StringBuilder();
        regex.append("(?:");
        for (int i = 0; i < WATCHED_PREFIXES.length; i++) {
            if (i > 0) regex.append("|");
            // Match "[<prefix>" possibly followed by ".<anything>" then "]"
            regex.append("\\[").append(java.util.regex.Pattern.quote(WATCHED_PREFIXES[i])).append("(?:\\..*)?\\]");
        }
        regex.append(")");

        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();

        PatternLayout layout = PatternLayout.newBuilder()
                .withConfiguration(config)
                .withPattern(PATTERN)
                .build();

        RegexFilter filter;
        try {
            filter = RegexFilter.createFilter(
                    regex.toString(),
                    new String[0],
                    /* useRawMsg */ false,
                    /* onMatch */ org.apache.logging.log4j.core.Filter.Result.ACCEPT,
                    /* onMismatch */ org.apache.logging.log4j.core.Filter.Result.DENY
            );
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to build RegexFilter for mods.log", e);
        }

        // Ensure logs/ directory exists (it usually does, but be safe).
        try {
            Path logsDir = Paths.get("logs");
            if (!java.nio.file.Files.exists(logsDir)) {
                java.nio.file.Files.createDirectories(logsDir);
            }
        } catch (java.io.IOException ioe) {
            System.err.println("[modlogmod] Could not create logs/ directory: " + ioe);
        }

        RollingRandomAccessFileAppender appender = RollingRandomAccessFileAppender.newBuilder()
                .withName("ModsFile")
                .withFileName(FILE_NAME)
                .withFilePattern(FILE_PATTERN)
                .withLayout(layout)
                .withFilter(filter)
                .withAppend(true)
                .withImmediateFlush(true)
                .withPolicy(org.apache.logging.log4j.core.appender.rolling.CompositeTriggeringPolicy
                        .createPolicy(
                                org.apache.logging.log4j.core.appender.rolling.TimeBasedTriggeringPolicy
                                        .createPolicy("1", "true"),
                                org.apache.logging.log4j.core.appender.rolling.OnStartupTriggeringPolicy
                                        .createPolicy(0)
                        ))
                .withStrategy(org.apache.logging.log4j.core.appender.rolling.DefaultRolloverStrategy
                        .createStrategy("10", "1", null, null, null, true, null))
                .setConfiguration(config)
                .build();
        appender.start();

        // Register the appender on the root logger config so every log event
        // has a chance to flow through it (the filter does the actual
        // routing).
        LoggerConfig rootConfig = config.getLoggerConfig("");
        rootConfig.addAppender(appender, null, filter);

        // Re-read the configuration so the new appender takes effect.
        ctx.updateLoggers();
    }
}
