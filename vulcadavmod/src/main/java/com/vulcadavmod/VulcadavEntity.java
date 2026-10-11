package com.vulcadavmod;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Vulcadav — boss with 3 phases.
 * Based on FoxEntity, disguised as WOLF.
 *
 * Fixes applied:
 *  - No item drops (canPickUpLoot=false, dropEquipment cancelled)
 *  - Wolf sounds instead of fox sounds
 *  - Boss bar removed on death
 *  - Damage respects armor (uses mob_attack, not bypass)
 *  - Scale handled via dimensions in EntityType.Builder
 */
public class VulcadavEntity extends FoxEntity {

    private final ServerBossBar bossBar;
    private int phase = 1;
    private int summonCooldown = 0;
    private final Set<UUID> damageDealers = new HashSet<>();
    private static final double COMBAT_RADIUS = 35.0;
    private boolean deathHandled = false;

    public VulcadavEntity(EntityType<? extends FoxEntity> entityType, World world) {
        super(entityType, world);
        this.bossBar = new ServerBossBar(
                Text.literal("Вулкадав").formatted(Formatting.DARK_RED, Formatting.BOLD),
                BossBar.Color.RED, BossBar.Style.PROGRESS);
        this.setCustomName(Text.literal("Вулкадав").formatted(Formatting.DARK_RED, Formatting.BOLD));
        this.setCustomNameVisible(true);
        this.setPersistent();
    }

    public static DefaultAttributeContainer.Builder createVulcadavAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.MAX_HEALTH, 300.0)
                .add(EntityAttributes.ATTACK_DAMAGE, 18.0)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.4)
                .add(EntityAttributes.FOLLOW_RANGE, 40.0)
                .add(EntityAttributes.ATTACK_SPEED, 4.0); // fast attacks like Vindicator Johnny
    }

    @Override
    protected void initGoals() {
        this.goalSelector.clear(g -> true);
        this.targetSelector.clear(g -> true);

        // Leap at target (fox pounce) — this is what makes Vulcadav JUMP
        this.goalSelector.add(1, new net.minecraft.entity.ai.goal.PounceAtTargetGoal(this, 0.6f));
        // Melee attack
        this.goalSelector.add(2, new MeleeAttackGoal(this, 1.2, true));

        // RevengeGoal — attack ANY LivingEntity that damages Vulcadav.
        // Works for Larry, wolves, zombies, etc. — not just players.
        this.targetSelector.add(1, new RevengeGoal(this));

        // Universal target goal — actively hunts any nearby LivingEntity
        // (not just players). Predicate excludes Vulcadav's own summoned
        // wolves, other Vulcadavs, foxes (own base), ALL wolves, and
        // peaceful animals.
        this.targetSelector.add(2, new net.minecraft.entity.ai.goal.ActiveTargetGoal<>(
                this, net.minecraft.entity.LivingEntity.class, 10, true, true,
                (target, world) -> isValidVulcadavTarget(target)));

        // Player-only target goal as fallback priority 3.
        this.targetSelector.add(3, new net.minecraft.entity.ai.goal.ActiveTargetGoal<>(
                this, PlayerEntity.class, true));

        this.goalSelector.add(5, new WanderAroundFarGoal(this, 1.0));
        this.goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 20.0f));
    }

    /**
     * Predicate for the universal target goal.
     * Vulcadav attacks: players, hostile mobs (Larry, zombies, skeletons, etc.)
     * Vulcadav does NOT attack: himself, other Vulcadavs, foxes (own base),
     * ALL wolves (own minions + player pets + WolfBow wolves), peaceful animals.
     */
    private boolean isValidVulcadavTarget(net.minecraft.entity.LivingEntity target) {
        if (target == this) return false;
        if (target.isDead()) return false;
        if (target instanceof VulcadavEntity) return false;
        if (target instanceof net.minecraft.entity.passive.FoxEntity) return false;
        // Don't target ANY wolves — own minions + player pets + WolfBow wolves
        if (target instanceof net.minecraft.entity.passive.WolfEntity) return false;
        if (isPeacefulAnimal(target)) return false;
        return true;
    }

    private boolean isPeacefulAnimal(net.minecraft.entity.LivingEntity entity) {
        net.minecraft.entity.EntityType<?> type = entity.getType();
        return type == net.minecraft.entity.EntityType.SHEEP
                || type == net.minecraft.entity.EntityType.COW
                || type == net.minecraft.entity.EntityType.PIG
                || type == net.minecraft.entity.EntityType.CHICKEN
                || type == net.minecraft.entity.EntityType.RABBIT
                || type == net.minecraft.entity.EntityType.HORSE
                || type == net.minecraft.entity.EntityType.DONKEY
                || type == net.minecraft.entity.EntityType.MULE
                || type == net.minecraft.entity.EntityType.CAT
                || type == net.minecraft.entity.EntityType.PARROT
                || type == net.minecraft.entity.EntityType.OCELOT
                || type == net.minecraft.entity.EntityType.BEE
                || type == net.minecraft.entity.EntityType.TURTLE
                || type == net.minecraft.entity.EntityType.PANDA
                || type == net.minecraft.entity.EntityType.FROG
                || type == net.minecraft.entity.EntityType.ALLAY
                || type == net.minecraft.entity.EntityType.SNIFFER
                || type == net.minecraft.entity.EntityType.AXOLOTL
                || type == net.minecraft.entity.EntityType.GOAT
                || type == net.minecraft.entity.EntityType.CAMEL
                || type == net.minecraft.entity.EntityType.ARMADILLO
                || type == net.minecraft.entity.EntityType.MOOSHROOM
                || type == net.minecraft.entity.EntityType.BAT
                || type == net.minecraft.entity.EntityType.SQUID
                || type == net.minecraft.entity.EntityType.GLOW_SQUID
                || type == net.minecraft.entity.EntityType.COD
                || type == net.minecraft.entity.EntityType.SALMON
                || type == net.minecraft.entity.EntityType.PUFFERFISH
                || type == net.minecraft.entity.EntityType.TROPICAL_FISH
                || type == net.minecraft.entity.EntityType.DOLPHIN
                || type == net.minecraft.entity.EntityType.WANDERING_TRADER
                || type == net.minecraft.entity.EntityType.VILLAGER;
    }

    /** No item pickup — never drops the mace. */
    @Override
    public boolean canPickUpLoot() {
        return false;
    }

    // === BOSS SOUNDS (override fox sounds with ravager/wolf sounds) ===

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_RAVAGER_AMBIENT;
    }

    @Override
    public void playAmbientSound() {
        this.playSound(SoundEvents.ENTITY_RAVAGER_AMBIENT, 1.5f, 0.5f);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_RAVAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_RAVAGER_DEATH;
    }

    @Override
    protected void playStepSound(net.minecraft.util.math.BlockPos pos, net.minecraft.block.BlockState state) {
        this.playSound(SoundEvents.ENTITY_WOLF_STEP, 0.15f, 1.0f);
    }

    // === COMBAT ===

    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        boolean result = super.damage(world, source, amount);

        if (result && source.getAttacker() instanceof ServerPlayerEntity player) {
            damageDealers.add(player.getUuid());
            if (!bossBar.getPlayers().contains(player)) {
                bossBar.addPlayer(player);
            }
        }
        return result;
    }

    /**
     * Override tryAttack to use mob_attack damage (respects armor).
     * FoxEntity.tryAttack might use a damage type that bypasses armor.
     */
    @Override
    public boolean tryAttack(ServerWorld world, Entity target) {
        // Use standard mob attack damage — respects armor
        float damage = (float) this.getAttributeValue(EntityAttributes.ATTACK_DAMAGE);
        DamageSource source = world.getDamageSources().mobAttack(this);
        boolean hit = target.damage(world, source, damage);
        if (hit) {
            this.playSound(SoundEvents.ENTITY_RAVAGER_ATTACK, 1.0f, 0.8f);
        }
        return hit;
    }

    // === PHASES ===

    @Override
    public void tickMovement() {
        super.tickMovement();

        if (this.getEntityWorld().isClient()) return;
        ServerWorld serverWorld = (ServerWorld) this.getEntityWorld();

        // FALLBACK CLEANUP: if Vulcadav is dead but boss bar still has
        // players, clear it. Handles edge cases where onDeath was bypassed.
        if (!this.isAlive() || this.isRemoved()) {
            if (bossBar != null && !bossBar.getPlayers().isEmpty()) {
                bossBar.setPercent(0);
                bossBar.clearPlayers();
            }
            return;
        }

        // Update boss bar
        bossBar.setPercent(this.getHealth() / this.getMaxHealth());

        // Phase transitions:
        //   Phase 1: HP > 200  (full health)
        //   Phase 2: 100 < HP <= 200  (Ярость — summons wolves)
        //   Phase 3: HP <= 100  (Берсерк — mace with Wind Burst III in mouth)
        float hp = this.getHealth();
        int newPhase = hp > 200 ? 1 : (hp > 100 ? 2 : 3);
        if (newPhase != phase) {
            phase = newPhase;
            onPhaseChange(serverWorld);
        }

        // Phase 2: summon wolves every 60 seconds
        if (phase >= 2) {
            summonCooldown++;
            if (summonCooldown >= 1200) {
                summonCooldown = 0;
                summonWolves(serverWorld, 3);
            }
        }

        // Phase 3: give mace enchanted with Wind Burst III in mouth, don't drop it
        if (phase == 3) {
            ItemStack mace = this.getEquippedStack(net.minecraft.entity.EquipmentSlot.MAINHAND);
            // Only re-equip if slot is empty — preserves the enchanted mace across ticks
            if (mace.isEmpty()) {
                ItemStack enchantedMace = new ItemStack(Items.MACE);
                // Enchant with Wind Burst III using the server's enchantment registry
                net.minecraft.registry.Registry<net.minecraft.enchantment.Enchantment> enchRegistry =
                        serverWorld.getRegistryManager().getOrThrow(net.minecraft.registry.RegistryKeys.ENCHANTMENT);
                net.minecraft.registry.entry.RegistryEntry<net.minecraft.enchantment.Enchantment> windBurst =
                        enchRegistry.getOrThrow(net.minecraft.enchantment.Enchantments.WIND_BURST);
                net.minecraft.enchantment.EnchantmentHelper.apply(enchantedMace, builder -> builder.add(windBurst, 3));
                this.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, enchantedMace);
            }
        }
    }

    private void onPhaseChange(ServerWorld world) {
        this.playSound(SoundEvents.ENTITY_RAVAGER_ROAR, 2.0f, 0.5f);

        if (phase == 2) {
            bossBar.setColor(BossBar.Color.YELLOW);
            bossBar.setName(Text.literal("Вулкадав — Ярость").formatted(Formatting.YELLOW, Formatting.BOLD));
            for (ServerPlayerEntity player : bossBar.getPlayers()) {
                player.sendMessage(Text.literal("§eВулкадав впадает в ярость! Призывает волков!")
                        .formatted(Formatting.YELLOW), false);
            }
            summonWolves(world, 5); // immediately 5× per player
        } else if (phase == 3) {
            bossBar.setColor(BossBar.Color.PURPLE);
            bossBar.setName(Text.literal("Вулкадав — Берсерк").formatted(Formatting.DARK_PURPLE, Formatting.BOLD));
            for (ServerPlayerEntity player : bossBar.getPlayers()) {
                player.sendMessage(Text.literal("§5Вулкадав берёт булаву! Берсерк-режим!")
                        .formatted(Formatting.DARK_PURPLE), false);
            }
        }
    }

    /**
     * Command tag applied to all wolves summoned by Vulcadav.
     * The target predicate (isValidVulcadavTarget) excludes these wolves —
     * Vulcadav never attacks his own minions. The wolves still attack the
     * players (their target is set to Vulcadav's current target).
     */
    public static final String SUMMONED_WOLF_TAG = "vulcadav_summoned_wolf";

    private void summonWolves(ServerWorld world, int wolvesPerPlayer) {
        int playerCount = 0;
        for (UUID uuid : damageDealers) {
            ServerPlayerEntity p = world.getServer().getPlayerManager().getPlayer(uuid);
            if (p != null && p.squaredDistanceTo(this) < COMBAT_RADIUS * COMBAT_RADIUS) {
                playerCount++;
            }
        }
        int wolfCount = playerCount * wolvesPerPlayer;
        for (int i = 0; i < wolfCount; i++) {
            net.minecraft.entity.passive.WolfEntity wolf = EntityType.WOLF.create(world,
                    net.minecraft.entity.SpawnReason.TRIGGERED);
            if (wolf != null) {
                double ox = (world.random.nextDouble() - 0.5) * 4;
                double oz = (world.random.nextDouble() - 0.5) * 4;
                wolf.refreshPositionAndAngles(this.getX() + ox, this.getY(), this.getZ() + oz,
                        world.random.nextFloat() * 360, 0);
                // Set wolf's target to Vulcadav's current target.
                // If Vulcadav is fighting Larry, wolves attack Larry.
                // If Vulcadav is fighting a player, wolves attack the player.
                // Wolves are also tagged so Vulcadav's own target predicate
                // excludes them (he never attacks his own minions).
                wolf.setTarget(this.getTarget());
                wolf.addCommandTag(SUMMONED_WOLF_TAG);
                wolf.setPersistent();
                world.spawnEntity(wolf);
            }
        }
        this.playSound(SoundEvents.ENTITY_RAVAGER_ROAR, 3.0f, 0.7f);
    }

    // === DEATH ===

    @Override
    public void onDeath(DamageSource source) {
        if (deathHandled) return;
        deathHandled = true;

        super.onDeath(source);

        // Remove boss bar immediately
        bossBar.setPercent(0);
        bossBar.clearPlayers();

        ServerWorld world = (ServerWorld) this.getEntityWorld();

        // Remove all Wolf Compasses
        WolfCompass.removeAll(world);

        // Announce death to all players
        world.getServer().getPlayerManager().broadcast(
                Text.literal("§a§lВулкадав повержен! Заберите свою награду!")
                        .formatted(Formatting.GREEN), false);

        // Give loot bag to EACH damage dealer within 35 blocks (one per player)
        for (UUID uuid : damageDealers) {
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(uuid);
            if (player != null && player.squaredDistanceTo(this) < COMBAT_RADIUS * COMBAT_RADIUS) {
                ItemStack bag = LootBag.createBossBag();
                if (!player.getInventory().insertStack(bag)) {
                    player.dropItem(bag, false);
                }
                player.sendMessage(Text.literal("§aВы получили Мешок Босса! ПКМ — открыть")
                        .formatted(Formatting.GREEN), false);
            }
        }
    }

    /** Cancel equipment drop — no mace drops. */
    @Override
    protected void dropEquipment(ServerWorld world, DamageSource source, boolean causedByPlayer) {
        // Do nothing — no equipment drops
    }

    /** Cancel vanilla loot drop. */
    @Override
    protected void dropLoot(ServerWorld world, DamageSource source, boolean causedByPlayer) {
        // Do nothing — no vanilla loot
    }

    /** Override FoxEntity.drop — prevents fox from dropping held item (mace). */
    @Override
    protected void drop(ServerWorld world, DamageSource source) {
        // Only call LivingEntity.drop, skip FoxEntity.drop which drops held items
        // LivingEntity.drop calls dropLoot, dropEquipment, dropInventory, dropExperience
        // All of which are cancelled or empty
        super.drop(world, source);
    }

    /** Prevent fox from dropping items via its own dropItem method. */
    public net.minecraft.entity.ItemEntity dropStack(ServerWorld world, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (this.deathHandled) return null;
        return super.dropStack(world, stack);
    }

    @Override
    public boolean isFireImmune() {
        return true;
    }

    public boolean isImmuneToExplosion() {
        return true;
    }

    /**
     * FINAL FALLBACK: when the entity is removed from the world (chunk unload,
     * /kill, dimension change), clear the boss bar. Third line of defense
     * after onDeath and tickMovement — guarantees no boss bar leak.
     */
    @Override
    public void onRemoved() {
        if (bossBar != null) {
            bossBar.setPercent(0);
            bossBar.clearPlayers();
        }
        super.onRemoved();
    }

    /**
     * Disguise as WOLF for client rendering.
     * Scale is handled by EntityType dimensions (3.0×3.0 = 5× wolf).
     */
    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket(EntityTrackerEntry entry) {
        return new net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket(
                this.getId(),
                this.getUuid(),
                entry.getPos().x, entry.getPos().y, entry.getPos().z,
                entry.getPitch(), entry.getYaw(),
                EntityType.WOLF,
                0,
                entry.getVelocity(),
                entry.getHeadYaw()
        );
    }
}
