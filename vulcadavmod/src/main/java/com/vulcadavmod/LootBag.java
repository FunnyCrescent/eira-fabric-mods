package com.vulcadavmod;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Loot Bag — generates loot on right-click.
 *
 * Uses CLAY_BALL (no built-in right-click behavior) instead of Bundle.
 * Bundle has its own use() method that opens inventory UI — conflicts
 * with UseItemCallback. Clay ball does nothing on right-click, so our
 * callback works cleanly on both Java and Bedrock.
 */
public final class LootBag {

    private LootBag() {}

    public static final String NBT_KEY = "LootBag";
    public static final String TYPE_KEY = "LootType";
    public static final String SWORD_KILL_KEY = "SwordKill";
    public static final String GROUP_GREW_KEY = "GroupGrew";
    public static final String BONE_COUNT_KEY = "BoneCount";
    public static final String HAS_WOLF_BOX_KEY = "HasWolfBox";
    public static final String HAS_BONE_KEY = "HasSharpenedBone";

    public static final String TYPE_LARRY = "larry";
    public static final String TYPE_BOSS = "boss";

    /** Create a Larry loot bag with pre-generated contents. */
    public static ItemStack createLarryBag(boolean swordKill, boolean groupGrew, int boneCount, boolean hasBone) {
        ItemStack stack = new ItemStack(Items.GRAY_BUNDLE);
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean(NBT_KEY, true);
        nbt.putString(TYPE_KEY, TYPE_LARRY);
        nbt.putBoolean(SWORD_KILL_KEY, swordKill);
        nbt.putBoolean(GROUP_GREW_KEY, groupGrew);
        nbt.putInt(BONE_COUNT_KEY, boneCount);
        nbt.putBoolean(HAS_WOLF_BOX_KEY, true);
        nbt.putBoolean(HAS_BONE_KEY, hasBone);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("Мешок Ларри").formatted(Formatting.GOLD, Formatting.BOLD));

        // Build lore with kill info
        List<Text> lore = new java.util.ArrayList<>();
        lore.add(Text.literal("ПКМ — открыть").formatted(Formatting.YELLOW));
        lore.add(Text.literal(swordKill ? "Убийство: меч" : "Убийство: другое")
                .formatted(swordKill ? Formatting.GREEN : Formatting.GRAY));
        if (groupGrew) {
            lore.add(Text.literal("Группа разрослась").formatted(Formatting.DARK_GREEN));
        }
        if (hasBone) {
            lore.add(Text.literal("§6✦ Точенная кость внутри!").formatted(Formatting.GOLD));
        }
        stack.set(DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(lore));
        return stack;
    }

    /** Create a Boss loot bag. */
    public static ItemStack createBossBag() {
        ItemStack stack = new ItemStack(Items.GRAY_BUNDLE);
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean(NBT_KEY, true);
        nbt.putString(TYPE_KEY, TYPE_BOSS);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("Мешок Босса").formatted(Formatting.DARK_PURPLE, Formatting.BOLD));
        stack.set(DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(List.of(
                Text.literal("ПКМ — открыть").formatted(Formatting.YELLOW)
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

    /** Open the loot bag and generate items directly into player's inventory. */
    public static void open(ItemStack stack, PlayerEntity player) {
        NbtComponent cd = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (cd == null) return;
        NbtCompound nbt = cd.copyNbt();
        String type = nbt.getString(TYPE_KEY, TYPE_LARRY);

        if (type.equals(TYPE_LARRY)) {
            openLarryBag(player, nbt);
        } else if (type.equals(TYPE_BOSS)) {
            openBossBag(player);
        }

        player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 0.7f, 1.0f);
        stack.decrement(1);
    }

    /** Larry loot bag — uses pre-generated contents from NBT. */
    private static void openLarryBag(PlayerEntity player, NbtCompound nbt) {
        // Bones
        int bones = nbt.getInt(BONE_COUNT_KEY, 0);
        if (bones > 0) {
            giveItem(player, new ItemStack(Items.BONE, bones));
        }
        // Wolf box — 100%
        if (nbt.getBoolean(HAS_WOLF_BOX_KEY, false)) {
            giveItem(player, WolfBox.create());
        }
        // Sharpened bone
        if (nbt.getBoolean(HAS_BONE_KEY, false)) {
            giveItem(player, createSharpenedBone());
        }
        player.sendMessage(Text.literal("§aМешок Ларри открыт!").formatted(Formatting.GREEN), true);
    }

    /** Boss loot bag — 100% wolf berry + 50/50 wolf bow or wolf rod. */
    private static void openBossBag(PlayerEntity player) {
        giveItem(player, WolfBerry.create());

        // 50/50 — equal chance for bow or rod.
        // nextBoolean() returns true/false with 50% probability each.
        boolean giveBow = player.getEntityWorld().getRandom().nextBoolean();
        if (giveBow) {
            giveItem(player, WolfBow.create());
        } else {
            giveItem(player, WolfRod.create());
        }
        player.sendMessage(Text.literal("§aМешок Босса открыт!").formatted(Formatting.GREEN), true);
    }

    /** Create sharpened bone (LarryBone equivalent). */
    private static ItemStack createSharpenedBone() {
        ItemStack bone = new ItemStack(Items.BONE);
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean("LarryBone", true);
        bone.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        bone.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("Точенная Кость Ларри").formatted(Formatting.GOLD, Formatting.BOLD));
        bone.set(DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(List.of(
                Text.literal("Легендарное оружие").formatted(Formatting.YELLOW),
                Text.literal("Серия ударов: 1 → 2 → 4 → 8 → 16 → 32").formatted(Formatting.GRAY)
        )));
        return bone;
    }

    private static void giveItem(PlayerEntity player, ItemStack stack) {
        if (!player.getInventory().insertStack(stack)) {
            player.dropItem(stack, false);
        }
    }
}
