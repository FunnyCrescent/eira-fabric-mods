package com.hellimp;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.VexEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.dimension.DimensionOptions;

/**
 * HellImp (Бес) — адский вредин, extends VexEntity.
 *
 * Характеристики:
 *   - 20 HP (стандартный Vex имеет 14 HP)
 *   - Трезубец в руках (как у drowned)
 *   - Спавнится естественно в Незере (через BiomeModifications, очень редкий)
 *   - Дроп: 25% шанс Heart of Demon
 *
 * Bedrock-совместимость: VexEntity — ванильный моб, Bedrock видит его через Geyser.
 * Disguise через createSpawnPacket() чтобы Bedrock видел обычного Vex.
 */
public class HellImpEntity extends VexEntity {

    public HellImpEntity(EntityType<? extends VexEntity> entityType, World world) {
        super(entityType, world);
    }

    public static DefaultAttributeContainer.Builder createHellImpAttributes() {
        return VexEntity.createVexAttributes()
                .add(EntityAttributes.MAX_HEALTH, 20.0)
                .add(EntityAttributes.ATTACK_DAMAGE, 5.0);
    }

    @Override
    protected void initGoals() {
        super.initGoals();
        // VexEntity has its own goals (charge at target, track owner).
        // We keep them — HellImp behaves like a normal Vex but with a trident.
    }

    @Override
    public void tick() {
        super.tick();
        // Ensure trident is always in main hand
        ItemStack mainHand = this.getEquippedStack(EquipmentSlot.MAINHAND);
        if (mainHand.isEmpty() || !mainHand.isOf(Items.TRIDENT)) {
            this.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
        }
    }

    @Override
    public boolean isFireImmune() {
        return true; // Hell imps are immune to fire (they live in the Nether)
    }

    /**
     * Spawn predicate — HellImp can only spawn in the Nether.
     * Called by SpawnRestrictions when the game checks if a HellImp can spawn
     * at a given position.
     */
    public static boolean canHellImpSpawn(EntityType<HellImpEntity> type, WorldAccess world,
                                           SpawnReason spawnReason, BlockPos pos, Random random) {
        // Only spawn in the Nether
        if (world instanceof net.minecraft.server.world.ServerWorld sw) {
            if (!sw.getRegistryKey().equals(DimensionOptions.NETHER)) return false;
        }
        // Standard mob spawn checks: not in lava, not in blocks, etc.
        return world.getBlockState(pos.down()).isOpaque() && pos.getY() > 0;
    }

    /**
     * Disguise as vanilla VEX for client rendering.
     * Bedrock clients don't know about our custom EntityType, so we send
     * a spawn packet claiming this entity is a Vex.
     */
    @Override
    public net.minecraft.network.packet.Packet<net.minecraft.network.listener.ClientPlayPacketListener> createSpawnPacket(
            net.minecraft.server.network.EntityTrackerEntry entry) {
        return new net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket(
                this.getId(),
                this.getUuid(),
                entry.getPos().x, entry.getPos().y, entry.getPos().z,
                entry.getPitch(), entry.getYaw(),
                EntityType.VEX,
                0,
                entry.getVelocity(),
                entry.getHeadYaw()
        );
    }
}
