package com.larrymod;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * Larry — a custom hostile mob (NOT a skeleton override).
 *
 * Registered as a new EntityType "larrymod:larry". On the server side,
 * this is a full custom entity with its own AI, loot, HP, attributes.
 *
 * On the client side (Java + Bedrock via Geyser), a mixin on
 * EntitySpawnS2CPacket replaces the entity type with minecraft:skeleton
 * so the client renders a skeleton — no client mod required.
 *
 * Stats:
 *  - HP: 2 (1 heart)
 *  - Damage: 3 (skeleton melee)
 *  - Speed: 0.35 (faster than zombie 0.23)
 *  - Fire immune
 *  - Doesn't burn in sunlight
 *
 * AI:
 *  - Attacks ALL mobs except peaceful animals
 *  - 1.5× melee speed
 *  - Each attack spawns a clone (up to 8 per group)
 *
 * Drops:
 *  - 0-8 bones (only when last Larry in group dies)
 *  - 2.5% chance for legendary bone (if killed by sword, group grew to 3+)
 */
public class LarryEntity extends HostileEntity {

    private UUID groupId;
    private boolean groupGrew;
    private UUID lastAttackerUuid;
    private boolean lastAttackerUsedSword;
    private static final java.util.Map<UUID, net.minecraft.entity.boss.ServerBossBar> groupBossBars = new java.util.concurrent.ConcurrentHashMap<>();

    public LarryEntity(EntityType<? extends HostileEntity> entityType, World world) {
        super(entityType, world);
        this.groupId = UUID.randomUUID();
        this.groupGrew = false;
        this.lastAttackerUuid = null;
        this.lastAttackerUsedSword = false;
    }

    public static DefaultAttributeContainer.Builder createLarryAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.MAX_HEALTH, 8.0)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.25)
                .add(EntityAttributes.ATTACK_DAMAGE, 3.0)
                .add(EntityAttributes.FOLLOW_RANGE, 16.0)
                .add(EntityAttributes.ATTACK_SPEED, 4.0); // fast attacks like Vindicator Johnny
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(2, new MeleeAttackGoal(this, 1.0, true));
        this.goalSelector.add(3, new FollowGroupGoal(this)); // stay together
        this.targetSelector.add(1, new RevengeGoal(this));
        this.targetSelector.add(2, new ActiveTargetGoal<>(
                this, LivingEntity.class, 10, true, true,
                (target, world) -> isValidTarget(target)));
        this.goalSelector.add(5, new WanderAroundFarGoal(this, 1.0));
        this.goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
        this.goalSelector.add(6, new LookAroundGoal(this));
    }

    @Override
    public boolean isFireImmune() {
        return true;
    }

    /** Cancel equipment drop — no stone sword drops. */
    @Override
    protected void dropEquipment(ServerWorld world, DamageSource source, boolean causedByPlayer) {
        // Do nothing
    }

    /** Use mob_attack damage (respects armor). */
    @Override
    public boolean tryAttack(ServerWorld world, Entity target) {
        float damage = (float) this.getAttributeValue(EntityAttributes.ATTACK_DAMAGE);
        DamageSource source = world.getDamageSources().mobAttack(this);
        boolean hit = target.damage(world, source, damage);
        if (hit && !world.isClient()) {
            spawnClone(world);
        }
        return hit;
    }

    /** Update group boss bar every tick. */
    @Override
    public void tickMovement() {
        super.tickMovement();
        if (this.getEntityWorld().isClient()) return;
        ServerWorld serverWorld = (ServerWorld) this.getEntityWorld();

        // Get or create boss bar for this group
        net.minecraft.entity.boss.ServerBossBar bossBar = groupBossBars.get(groupId);
        if (bossBar == null) {
            bossBar = new net.minecraft.entity.boss.ServerBossBar(
                    net.minecraft.text.Text.literal("Ларри × 1").formatted(net.minecraft.util.Formatting.WHITE),
                    net.minecraft.entity.boss.BossBar.Color.WHITE,
                    net.minecraft.entity.boss.BossBar.Style.PROGRESS);
            groupBossBars.put(groupId, bossBar);
        }

        // Count living Larrys in group
        long count = serverWorld.getEntitiesByType(LarryMod.LARRY,
                l -> l.isAlive() && l.groupId.equals(this.groupId)).size();

        // Update boss bar: show count out of 8
        bossBar.setName(net.minecraft.text.Text.literal("Ларри × " + count)
                .formatted(net.minecraft.util.Formatting.WHITE));
        bossBar.setPercent((float) count / LarryMod.MAX_GROUP_SIZE);

        // Add nearby players to boss bar
        for (net.minecraft.server.network.ServerPlayerEntity p : serverWorld.getPlayers(p -> p.squaredDistanceTo(this) < 64*64)) {
            if (!bossBar.getPlayers().contains(p)) {
                bossBar.addPlayer(p);
            }
        }
    }

    /** Remove boss bar when last Larry dies. */
    @Override
    public void onDeath(DamageSource source) {
        super.onDeath(source);
        if (this.getEntityWorld().isClient()) return;

        // Check if this was the last Larry
        ServerWorld serverWorld = (ServerWorld) this.getEntityWorld();
        long remaining = serverWorld.getEntitiesByType(LarryMod.LARRY,
                l -> l != this && l.isAlive() && l.groupId.equals(this.groupId)).size();

        if (remaining == 0) {
            net.minecraft.entity.boss.ServerBossBar bossBar = groupBossBars.remove(groupId);
            if (bossBar != null) {
                bossBar.setPercent(0);
                bossBar.clearPlayers();
            }
        }
    }

    /**
     * Override createSpawnPacket to send EntityType.SKELETON to clients.
     * This makes Java vanilla and Bedrock (via Geyser) render Larry as a
     * skeleton — no client mod required.
     */
    @Override
    public net.minecraft.network.packet.Packet<net.minecraft.network.listener.ClientPlayPacketListener> createSpawnPacket(EntityTrackerEntry entry) {
        return new EntitySpawnS2CPacket(
                this.getId(),
                this.getUuid(),
                entry.getPos().x,
                entry.getPos().y,
                entry.getPos().z,
                entry.getPitch(),
                entry.getYaw(),
                EntityType.SKELETON,  // ← disguise as skeleton
                0,
                entry.getVelocity(),
                entry.getHeadYaw()
        );
    }

    /**
     * Spawn a clone of this Larry, up to MAX_GROUP_SIZE per group.
     */
    private void spawnClone(ServerWorld world) {
        long count = world.getEntitiesByType(LarryMod.LARRY,
                larry -> larry.groupId.equals(this.groupId)).size();
        if (count >= LarryMod.MAX_GROUP_SIZE) return;

        LarryEntity clone = LarryMod.LARRY.create(world, SpawnReason.TRIGGERED);
        if (clone == null) return;

        double ox = (world.random.nextDouble() - 0.5) * 2;
        double oz = (world.random.nextDouble() - 0.5) * 2;
        clone.refreshPositionAndAngles(
                this.getX() + ox, this.getY(), this.getZ() + oz,
                this.getYaw(), this.getPitch());
        clone.groupId = this.groupId;
        clone.groupGrew = true;
        clone.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
        clone.setCustomName(Text.literal("Ларри").formatted(Formatting.WHITE));
        clone.setCustomNameVisible(true);
        clone.setPersistent();

        // Mark group as grew (for legendary bone drop)
        this.groupGrew = true;

        world.spawnEntity(clone);
    }

    /**
     * Valid target: everything EXCEPT peaceful animals and same-group Larrys.
     * Villagers ARE attacked (they are NOT peaceful animals).
     */
    private boolean isValidTarget(LivingEntity target) {
        if (target == this) return false;
        if (target.isDead()) return false;
        if (isPeacefulAnimal(target)) return false;
        if (target instanceof LarryEntity other && other.groupId.equals(this.groupId)) {
            return false;
        }
        return true;
    }

    private boolean isPeacefulAnimal(LivingEntity entity) {
        EntityType<?> type = entity.getType();
        return type == EntityType.SHEEP || type == EntityType.COW || type == EntityType.PIG
                || type == EntityType.CHICKEN || type == EntityType.RABBIT
                || type == EntityType.HORSE || type == EntityType.DONKEY
                || type == EntityType.MULE || type == EntityType.CAT
                || type == EntityType.PARROT || type == EntityType.OCELOT
                || type == EntityType.FOX || type == EntityType.BEE
                || type == EntityType.TURTLE || type == EntityType.PANDA
                || type == EntityType.FROG || type == EntityType.ALLAY
                || type == EntityType.SNIFFER || type == EntityType.AXOLOTL
                || type == EntityType.GOAT || type == EntityType.CAMEL
                || type == EntityType.ARMADILLO || type == EntityType.MOOSHROOM
                || type == EntityType.BAT || type == EntityType.SQUID
                || type == EntityType.GLOW_SQUID || type == EntityType.COD
                || type == EntityType.SALMON || type == EntityType.PUFFERFISH
                || type == EntityType.TROPICAL_FISH || type == EntityType.DOLPHIN
                || type == EntityType.WANDERING_TRADER;
    }

    /** Track last attacker for loot distribution. */
    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        if (source.getAttacker() instanceof net.minecraft.entity.player.PlayerEntity player) {
            lastAttackerUuid = player.getUuid();
            net.minecraft.item.ItemStack weapon = player.getMainHandStack();
            String itemId = weapon.getItem().toString();
            lastAttackerUsedSword = itemId.contains("sword") || itemId.contains("bone");
        }
        return super.damage(world, source, amount);
    }

    public UUID getLastAttackerUuid() { return lastAttackerUuid; }
    public boolean wasLastAttackerUsingSword() { return lastAttackerUsedSword; }

    public UUID getGroupId() { return groupId; }
    public boolean didGroupGrow() { return groupGrew; }
    public void setGroupId(UUID groupId) { this.groupId = groupId; }
    public void setGroupGrew(boolean grew) { this.groupGrew = grew; }

    @Override
    public void writeCustomData(net.minecraft.storage.WriteView view) {
        super.writeCustomData(view);
        view.putString("groupId", groupId.toString());
        view.putBoolean("groupGrew", groupGrew);
    }

    @Override
    public void readCustomData(net.minecraft.storage.ReadView view) {
        super.readCustomData(view);
        try {
            groupId = UUID.fromString(view.getString("groupId", UUID.randomUUID().toString()));
        } catch (Exception e) {
            groupId = UUID.randomUUID();
        }
        groupGrew = view.getBoolean("groupGrew", false);
    }
}
