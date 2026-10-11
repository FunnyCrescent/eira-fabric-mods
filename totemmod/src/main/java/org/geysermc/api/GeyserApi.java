package org.geysermc.api;

/**
 * Empty marker interface used only so that mods that probe for the legacy
 * Spigot-style class name {@code org.geysermc.api.GeyserApi} (e.g.
 * SimpleVoice-Geyser 0.1.x Fabric) find *something* on the classpath when
 * Geyser-Fabric is installed.
 *
 * Geyser-Fabric ships the real API class at the new package
 * {@code org.geysermc.geyser.api.GeyserApi}; SVG's actual runtime calls use
 * that path, but SVG's presence check uses the old path. Placing this empty
 * interface here makes the presence check succeed, and SVG then proceeds to
 * use the correct class path for its real calls.
 *
 * This class intentionally has no methods and no implementations — it must
 * not be instantiated or invoked.
 */
public interface GeyserApi {
}
