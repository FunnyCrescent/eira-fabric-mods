package com.hellimp;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResult;
import net.fabricmc.fabric.api.event.player.UseItemCallback;

import java.util.List;

/**
 * Heart of Demon (Сердце Демона) — Enchanted Book with NBT marker.
 *
 * Source: 25% drop from HellImp (Бес).
 * Texture: Enchanted Book (vanilla item, Bedrock-visible via Geyser).
 *
 * Use: Right-click while holding → permanently expands accessory slots
 * from 6 to 7 for that player. Heart is consumed.
 *
 * Implementation: Enchanted Book with custom NBT marker. The enchantment
 * glint override gives it a custom look.
 */
public final class HeartOfDemon {

    private HeartOfDemon() {}

    public static final String NBT_KEY = "HeartOfDemon";
    public static final String PLAYER_TAG_EXTENDED = "hellimp_extended_slots";

    public static ItemStack create() {
        ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean(NBT_KEY, true);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("Сердце Демона")
                        .formatted(Formatting.DARK_RED, Formatting.BOLD));
        stack.set(DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(List.of(
                Text.literal("Дроп с Беса").formatted(Formatting.GRAY),
                Text.literal("ПКМ — расширить слоты аксессуаров до 7").formatted(Formatting.YELLOW)
        )));
        // Enchanted book has natural glint — no need for override
        return stack;
    }

    public static boolean isHeartOfDemon(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!stack.isOf(Items.ENCHANTED_BOOK)) return false;
        NbtComponent cd = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (cd == null || cd.isEmpty()) return false;
        return cd.copyNbt().getBoolean(NBT_KEY, false);
    }

    public static void register() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;

            ItemStack stack = player.getStackInHand(hand);
            if (!isHeartOfDemon(stack)) return ActionResult.PASS;

            // Check if player already has expanded slots
            if (AccessoriesSystem.hasExtendedSlots(player)) {
                player.sendMessage(Text.literal("§cУ вас уже расширенные слоты (7/7)")
                        .formatted(Formatting.RED), true);
                return ActionResult.CONSUME;
            }

            // Consume heart and expand slots
            stack.decrement(1);
            AccessoriesSystem.setExtendedSlots(player, true);
            player.playSound(SoundEvents.ITEM_TOTEM_USE, 1.0f, 0.7f);
            player.sendMessage(Text.literal("§a§lСердце Демона поглощено! Слоты аксессуаров расширены до 7.")
                    .formatted(Formatting.GREEN), false);

            return ActionResult.CONSUME;
        });
    }
}
