package com.hellimp;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnLocationTypes;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.mob.VexEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.Heightmap;

/**
 * HellImp — мод "Бес" для Minecraft 1.21.11.
 *
 * Содержит:
 *   1. HellImpEntity — extends VexEntity, 20 HP, трезубец в руках, спавнится в Незере
 *   2. HeartOfDemon — предмет (Enchanted Book с NBT), 25% дроп с HellImp,
 *      ПКМ расширяет слоты аксессуаров с 6 до 7
 *   3. AccessoriesSystem — 6 слотов аксессуаров (расширяется до 7 сердцем),
 *      сохраняются между смертями
 *   4. /aks — команда для открытия GUI слотов
 *
 * Спавн Беса: NATURAL в Незере через BiomeModifications. Очень редкий —
 * вес спавна 1 (для сравнения у зомби ~100). ~0.5% от всех спавнов мобов
 * в Незере будут Бесами.
 */
public class HellImpMod implements net.fabricmc.api.ModInitializer {

    public static final String MOD_ID = "hellimp";
    public static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(MOD_ID);

    /** HellImp entity type — extends VexEntity. */
    public static final EntityType<HellImpEntity> HELL_IMP = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of(MOD_ID, "hell_imp"),
            EntityType.Builder.create(HellImpEntity::new, SpawnGroup.MONSTER)
                    .dimensions(0.4f, 0.8f) // standard Vex size
                    .build(RegistryKey.of(RegistryKeys.ENTITY_TYPE,
                            Identifier.of(MOD_ID, "hell_imp"))));

    /**
     * Initialize the mod.
     */
    @Override
    public void onInitialize() {
        // Register attributes for HellImp
        net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(
                HELL_IMP, HellImpEntity.createHellImpAttributes());

        // Register spawn restrictions — HellImp spawns on the ground in the Nether
        SpawnRestriction.register(HELL_IMP, SpawnLocationTypes.ON_GROUND,
                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                HellImpEntity::canHellImpSpawn);

        // Register natural spawn in Nether biomes via BiomeModifications
        net.fabricmc.fabric.api.biome.v1.BiomeModifications.addSpawn(
                net.fabricmc.fabric.api.biome.v1.BiomeSelectors.foundInTheNether(),
                SpawnGroup.MONSTER,
                HELL_IMP,
                // Very rare: weight 1 (for comparison, Zombie=100, Blaze=50)
                // This means ~1% of Nether monster spawns will be HellImps,
                // but combined with Nether's low spawn rate → effective 0.5%.
                1,   // weight
                1,   // minGroupSize
                1    // maxGroupSize
        );

        // Register accessories system
        AccessoriesSystem.register();

        // Register all accessories (can be placed in accessory slots)
        AccessoriesSystem.registerAccessory(ElytraCloak.NBT_KEY);
        AccessoriesSystem.registerAccessory(InfiniteFoodAccessory.NBT_KEY);
        AccessoriesSystem.registerAccessory(AntiGravityAccessory.NBT_KEY);

        // Register Heart of Demon use callback (ПКМ → +1 slot)
        HeartOfDemon.register();

        // Register /aks command
        HellImpCommands.register();

        // Register /hellimpgive admin command
        HellImpGiveCommand.register();

        LOGGER.info("[hellimp] Бес зарегистрирован — 20 HP, трезубец, спавн в Незере (редкий)");
        LOGGER.info("[hellimp] Система аксессуаров активна — 6 слотов (7 с Сердцем Демона)");
        LOGGER.info("[hellimp] Аксессуары: Элитовый Плащ, Бесконечная Еда, Антигравитация");
    }
}
