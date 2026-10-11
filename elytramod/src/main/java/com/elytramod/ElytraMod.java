package com.elytramod;

import net.fabricmc.api.ModInitializer;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Serverside-only mod that nerfs Elytra and adds a craftable version.
 *
 * Changes:
 *  1) End city ships no longer contain a vanilla Elytra in their item frame.
 *     Instead, they contain a Totem of Inventory (the consumable that keeps
 *     your inventory on death). This makes End cities worth exploring for
 *     the totem, but you can't just grab a free Elytra anymore.
 *
 *  2) Elytra can no longer be repaired in the anvil with anything except
 *     Phantom Membrane. Combining two Elytras is also blocked. Enchanting
 *     via anvil (books) is blocked too.
 *
 *  3) Elytra is craftable: a 3x3 pattern of Phantom Membrane (corners +
 *     bottom), String (edges), and a Nether Star (center). This makes
 *     Elytra a mid-to-late-game goal that requires killing the Wither
 *     AND collecting Phantom Membranes.
 *
 *  4) Phantom Membrane repairs are uncapped (no "Too Expensive" for elytra).
 *
 * The recipe is at data/elytramod/recipe/elytra.json.
 */
public class ElytraMod implements ModInitializer {

    public static final String MOD_ID = "elytramod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[elytramod] Elytra nerf active — craftable, no End ship loot, membrane-only repair");
    }

    /**
     * Create a Totem of Inventory stack — used by the End city ship mixin
     * to replace the Elytra in item frames.
     *
     * This is the same item as the one from totemmod, identified by the
     * TotemInventory NBT marker. We duplicate the creation logic here so
     * elytramod doesn't depend on totemmod at compile time (the NBT marker
     * is the contract between the two mods).
     */
    public static ItemStack createTotemOfInventory() {
        ItemStack stack = new ItemStack(Items.TOTEM_OF_UNDYING);

        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean("TotemInventory", true);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));

        stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("Тотем Сохранения Инвентаря")
                        .formatted(Formatting.AQUA, Formatting.BOLD));

        stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.literal("Расходник").formatted(Formatting.YELLOW),
                Text.literal("Сохраняет инвентарь при смерти").formatted(Formatting.GRAY),
                Text.literal("Тотем уничтожается").formatted(Formatting.GRAY)
        )));

        return stack;
    }
}
