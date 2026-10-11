package com.totemmod;

/**
 * Thread-local state flag that is set while a LivingEntity is dropping its
 * loot. Used by {@link com.totemmod.mixin.LootPoolMixin} to filter out
 * emerald drops from any mob's loot table without affecting block / chest /
 * fishing loot tables.
 */
public final class LootDropState {

    private static final ThreadLocal<Boolean> DROPPING = ThreadLocal.withInitial(() -> false);

    private LootDropState() {}

    public static boolean isDropping() {
        return DROPPING.get();
    }

    public static void setDropping(boolean v) {
        DROPPING.set(v);
    }
}
