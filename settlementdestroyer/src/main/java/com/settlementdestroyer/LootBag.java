package com.settlementdestroyer;

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
 * Loot Bag — Gray Bundle with NBT marker.
 * Right-click to open and receive loot.
 *
 * Uses GRAY_BUNDLE (visible on Bedrock) — vanilla Bundle's use() behavior
 * is overridden by UseItemCallback which returns CONSUME.
 *
 * Always contains: Three Fangs Trident (guaranteed, with Loyalty III applied).
 */
public final class LootBag {

    private LootBag() {}

    public static final String NBT_KEY = "SettlementDestroyerLootBag";
    public static final String TYPE_KEY = "LootType";
    public static final String TYPE_BOSS = "boss";

    public static ItemStack createBossBag() {
        ItemStack stack = new ItemStack(Items.GRAY_BUNDLE);
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean(NBT_KEY, true);
        nbt.putString(TYPE_KEY, TYPE_BOSS);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("Мешок Разрушителя")
                        .formatted(Formatting.DARK_RED, Formatting.BOLD));
        stack.set(DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(List.of(
                Text.literal("ПКМ — открыть").formatted(Formatting.YELLOW),
                Text.literal("Внутри: Три Челюсти").formatted(Formatting.GOLD)
        )));
        return stack;
    }

    public static boolean isLootBag(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!stack.isOf(Items.GRAY_BUNDLE)) return false;
        NbtComponent cd = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (cd == null || cd.isEmpty()) return false;
        return cd.copyNbt().getBoolean(NBT_KEY, false);
    }

    public static void open(ItemStack stack, PlayerEntity player) {
        // Give guaranteed loot: Three Fangs Trident
        ItemStack trident = ThreeFangsTrident.create();
        // Apply Loyalty III using the server's enchantment registry.
        // This must happen before the trident is given to the player so
        // that throwing it will trigger the Loyalty return behavior.
        if (player.getEntityWorld() instanceof ServerWorld sw) {
            ThreeFangsTrident.ensureLoyaltyEnchantment(sw, trident);
        }
        if (!player.getInventory().insertStack(trident)) {
            player.dropItem(trident, false);
        }
        player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.0f);
        player.sendMessage(Text.literal("§a§lВы получили Три Челюсти!")
                .formatted(Formatting.GOLD), false);
        stack.decrement(1);
    }
}
