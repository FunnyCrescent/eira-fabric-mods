package com.leomordmod;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Leomord Loot Bag — Gray Bundle with NBT marker.
 *
 * STRICT REQUIREMENT (from spec): "LeomordLootBag" is an ISOLATED class.
 * NBT key "LeomordLootBag" — does NOT collide with vulcadavmod's "LootBag".
 *
 * Drop table (per spec iteration 7):
 *   - One random player (the "head winner") gets: 3x Wither Skeleton Skull
 *     + 1 random item from the table below.
 *   - Other players get: just 1 random item.
 *
 * Random item drop table (per-player chance):
 *   - Взрывное копье (ExplosiveSpear)    9%
 *   - Меч подлёта (LiftSword)            8%
 *   - Лук огня (FireBow)                 6%
 *   - Арбалет Падали (CarrionCrossbow)   5%
 *   - Символ огня (FireSymbol)          3.5%
 *   - 5 костей (Bones x5)             69.5%
 */
public final class LeomordLootBag {

    private LeomordLootBag() {}

    public static final String NBT_KEY = "LeomordLootBag";
    public static final String HEAD_WINNER_KEY = "HeadWinner";
    public static final String LOOT_ITEM_KEY = "LootItemKey";

    /** Loot item keys (stored in NBT, regenerated at open time). */
    public static final String LOOT_EXPLOSIVE_SPEAR  = "ExplosiveSpear";
    public static final String LOOT_LIFT_SWORD       = "LiftSword";
    public static final String LOOT_FIRE_BOW         = "FireBow";
    public static final String LOOT_CARRION_CROSSBOW = "CarrionCrossbow";
    public static final String LOOT_FIRE_SYMBOL      = "FireSymbol";
    public static final String LOOT_BONES            = "Bones";

    /** Create a loot bag with the given flags. The loot item is rolled
     *  randomly here (at bag-creation time = death time).
     *  - isHeadWinner: if true, the bag will give 3x Wither Skeleton Skull
     *    on open, plus the random item.
     *  - lootItemKey: one of LOOT_* constants — which random item to give. */
    public static ItemStack create(boolean isHeadWinner, String lootItemKey) {
        ItemStack stack = new ItemStack(Items.GRAY_BUNDLE);
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean(NBT_KEY, true);
        nbt.putBoolean(HEAD_WINNER_KEY, isHeadWinner);
        nbt.putString(LOOT_ITEM_KEY, lootItemKey);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("Мешок Леоморда")
                        .formatted(Formatting.DARK_RED, Formatting.BOLD));
        stack.set(DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(List.of(
                Text.literal("ПКМ — открыть").formatted(Formatting.YELLOW),
                Text.literal("Добыт с Вождя Падших").formatted(Formatting.DARK_PURPLE),
                isHeadWinner
                        ? Text.literal("§6✦ Победитель голов").formatted(Formatting.GOLD)
                        : Text.literal("").formatted(Formatting.GRAY)
        )));
        return stack;
    }

    /** Backwards-compatible create() — used by tests/fallbacks (no loot inside). */
    public static ItemStack create() {
        return create(false, LOOT_BONES);
    }

    public static boolean isLootBag(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!stack.isOf(Items.GRAY_BUNDLE)) return false;
        NbtComponent cd = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (cd == null || cd.isEmpty()) return false;
        return cd.copyNbt().getBoolean(NBT_KEY, false);
    }

    /**
     * Roll a random loot item key. Called once per loot bag at creation time
     * (so the same item is given when the player opens the bag later).
     *
     * Drop chances (per spec):
     *   - ExplosiveSpear    9%
     *   - LiftSword         8%
     *   - FireBow           6%
     *   - CarrionCrossbow   5%
     *   - FireSymbol       3.5%
     *   - Bones (x5)     69.5%
     */
    public static String rollLootItem(ServerWorld world) {
        double r = world.getRandom().nextDouble();
        if (r < 0.09) return LOOT_EXPLOSIVE_SPEAR;
        if (r < 0.17) return LOOT_LIFT_SWORD;
        if (r < 0.23) return LOOT_FIRE_BOW;
        if (r < 0.28) return LOOT_CARRION_CROSSBOW;
        if (r < 0.315) return LOOT_FIRE_SYMBOL;
        return LOOT_BONES;  // 69.5%
    }

    /** Generate the ItemStack for a given loot item key. */
    private static ItemStack generateLootItem(String key, ServerWorld world) {
        switch (key) {
            case LOOT_EXPLOSIVE_SPEAR:
                return ExplosiveSpear.create(world);
            case LOOT_LIFT_SWORD:
                return LiftSword.create();
            case LOOT_FIRE_BOW:
                return FireBow.create();
            case LOOT_CARRION_CROSSBOW:
                return CarrionCrossbow.create();
            case LOOT_FIRE_SYMBOL:
                return FireSymbol.create();
            case LOOT_BONES:
            default:
                return new ItemStack(Items.BONE, 5);
        }
    }

    /**
     * Open the loot bag — give the player:
     *   - 3x Wither Skeleton Skull (if they're the head winner)
     *   - The pre-rolled random loot item
     */
    public static void open(ItemStack stack, PlayerEntity player) {
        if (!(player.getEntityWorld() instanceof ServerWorld sw)) return;

        NbtComponent cd = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (cd == null) return;
        NbtCompound nbt = cd.copyNbt();
        boolean isHeadWinner = nbt.getBoolean(HEAD_WINNER_KEY, false);
        String lootItemKey = nbt.getString(LOOT_ITEM_KEY, LOOT_BONES);

        // Give 3x Wither Skeleton Skull to the head winner
        if (isHeadWinner) {
            ItemStack heads = new ItemStack(Items.WITHER_SKELETON_SKULL, 3);
            giveItem(player, heads);
            player.sendMessage(Text.literal("§6§lВы — победитель голов Леоморда!")
                    .formatted(Formatting.GOLD), false);
        }

        // Give the pre-rolled random item
        ItemStack loot = generateLootItem(lootItemKey, sw);
        giveItem(player, loot);

        // Sound + message
        player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.0f);
        player.sendMessage(Text.literal("§6§lМешок Леоморда открыт!")
                .formatted(Formatting.GOLD), false);

        stack.decrement(1);
    }

    private static void giveItem(PlayerEntity player, ItemStack stack) {
        if (!player.getInventory().insertStack(stack)) {
            player.dropItem(stack, false);
        }
    }
}
