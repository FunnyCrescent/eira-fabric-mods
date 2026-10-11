package com.totemmod;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Constants and helpers for all 5 totem types.
 *
 * All totems use the vanilla minecraft:totem_of_undying item (no custom items
 * — everything is serverside-only). Each type is distinguished by a boolean
 * NBT marker stored under minecraft:custom_data:
 *
 *   TotemBlank      — Заготовка Тотема (no effect, used for crafting)
 *   TotemInventory  — Тотем Сохранения Инвентаря (consumable: keep inventory on death)
 *   TotemEnd        — Тотем Энда (artifact: no void damage + teleport to safety)
 *   TotemNether     — Тотем Незера (artifact: no fire/lava damage)
 *   TotemSea        — Тотем Моря (artifact: infinite water breathing + mining speed)
 *
 * Visibility on Bedrock (via Geyser):
 *   - custom_data is server-side only — invisible to Bedrock clients.
 *   - custom_name IS translated by Geyser → Bedrock sees the totem name.
 *   - lore IS translated by Geyser → Bedrock sees the description lines.
 */
public final class TotemType {

    public static final String BLANK = "TotemBlank";
    public static final String INVENTORY = "TotemInventory";
    public static final String END = "TotemEnd";
    public static final String NETHER = "TotemNether";
    public static final String SEA = "TotemSea";

    private TotemType() {}

    /** Create a Blank Totem (Заготовка) — no effect, used for crafting. */
    public static ItemStack createBlank(int count) {
        return create(count, BLANK, "Заготовка Тотема", Formatting.GOLD,
                List.of(
                        Text.literal("Не активируется при смерти").formatted(Formatting.RED),
                        Text.literal("Используется для крафта").formatted(Formatting.GRAY),
                        Text.literal("истинного Тотема Бессмертия").formatted(Formatting.GRAY)
                ));
    }

    /** Create a Totem of Inventory (Тотем Сохранения Инвентаря) — consumable. */
    public static ItemStack createInventory(int count) {
        return create(count, INVENTORY, "Тотем Сохранения Инвентаря", Formatting.AQUA,
                List.of(
                        Text.literal("Расходник").formatted(Formatting.YELLOW),
                        Text.literal("Сохраняет инвентарь при смерти").formatted(Formatting.GRAY),
                        Text.literal("Тотем уничтожается").formatted(Formatting.GRAY)
                ));
    }

    /** Create a Totem of End (Тотем Энда) — artifact. */
    public static ItemStack createEnd(int count) {
        return create(count, END, "Тотем Энда", Formatting.LIGHT_PURPLE,
                List.of(
                        Text.literal("Артефакт").formatted(Formatting.YELLOW),
                        Text.literal("Держите во второй руке").formatted(Formatting.GRAY),
                        Text.literal("Защита от урона Бездной").formatted(Formatting.DARK_PURPLE),
                        Text.literal("Телепорт на твёрдую поверхность").formatted(Formatting.DARK_PURPLE)
                ));
    }

    /** Create a Totem of Nether (Тотем Незера) — artifact. */
    public static ItemStack createNether(int count) {
        return create(count, NETHER, "Тотем Незера", Formatting.RED,
                List.of(
                        Text.literal("Артефакт").formatted(Formatting.YELLOW),
                        Text.literal("Держите во второй руке").formatted(Formatting.GRAY),
                        Text.literal("Полная защита от огня и лавы").formatted(Formatting.DARK_RED)
                ));
    }

    /** Create a Totem of Sea (Тотем Моря) — artifact. */
    public static ItemStack createSea(int count) {
        return create(count, SEA, "Тотем Моря", Formatting.BLUE,
                List.of(
                        Text.literal("Артефакт").formatted(Formatting.YELLOW),
                        Text.literal("Держите во второй руке").formatted(Formatting.GRAY),
                        Text.literal("Бесконечное дыхание под водой").formatted(Formatting.DARK_AQUA),
                        Text.literal("Скорость копания как на суше").formatted(Formatting.DARK_AQUA)
                ));
    }

    /**
     * Build a totem item stack with the given NBT marker, name, and lore.
     */
    private static ItemStack create(int count, String marker, String displayName,
                                    Formatting nameColor, List<Text> loreLines) {
        ItemStack stack = new ItemStack(Items.TOTEM_OF_UNDYING, count);

        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean(marker, true);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));

        stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal(displayName).formatted(nameColor, Formatting.BOLD));

        stack.set(DataComponentTypes.LORE, new LoreComponent(loreLines));

        return stack;
    }

    /** Check if the stack has the given totem marker. */
    public static boolean isType(ItemStack stack, String marker) {
        if (stack == null || stack.isEmpty()) return false;
        if (!stack.isOf(Items.TOTEM_OF_UNDYING)) return false;
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) return false;
        NbtCompound nbt = customData.copyNbt();
        return nbt.getBoolean(marker, false);
    }

    public static boolean isBlank(ItemStack stack) { return isType(stack, BLANK); }
    public static boolean isInventory(ItemStack stack) { return isType(stack, INVENTORY); }
    public static boolean isEnd(ItemStack stack) { return isType(stack, END); }
    public static boolean isNether(ItemStack stack) { return isType(stack, NETHER); }
    public static boolean isSea(ItemStack stack) { return isType(stack, SEA); }

    /** Check if the stack is ANY of our custom totems (not a vanilla totem). */
    public static boolean isAnyCustom(ItemStack stack) {
        return isBlank(stack) || isInventory(stack) || isEnd(stack)
                || isNether(stack) || isSea(stack);
    }
}
