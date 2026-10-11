package com.leomordmod;

import com.crownmod.CrownMod;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.SkeletonHorseEntity;
import net.minecraft.entity.mob.WitherSkeletonEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.equipment.trim.ArmorTrim;
import net.minecraft.item.equipment.trim.ArmorTrimMaterials;
import net.minecraft.item.equipment.trim.ArmorTrimPatterns;
import net.minecraft.item.equipment.trim.ArmorTrimMaterial;
import net.minecraft.item.equipment.trim.ArmorTrimPattern;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Unit;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Leomord — Wither Skeleton boss with 3 phases.
 *
 * PHASE 1 — Rider (Horse HP 100):
 *   - Leomord spawns riding a LeomordHorseEntity (SkeletonHorse with custom jump).
 *   - Leomord is fully invulnerable while the horse is alive.
 *   - Leomord attacks players with Netherite Spear from the saddle.
 *   - Horse does "Fox Jump" — on landing: knock up players in 4-block radius,
 *     ignite blocks in 4-block radius, deal contact damage in 4-block radius.
 *
 * PHASE 2 — Foot fight (Leomord HP 200):
 *   - Triggered when horse dies. Leomord dismounts, HP activates at 200,
 *     vulnerability restored, BossBar visible.
 *   - Melee with Netherite Spear.
 *   - Periodically casts "Get Over Here" — projectile pulls the player to
 *     the boss. First spear hit on a pulled player within 3s of pull = x2
 *     critical damage.
 *
 * PHASE 3 — Artillery (Leomord HP ≤ 50% = 100):
 *   - Leomord swaps to Crossbow. Attack cycle:
 *     1. Reload (25 ticks = 1.25s): stand still, apply Resistance 3 (50%
 *        damage reduction), Armor Stand with arrow above head as visual
 *        indicator.
 *     2. Fire: fly up 7 blocks, remove Resistance, multi-shot at every
 *        player in 30-block radius. Each arrow is a LeomordArrow entity.
 *
 * DEATH SEQUENCE (at HP = 0):
 *   - Leomord doesn't die immediately. 4 LeomordHorseEntity fall from the
 *     sky at Y+20 around Leomord's XZ.
 *   - On landing: knock up all players in 16-block radius, then horses
 *     die instantly.
 *   - After all 4 horses landed, Leomord disappears and spawns
 *     LeomordLootBag at his position.
 *
 * EQUIPMENT:
 *   - Head: crownmod:royal_crown (Royal Crown from Eira's crownmod)
 *   - Body/Legs/Boots: Netherite armor with Vex trim + Gold material
 *   - Negative armor attribute modifiers so total protection matches
 *     vanilla Iron set (armor=15, toughness=0, knockback_resistance=0)
 *   - Phase 1-2 mainhand: Leomord Spear (Netherite Sword with NBT)
 *   - Phase 3 mainhand: vanilla Crossbow
 */
public class LeomordEntity extends WitherSkeletonEntity {

    // === Boss Bar ===
    private final ServerBossBar bossBar;
    private int phase = 1;
    private boolean deathHandled = false;
    private int deathSequenceStage = 0; // 0 = none, 1 = horses falling, 2 = done
    private int deathTimeoutTicks = 0; // safety timeout if horses never land
    private static final int DEATH_TIMEOUT_MAX = 200; // 10s

    // === Combat tracking ===
    private static final double COMBAT_RADIUS = 50.0;
    private final Set<UUID> damageDealers = new HashSet<>();

    // === Phase 2: Get Over Here ===
    private int getOverHereCooldown = 100; // initial delay before first pull
    private static final int GET_OVER_HERE_INTERVAL = 240; // 12s
    private final java.util.Map<UUID, Long> pulledPlayers = new java.util.HashMap<>(); // player → expiry tick

    // === Phase 3: Crossbow cycle ===
    // State machine:
    //   RELOAD         → stand on ground, Resistance, arrow indicator, 25 ticks
    //   SPECIAL_FIRE   → fly up 7 blocks, multi-shot LeomordArrow at all players
    //   MULTI_PULL     → spawn 25 Get Over Here arrows above boss, aimed at
    //                    spread of points around each player (easier to hit)
    //                  → if any pull hit: go back to RELOAD
    //                  → if no pull hit: go to SPRAY_MODE
    //   SPRAY_MODE     → 5 seconds of running + shooting spray arrows
    //                    (explosion only, no stun, no fireballs)
    //                  → after 5 seconds: go back to RELOAD
    private enum Phase3State { RELOAD, SPECIAL_FIRE, MULTI_PULL_WAIT, SPRAY_MODE }
    private static final int CROSSBOW_RELOAD_TICKS = 25; // 1.25s (vanilla unenchanted)
    private static final double PHASE_3_HP_THRESHOLD = 100.0;
    /** Made public for LeomordProjectiles to reference. */
    public static final double AGGRO_RADIUS = 30.0;
    private Phase3State p3State = Phase3State.RELOAD;
    private int p3Timer = 0;
    private boolean hasFlownUp = false;
    private int pullHitsThisMultiShot = 0;
    private static final int SPRAY_DURATION_TICKS = 100; // 5 seconds
    private static final int MULTI_PULL_WAIT_TICKS = 60; // 3 seconds for arrows to hit or miss
    private int sprayShotCooldown = 0;
    /** Armor Stand holding an arrow above the boss's head — visual indicator during reload. */
    private UUID arrowIndicatorArmorStand = null;

    // === Armor attribute nerf IDs (so Netherite set totals match Iron set) ===
    // In 1.21.11 EntityAttributeModifier is a record taking (Identifier, double, Operation).
    private static final net.minecraft.util.Identifier ARMOR_NERF_ID    = net.minecraft.util.Identifier.of("leomordmod", "armor_nerf");
    private static final net.minecraft.util.Identifier TOUGH_NERF_ID    = net.minecraft.util.Identifier.of("leomordmod", "toughness_nerf");
    private static final net.minecraft.util.Identifier KB_NERF_ID       = net.minecraft.util.Identifier.of("leomordmod", "kb_nerf");

    public LeomordEntity(EntityType<? extends WitherSkeletonEntity> entityType, World world) {
        super(entityType, world);
        this.bossBar = new ServerBossBar(
                Text.literal("Леоморд").formatted(Formatting.DARK_RED, Formatting.BOLD),
                BossBar.Color.RED, BossBar.Style.PROGRESS);
        this.setCustomName(Text.literal("Леоморд").formatted(Formatting.DARK_RED, Formatting.BOLD));
        this.setCustomNameVisible(true);
        this.setPersistent();
    }

    public static net.minecraft.entity.attribute.DefaultAttributeContainer.Builder createLeomordAttributes() {
        return AbstractSkeletonEntity.createAbstractSkeletonAttributes()
                .add(EntityAttributes.MAX_HEALTH, 200.0)
                .add(EntityAttributes.ATTACK_DAMAGE, 16.0)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.32)
                .add(EntityAttributes.FOLLOW_RANGE, 50.0)
                .add(EntityAttributes.KNOCKBACK_RESISTANCE, 0.0);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.clear(g -> true);
        this.targetSelector.clear(g -> true);

        // Melee with spear (used in phases 1 and 2)
        this.goalSelector.add(1, new net.minecraft.entity.ai.goal.MeleeAttackGoal(this, 1.1, true));
        this.targetSelector.add(1, new net.minecraft.entity.ai.goal.RevengeGoal(this));
        // Universal target goal — actively hunts any LivingEntity (players, Larrys,
        // zombies, skeletons, etc.) EXCLUDING creative-mode players (per user spec).
        this.targetSelector.add(2, new net.minecraft.entity.ai.goal.ActiveTargetGoal<>(
                this, net.minecraft.entity.LivingEntity.class, 10, true, true,
                (target, world) -> isValidLeomordTarget(target)));

        this.goalSelector.add(5, new net.minecraft.entity.ai.goal.WanderAroundFarGoal(this, 0.9));
        this.goalSelector.add(6, new net.minecraft.entity.ai.goal.LookAtEntityGoal(this, PlayerEntity.class, 32.0f));
    }

    /**
     * Predicate for valid Leomord target — same logic as LeomordProjectiles.findEnemiesInRadius.
     * Excludes: self, dead, spectators, creative-mode players, peaceful animals,
     * AND LeomordHorseEntity instances (so the boss never attacks his own death horses
     * left over from a previous Leomord's death sequence).
     */
    private boolean isValidLeomordTarget(net.minecraft.entity.LivingEntity target) {
        if (target == this) return false;
        if (target.isDead()) return false;
        if (target.isSpectator()) return false;
        if (target instanceof PlayerEntity p && p.isCreative()) return false;
        // Don't target LeomordHorseEntity (Phase 1 horse + death horses)
        if (target instanceof LeomordHorseEntity) return false;
        // Skip peaceful animals
        net.minecraft.entity.EntityType<?> type = target.getType();
        if (type == net.minecraft.entity.EntityType.SHEEP
                || type == net.minecraft.entity.EntityType.COW
                || type == net.minecraft.entity.EntityType.PIG
                || type == net.minecraft.entity.EntityType.CHICKEN
                || type == net.minecraft.entity.EntityType.HORSE
                || type == net.minecraft.entity.EntityType.DONKEY
                || type == net.minecraft.entity.EntityType.MULE
                || type == net.minecraft.entity.EntityType.VILLAGER
                || type == net.minecraft.entity.EntityType.WANDERING_TRADER
                || type == net.minecraft.entity.EntityType.SKELETON_HORSE
                || type == net.minecraft.entity.EntityType.ZOMBIE_HORSE) return false;
        return true;
    }

    // === COMBAT — damage tracking + phase 1 invulnerability ===

    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        // Phase 1 invulnerability — Leomord cannot be hurt while horse is alive
        if (phase == 1 && deathSequenceStage == 0) {
            // Still knock back briefly for feedback, but no damage
            return false;
        }
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
     * Override tryAttack — Phase 1-2 melee with spear.
     * If target is a player who was pulled within the combo window,
     * apply x2 critical damage.
     */
    @Override
    public boolean tryAttack(ServerWorld world, Entity target) {
        float baseDamage = (float) this.getAttributeValue(EntityAttributes.ATTACK_DAMAGE);
        boolean isCombo = false;
        if (target instanceof PlayerEntity player) {
            Long expiry = pulledPlayers.get(player.getUuid());
            long now = world.getServer().getTicks();
            if (expiry != null && now < expiry) {
                // x2 combo
                baseDamage *= 2.0f;
                isCombo = true;
                pulledPlayers.remove(player.getUuid()); // combo consumed
            }
        }
        DamageSource source = world.getDamageSources().mobAttack(this);
        boolean hit = target.damage(world, source, baseDamage);
        if (hit) {
            this.playSound(SoundEvents.ENTITY_WITHER_SKELETON_HURT, 1.5f, 0.5f);
            if (isCombo) {
                this.playSound(SoundEvents.ENTITY_WITHER_HURT, 2.0f, 0.7f);
                // Spawn crit particles
                world.spawnParticles(net.minecraft.particle.ParticleTypes.CRIT,
                        target.getX(), target.getY() + 1, target.getZ(), 20, 0.5, 1.0, 0.5, 0.3);
            }
        }
        return hit;
    }

    // === STUN / PULL API (called by LeomordProjectiles) ===

    public void markPulled(UUID playerId, long expiryTick) {
        pulledPlayers.put(playerId, expiryTick);
    }

    // === HORSE DEATH CALLBACK (phase 1 → phase 2 transition) ===

    public void onHorseDeath(ServerWorld world) {
        if (phase != 1) return;
        startPhase2(world);
    }

    // === TICK MOVEMENT — main phase logic ===

    @Override
    public void tickMovement() {
        super.tickMovement();
        if (this.getEntityWorld().isClient()) return;
        ServerWorld sw = (ServerWorld) this.getEntityWorld();

        // === Visual fire — set fire ticks at end so they remain >0 for rendering ===
        // WitherSkeleton is fire-immune so tickFire() clears them next tick, but
        // setting them here at the end of tickMovement means the renderer (which
        // runs between ticks) sees fireTicks>0 and renders the burning effect.
        this.setFireTicks(2);

        // === Fallback BossBar cleanup ===
        if (!this.isAlive() || this.isRemoved()) {
            if (bossBar != null && !bossBar.getPlayers().isEmpty()) {
                bossBar.setPercent(0);
                bossBar.clearPlayers();
            }
            return;
        }

        // === Death sequence ticking ===
        if (deathSequenceStage > 0) {
            tickDeathSequence(sw);
            return; // don't run normal phase logic during death sequence
        }

        // === Recovery: if boss is in phase 1 but was never equipped (e.g.,
        //     onSummoned failed earlier, or boss was reloaded from a save
        //     after a server crash), apply equipment now. Don't spawn a new
        //     horse if the boss is already riding one (vanilla restored the
        //     riding relationship from NBT).
        if (phase == 1 && !equipmentApplied) {
            try {
                applyArmorNerfs();
                applyEquipmentIfNeeded(sw);
            } catch (Throwable t) {
                LeomordMod.LOGGER.error("[leomordmod] Recovery applyEquipment failed: {}", t.toString());
            }
        }
        // If boss should be in phase 1 but has no horse, spawn one.
        if (phase == 1 && !this.hasVehicle() && equipmentApplied) {
            try {
                LeomordHorseEntity horse = LeomordMod.LEOMORD_HORSE.create(sw,
                        net.minecraft.entity.SpawnReason.TRIGGERED);
                if (horse != null) {
                    horse.refreshPositionAndAngles(this.getX(), this.getY(), this.getZ(),
                            this.getYaw(), 0);
                    sw.spawnEntity(horse);
                    this.startRiding(horse, true, true);
                }
            } catch (Throwable t) {
                LeomordMod.LOGGER.error("[leomordmod] Recovery horse spawn failed: {}", t.toString());
            }
        }

        // === Phase 1: invulnerable rider — boss bar shows horse HP ===
        if (phase == 1) {
            Entity vehicle = this.getVehicle();
            if (vehicle instanceof LeomordHorseEntity horse) {
                bossBar.setName(Text.literal("Леоморд — Всадник")
                        .formatted(Formatting.RED, Formatting.BOLD));
                bossBar.setColor(BossBar.Color.RED);
                bossBar.setPercent(Math.max(0.0f, horse.getHealth() / Math.max(1.0f, horse.getMaxHealth())));
                // Auto-add nearby players
                autoAddPlayers(sw);
            } else if (equipmentApplied) {
                // Horse died but onHorseDeath didn't fire (e.g., horse despawned)
                startPhase2(sw);
            } else {
                // No horse AND equipment not yet applied — wait for recovery
                // tick (next tick) to apply equipment.
                bossBar.setName(Text.literal("Леоморд — пробуждение...")
                        .formatted(Formatting.GRAY, Formatting.BOLD));
                bossBar.setColor(BossBar.Color.WHITE);
                bossBar.setPercent(1.0f);
                autoAddPlayers(sw);
            }
            return;
        }

        // === Phase 2/3: update boss bar with Leomord HP ===
        bossBar.setPercent(this.getHealth() / this.getMaxHealth());
        autoAddPlayers(sw);

        // === Phase 3 transition ===
        if (phase == 2 && this.getHealth() <= PHASE_3_HP_THRESHOLD) {
            startPhase3(sw);
        }

        // === Phase 2: Get Over Here ===
        if (phase == 2) {
            getOverHereCooldown--;
            if (getOverHereCooldown <= 0) {
                getOverHereCooldown = GET_OVER_HERE_INTERVAL;
                castGetOverHere(sw);
            }
        }

        // === Phase 3: Crossbow cycle ===
        if (phase == 3) {
            tickPhase3Cycle(sw);
        }
    }

    // === PHASE TRANSITIONS ===

    private void startPhase2(ServerWorld world) {
        phase = 2;
        this.setHealth(200.0f);
        // Apply armor attribute nerfs so Netherite set equals Iron set
        applyArmorNerfs();

        bossBar.setName(Text.literal("Леоморд — Пеший Бой")
                .formatted(Formatting.YELLOW, Formatting.BOLD));
        bossBar.setColor(BossBar.Color.YELLOW);

        // Equip spear
        this.equipStack(EquipmentSlot.MAINHAND, LeomordSpear.createBossSpear());

        // Roar + announcement
        this.playSound(SoundEvents.ENTITY_WITHER_DEATH, 2.0f, 0.5f);
        for (ServerPlayerEntity p : bossBar.getPlayers()) {
            p.sendMessage(Text.literal("§e§lЛеоморд спешивается! Берегись Копья и «Get Over Here»!")
                    .formatted(Formatting.YELLOW), false);
        }
    }

    private void startPhase3(ServerWorld world) {
        phase = 3;
        bossBar.setName(Text.literal("Леоморд — Артиллерия")
                .formatted(Formatting.DARK_PURPLE, Formatting.BOLD));
        bossBar.setColor(BossBar.Color.PURPLE);

        // Swap to crossbow
        this.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
        this.playSound(SoundEvents.ENTITY_WITHER_SHOOT, 2.0f, 0.5f);

        for (ServerPlayerEntity p : bossBar.getPlayers()) {
            p.sendMessage(Text.literal("§5§lЛеоморд берёт Арбалет! Артиллерийский режим!")
                    .formatted(Formatting.DARK_PURPLE), false);
        }
    }

    // === PHASE 2: Get Over Here ===

    private void castGetOverHere(ServerWorld world) {
        // Target the closest enemy (player OR Larry/mob) in aggro range.
        // Per user spec: "он должен атаковать их [Ларри]" — target any LivingEntity
        // except creative-mode players.
        LivingEntity target = findClosestEnemy(world, AGGRO_RADIUS);
        if (target == null) return;
        LeomordProjectiles.spawnGetOverHere(world, this, target);
        this.playSound(SoundEvents.ENTITY_WITHER_SHOOT, 1.5f, 0.6f);
        for (ServerPlayerEntity p : bossBar.getPlayers()) {
            p.sendMessage(Text.literal("§c§lGet Over Here!").formatted(Formatting.RED), true);
        }
    }

    // === PHASE 3: Crossbow cycle (state machine) ===

    private void tickPhase3Cycle(ServerWorld world) {
        switch (p3State) {
            case RELOAD -> tickReload(world);
            case SPECIAL_FIRE -> tickSpecialFire(world);
            case MULTI_PULL_WAIT -> tickMultiPullWait(world);
            case SPRAY_MODE -> tickSprayMode(world);
        }
    }

    /**
     * RELOAD — boss stands on ground, gets Resistance 3, arrow indicator above
     * head, for 25 ticks (1.25s — vanilla unenchanted crossbow reload time).
     */
    private void tickReload(ServerWorld world) {
        if (p3Timer == 0) {
            // First tick of reload — set up
            this.getNavigation().stop();
            this.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.RESISTANCE, CROSSBOW_RELOAD_TICKS + 5, 3, false, false, false));
            spawnArrowIndicator(world);
            this.playSound(SoundEvents.ITEM_CROSSBOW_LOADING_START.value(), 1.0f, 0.5f);
        }
        updateArrowIndicator(world);
        p3Timer++;
        if (p3Timer >= CROSSBOW_RELOAD_TICKS) {
            // Reload done — start special fire phase
            p3Timer = 0;
            removeArrowIndicator(world);
            this.removeStatusEffect(StatusEffects.RESISTANCE);
            p3State = Phase3State.SPECIAL_FIRE;
            hasFlownUp = false;
        }
    }

    /**
     * SPECIAL_FIRE — boss flies up 7 blocks, multi-shot LeomordArrow at ONE
     * enemy (closest in aggro radius). Per user spec: "арбалет — взрывные стрелы
     * агр на одного врага". The explosion AoE may damage other enemies nearby —
     * that's intentional.
     * After firing, transition to MULTI_PULL_WAIT.
     */
    private void tickSpecialFire(ServerWorld world) {
        if (!hasFlownUp) {
            // Throw up 7 blocks — strong upward velocity
            this.setVelocity(0, 1.0, 0);
            this.velocityDirty = true;
            this.playSound(SoundEvents.ITEM_CROSSBOW_QUICK_CHARGE_3.value(), 2.0f, 0.6f);
            hasFlownUp = true;

            // Multi-shot 5 LeomordArrows at ONE enemy (closest non-creative)
            LivingEntity primaryTarget = findClosestEnemy(world, AGGRO_RADIUS);
            if (primaryTarget != null) {
                for (int i = 0; i < 5; i++) {
                    LeomordProjectiles.spawnLeomordArrow(world, this, primaryTarget);
                }
            } else {
                // No targets — fire at all players as fallback
                for (PlayerEntity p : world.getPlayers()) {
                    if (!p.isAlive() || p.isSpectator() || p.isCreative()) continue;
                    if (p.squaredDistanceTo(this) > AGGRO_RADIUS * AGGRO_RADIUS) continue;
                    LeomordProjectiles.spawnLeomordArrow(world, this, p);
                }
            }
            this.playSound(SoundEvents.ITEM_CROSSBOW_SHOOT, 2.0f, 0.5f);
        }
        // Wait until boss is back on ground, then transition to MULTI_PULL_WAIT
        if (this.isOnGround() && hasFlownUp) {
            hasFlownUp = false;
            p3State = Phase3State.MULTI_PULL_WAIT;
            p3Timer = 0;
            pullHitsThisMultiShot = 0;
            // Per user spec: "25 стрел умножаются на количество врагов".
            // For EACH enemy in aggro radius, spawn 25 Get Over Here arrows
            // aimed at points around THAT enemy. 8 enemies = 200 arrows.
            List<LivingEntity> enemies = LeomordProjectiles.findEnemiesInRadius(world, this, AGGRO_RADIUS);
            for (LivingEntity enemy : enemies) {
                LeomordProjectiles.spawnGetOverHereMultiShot(world, this, enemy, 25);
            }
        }
    }

    /**
     * MULTI_PULL_WAIT — wait up to 3 seconds for any of the 25 Get Over Here
     * arrows to hit a player. If at least one hit → go back to RELOAD.
     * If none hit after 3 seconds → go to SPRAY_MODE.
     */
    private void tickMultiPullWait(ServerWorld world) {
        p3Timer++;
        // If a pull hit during this wait, immediately transition to RELOAD
        if (pullHitsThisMultiShot > 0) {
            p3Timer = 0;
            p3State = Phase3State.RELOAD;
            return;
        }
        // Time's up — no pull hit, go to spray mode
        if (p3Timer >= MULTI_PULL_WAIT_TICKS) {
            p3Timer = 0;
            p3State = Phase3State.SPRAY_MODE;
            sprayShotCooldown = 0;
        }
    }

    /**
     * Called by LeomordProjectiles.handlePullEntityHit when a Get Over Here
     * arrow hits a player. Increments the hit counter so MULTI_PULL_WAIT can
     * detect it and transition to RELOAD.
     */
    public void notifyPullHit() {
        pullHitsThisMultiShot++;
    }

    /**
     * SPRAY_MODE — boss runs with crossbow, shoots spray arrows (explosion
     * only, no stun, no fireballs) at the closest enemy. Lasts 5 seconds,
     * then goes back to RELOAD (start of special attack cycle).
     */
    private void tickSprayMode(ServerWorld world) {
        p3Timer++;
        sprayShotCooldown--;
        if (sprayShotCooldown <= 0) {
            // Shoot a spray arrow at closest enemy (any LivingEntity, not just player)
            LivingEntity target = findClosestEnemy(world, AGGRO_RADIUS);
            if (target != null) {
                LeomordProjectiles.spawnSprayArrow(world, this, target);
                this.playSound(SoundEvents.ITEM_CROSSBOW_SHOOT, 1.5f, 0.7f);
            }
            sprayShotCooldown = 20; // shoot every 1 second
        }
        // Spray mode lasts 5 seconds (100 ticks), then back to RELOAD
        if (p3Timer >= SPRAY_DURATION_TICKS) {
            p3Timer = 0;
            p3State = Phase3State.RELOAD;
        }
    }

    // === ARROW INDICATOR (Armor Stand above head during reload) ===

    private void spawnArrowIndicator(ServerWorld world) {
        if (arrowIndicatorArmorStand != null) return;
        net.minecraft.entity.decoration.ArmorStandEntity stand =
                new net.minecraft.entity.decoration.ArmorStandEntity(
                        world, this.getX(), this.getY() + 3.0, this.getZ());
        stand.setInvisible(true); // hide body, show only head item
        stand.setNoGravity(true);
        // Note: setMarker(true) is private in 1.21.11 — accept the small hitbox
        // (0.5×1.975) instead. The armor stand disappears after the 25-tick
        // reload phase so collision is negligible.
        stand.setInvulnerable(true);
        stand.setCustomName(Text.literal("§e▼"));
        stand.setCustomNameVisible(true);
        // Give the stand an arrow in head slot
        stand.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.ARROW));
        arrowIndicatorArmorStand = stand.getUuid();
        world.spawnEntity(stand);
        // Also spawn a vertical particle stream above the boss's head
        // so the indicator is visible even on Bedrock (where armor stand
        // rendering might be incomplete).
        world.spawnParticles(net.minecraft.particle.ParticleTypes.CRIT,
                this.getX(), this.getY() + 3.0, this.getZ(),
                30, 0.3, 0.5, 0.3, 0.2);
    }

    private void updateArrowIndicator(ServerWorld world) {
        if (arrowIndicatorArmorStand == null) return;
        // Find the stand and teleport it above the boss's head
        for (net.minecraft.entity.decoration.ArmorStandEntity e :
                world.getEntitiesByType(net.minecraft.entity.EntityType.ARMOR_STAND, e2 -> e2.isAlive())) {
            if (e.getUuid().equals(arrowIndicatorArmorStand)) {
                e.refreshPositionAndAngles(this.getX(), this.getY() + 3.0, this.getZ(),
                        this.getYaw(), 0);
                return;
            }
        }
        // Stand not found — respawn it next tick
        arrowIndicatorArmorStand = null;
    }

    private void removeArrowIndicator(ServerWorld world) {
        if (arrowIndicatorArmorStand == null) return;
        for (net.minecraft.entity.decoration.ArmorStandEntity e :
                world.getEntitiesByType(net.minecraft.entity.EntityType.ARMOR_STAND, e2 -> e2.isAlive())) {
            if (e.getUuid().equals(arrowIndicatorArmorStand)) {
                e.discard();
                break;
            }
        }
        arrowIndicatorArmorStand = null;
    }

    // === DEATH SEQUENCE (iteration 6 — per-enemy) ===
    //
    // Per user spec:
    //   - On death, for EACH ENEMY (player OR Larry/mob) in aggro radius:
    //     spawn 4 horses ABOVE that enemy (Y+20).
    //   - 4 horses × N enemies = total horses (8 Larrys = 32 horses).
    //   - Each horse falls via gravity (no fox jumps — `LeomordDeathHorse`
    //     tag in LeomordHorseEntity.tickMovement skips Phase 1 logic).
    //   - As horses fall, they BREAK blocks on the way down (until Y = enemy Y).
    //   - When horse Y reaches enemy Y: spawn 4 TNT + 4 wind bursts UNDER THE
    //     ENEMY (not under the horse). Horse disappears 1 second (20 ticks)
    //     after arrival.
    //   - After all 4 horses for an enemy are gone (discarded):
    //     Wait 10 seconds (200 ticks).
    //     If enemy is a PLAYER AND alive → give LeomordLootBag (empty).
    //     If enemy is a Larry/mob OR dead/disconnected → no loot.
    //   - When all enemies are resolved → Leomord removed.

    private static final class EnemyDeathState {
        final UUID enemyUuid;
        final java.util.Set<UUID> pendingHorses = new java.util.HashSet<>();
        /** Horses that have arrived (Y matched) but not yet discarded (1s delay). */
        final java.util.Set<UUID> arrivedHorses = new java.util.HashSet<>();
        /** Map: arrived horse UUID → tick when it should be discarded. */
        final java.util.Map<UUID, Long> horseDiscardAt = new java.util.HashMap<>();
        boolean allHorsesDiscarded = false;
        long lootWaitStartTick = 0;
        boolean resolved = false;

        EnemyDeathState(UUID enemyUuid) { this.enemyUuid = enemyUuid; }
    }

    private final java.util.Map<UUID, EnemyDeathState> enemyDeathStates = new java.util.HashMap<>();
    /** Map of horse UUID → target enemy UUID. */
    private final java.util.Map<UUID, UUID> horseToEnemy = new java.util.HashMap<>();

    @Override
    public void onDeath(DamageSource source) {
        if (deathHandled) return;
        deathHandled = true;

        ServerWorld world = (ServerWorld) this.getEntityWorld();

        bossBar.setPercent(0);
        bossBar.clearPlayers();

        world.getServer().getPlayerManager().broadcast(
                Text.literal("§4§lЛеоморд повержен! Падальщики падают с неба...")
                        .formatted(Formatting.DARK_RED), false);

        deathSequenceStage = 1;
        deathTimeoutTicks = DEATH_TIMEOUT_MAX;

        // For EACH ENEMY in aggro radius (players + Larrys + mobs, excluding
        // creative players + peaceful animals): spawn 4 horses above that enemy.
        java.util.List<LivingEntity> enemies =
                LeomordProjectiles.findEnemiesInRadius(world, this, COMBAT_RADIUS);

        double[][] offsets = { {2, 2}, {-2, 2}, {2, -2}, {-2, -2} };
        for (LivingEntity target : enemies) {
            EnemyDeathState state = new EnemyDeathState(target.getUuid());
            for (double[] off : offsets) {
                LeomordHorseEntity horse = LeomordMod.LEOMORD_HORSE.create(world,
                        net.minecraft.entity.SpawnReason.TRIGGERED);
                if (horse == null) continue;
                double hx = target.getX() + off[0];
                double hy = target.getY() + 20.0;
                double hz = target.getZ() + off[1];
                horse.refreshPositionAndAngles(hx, hy, hz, 0, 0);
                horse.addCommandTag("LeomordDeathHorse");
                // LeomordHorseEntity.isInvulnerableTo returns true for ALL damage
                // when the horse has the LeomordDeathHorse tag — so player attacks
                // (including Three Fangs trident) won't kill it.
                horse.setNoGravity(false);
                horse.setHealth(horse.getMaxHealth());
                world.spawnEntity(horse);
                state.pendingHorses.add(horse.getUuid());
                horseToEnemy.put(horse.getUuid(), target.getUuid());
            }
            enemyDeathStates.put(target.getUuid(), state);
        }
    }

    private void tickDeathSequence(ServerWorld world) {
        deathTimeoutTicks--;
        if (deathTimeoutTicks <= 0) {
            finishDeath(world);
            return;
        }

        long currentTick = world.getServer().getTicks();

        // === Phase A: Process falling horses ===
        java.util.Iterator<java.util.Map.Entry<UUID, UUID>> it = horseToEnemy.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<UUID, UUID> entry = it.next();
            UUID horseUuid = entry.getKey();
            UUID enemyUuid = entry.getValue();

            LivingEntity target = LeomordProjectiles.resolveTargetPublic(world, enemyUuid);
            if (target == null) {
                // Target gone — discard horse
                Entity orphan = world.getEntity(horseUuid);
                if (orphan != null) orphan.discard();
                it.remove();
                EnemyDeathState state = enemyDeathStates.get(enemyUuid);
                if (state != null) {
                    state.pendingHorses.remove(horseUuid);
                    state.arrivedHorses.remove(horseUuid);
                    state.horseDiscardAt.remove(horseUuid);
                    if (state.pendingHorses.isEmpty() && state.arrivedHorses.isEmpty()) {
                        state.allHorsesDiscarded = true;
                    }
                }
                continue;
            }

            Entity e = world.getEntity(horseUuid);
            if (!(e instanceof LeomordHorseEntity horse) || !horse.isAlive()) {
                // Horse gone (discarded after 1s delay) — remove from tracking
                it.remove();
                EnemyDeathState state = enemyDeathStates.get(enemyUuid);
                if (state != null) {
                    state.pendingHorses.remove(horseUuid);
                    state.arrivedHorses.remove(horseUuid);
                    state.horseDiscardAt.remove(horseUuid);
                    if (state.pendingHorses.isEmpty() && state.arrivedHorses.isEmpty()) {
                        state.allHorsesDiscarded = true;
                    }
                }
                continue;
            }

            // If horse has already arrived, check if it's time to discard it
            EnemyDeathState state = enemyDeathStates.get(enemyUuid);
            if (state != null && state.arrivedHorses.contains(horseUuid)) {
                Long discardAt = state.horseDiscardAt.get(horseUuid);
                if (discardAt != null && currentTick >= discardAt) {
                    horse.discard();
                    state.arrivedHorses.remove(horseUuid);
                    state.horseDiscardAt.remove(horseUuid);
                    if (state.pendingHorses.isEmpty() && state.arrivedHorses.isEmpty()) {
                        state.allHorsesDiscarded = true;
                    }
                }
                continue;
            }

            // Still falling — break blocks on the way down
            double horseY = horse.getY();
            double enemyY = target.getY();

            if (horseY > enemyY) {
                net.minecraft.util.math.BlockPos horseBlockPos =
                        net.minecraft.util.math.BlockPos.ofFloored(horse.getX(), horse.getY(), horse.getZ());
                net.minecraft.block.BlockState here = world.getBlockState(horseBlockPos);
                if (!here.isAir()) {
                    if (here.getBlock().getHardness() >= 0
                            && here.getBlock() != net.minecraft.block.Blocks.BEDROCK
                            && here.getBlock() != net.minecraft.block.Blocks.OBSIDIAN
                            && here.getBlock() != net.minecraft.block.Blocks.CRYING_OBSIDIAN
                            && here.getBlock() != net.minecraft.block.Blocks.REINFORCED_DEEPSLATE
                            && here.getBlock() != net.minecraft.block.Blocks.NETHERITE_BLOCK
                            && here.getBlock() != net.minecraft.block.Blocks.ANCIENT_DEBRIS) {
                        world.breakBlock(horseBlockPos, false);
                    }
                }
            }

            // Has horse reached enemy's Y?
            if (horseY <= enemyY + 0.5) {
                // ARRIVED — spawn 4 TNT + 4 wind bursts UNDER THE ENEMY
                triggerArrivalExplosions(world, target, horse);
                // Schedule horse discard 1 second (20 ticks) from now
                if (state != null) {
                    state.arrivedHorses.add(horseUuid);
                    state.horseDiscardAt.put(horseUuid, currentTick + 20);
                    state.pendingHorses.remove(horseUuid);
                }
            }
        }

        // === Phase B: 10-second loot wait after all horses for an enemy are gone ===
        // Decide the head winner ONCE when loot distribution begins (the first
        // enemy to enter the 10s wait phase). One random player among those
        // who will receive loot gets the 3x Wither Skeleton Skull bonus.
        boolean allResolved = true;
        java.util.List<UUID> playersReadyForLoot = new java.util.ArrayList<>();
        for (java.util.Map.Entry<UUID, EnemyDeathState> entry : enemyDeathStates.entrySet()) {
            EnemyDeathState state = entry.getValue();
            if (state.resolved) continue;
            if (!state.allHorsesDiscarded) {
                allResolved = false;
                continue;
            }
            if (state.lootWaitStartTick == 0) {
                state.lootWaitStartTick = currentTick;
            }
            if (currentTick - state.lootWaitStartTick < 200) {
                // Still in 10s wait — if enemy dies, mark resolved (no loot)
                LivingEntity target = LeomordProjectiles.resolveTargetPublic(world, state.enemyUuid);
                if (target == null || !target.isAlive()) {
                    state.resolved = true;
                    continue;
                }
                if (target instanceof PlayerEntity) {
                    playersReadyForLoot.add(state.enemyUuid);
                }
                allResolved = false;
                continue;
            }
            // 10s passed — check final enemy status
            LivingEntity target = LeomordProjectiles.resolveTargetPublic(world, state.enemyUuid);
            if (target == null || !target.isAlive()) {
                state.resolved = true;
                continue;
            }
            // Only PLAYERS get loot — Larrys/mobs don't (no inventory).
            if (target instanceof PlayerEntity p) {
                // Determine if THIS player is the head winner.
                // Head winner is picked ONCE per death sequence — we use
                // damageDealers set + a random pick to ensure consistency.
                boolean isHeadWinner = isHeadWinner(state.enemyUuid);
                // Pre-roll the loot item for this player
                String lootKey = LeomordLootBag.rollLootItem(world);
                ItemStack bag = LeomordLootBag.create(isHeadWinner, lootKey);
                if (!p.getInventory().insertStack(bag)) {
                    p.dropItem(bag, false);
                }
                p.sendMessage(Text.literal("§6§lВы получили Мешок Леоморда!")
                        .formatted(Formatting.GOLD), false);
            }
            state.resolved = true;
        }

        if (allResolved) {
            // Reset head winner tracking
            headWinnerUuid = null;
            finishDeath(world);
        }
    }

    // === Head winner tracking — picked once per death sequence ===
    private UUID headWinnerUuid = null;

    /**
     * Determine if the given player UUID is the head winner. The winner is
     * picked randomly from the damageDealers set (players who damaged Leomord)
     * the first time this method is called after onDeath.
     */
    private boolean isHeadWinner(UUID playerUuid) {
        if (headWinnerUuid == null) {
            // Pick a random damager as the head winner
            if (damageDealers.isEmpty()) {
                // Fallback: this player is the winner
                headWinnerUuid = playerUuid;
            } else {
                java.util.List<UUID> damagers = new java.util.ArrayList<>(damageDealers);
                int idx = java.util.concurrent.ThreadLocalRandom.current().nextInt(damagers.size());
                headWinnerUuid = damagers.get(idx);
            }
        }
        return headWinnerUuid.equals(playerUuid);
    }

    /**
     * Trigger 4 TNT explosions (no block break) + 4 wind bursts UNDER THE ENEMY
     * (not under the horse). The horse is passed as the wind burst source so
     * it doesn't bounce itself (though it's about to be discarded anyway).
     */
    private void triggerArrivalExplosions(ServerWorld world, LivingEntity target, LeomordHorseEntity horse) {
        double px = target.getX();
        double py = target.getY();
        double pz = target.getZ();

        // 4 TNT explosions (no block break) at offsets around the enemy
        double[][] offsets = { {0, 0}, {1, 0}, {-1, 0}, {0, 1} };
        for (double[] off : offsets) {
            double ex = px + off[0];
            double ez = pz + off[1];
            LeomordProjectiles.triggerExplosion(world, this, ex, py, ez);
        }

        // 4 wind bursts under the enemy — pass `horse` as source to exclude it
        for (double[] off : offsets) {
            double ex = px + off[0];
            double ez = pz + off[1];
            LeomordProjectiles.triggerWindBurst(world, horse, ex, py, ez, 6.0);
        }

        // Strong knock-up on the enemy specifically
        if (target.isAlive()) {
            Vec3d cur = target.getVelocity();
            target.setVelocity(cur.x * 0.2, 2.5, cur.z * 0.2);
            target.velocityDirty = true;
        }
    }

    private void finishDeath(ServerWorld world) {
        if (deathSequenceStage == 2) return;
        deathSequenceStage = 2;

        // === Cleanup: discard any leftover death horses that didn't get
        //     discarded during the death sequence (e.g., timeout, errors). ===
        try {
            for (LeomordHorseEntity leftover : world.getEntitiesByType(
                    LeomordMod.LEOMORD_HORSE,
                    h -> h.isAlive() && h.getCommandTags().contains("LeomordDeathHorse"))) {
                leftover.discard();
            }
        } catch (Throwable t) {
            LeomordMod.LOGGER.error("[leomordmod] finishDeath cleanup failed: {}", t.toString());
        }

        // If no players got loot (e.g., all enemies were Larrys or everyone died),
        // drop a single fallback loot bag at Leomord's death position.
        boolean anyLootGiven = false;
        for (EnemyDeathState state : enemyDeathStates.values()) {
            if (state.resolved) anyLootGiven = true;
        }
        if (!anyLootGiven) {
            ItemStack bag = LeomordLootBag.create();
            net.minecraft.entity.ItemEntity item = new net.minecraft.entity.ItemEntity(
                    world, this.getX(), this.getY() + 1, this.getZ(), bag);
            item.setVelocity(0, 0.3, 0);
            world.spawnEntity(item);
        }

        world.getServer().getPlayerManager().broadcast(
                Text.literal("§6§lЛеоморд окончательно повержен! Мешки Леоморда розданы!")
                        .formatted(Formatting.GOLD), false);

        LeomordProjectiles.clear();
        this.discard();
    }

    // === EQUIPMENT (Crown + Netherite + Vex trim) ===

    /**
     * Equip Leomord with the Crown (from crownmod) on his head and a full
     * Netherite armor set with Vex trim + Gold material on body/legs/boots.
     * Apply negative attribute modifiers so the set's total protection matches
     * vanilla Iron set (armor=15, toughness=0, knockback_resistance=0).
     *
     * Called from LeomordMod.onInitialize() at the moment of summoning — but
     * since we need access to the server's registry (for trim materials),
     * we make this an instance method called on first tick.
     */
    private boolean equipmentApplied = false;

    private void applyEquipmentIfNeeded(ServerWorld world) {
        if (equipmentApplied) return;
        equipmentApplied = true;

        // Head: Royal Crown (from crownmod)
        ItemStack crown = new ItemStack(CrownMod.CROWN);
        this.equipStack(EquipmentSlot.HEAD, crown);

        // Body / Legs / Boots: Netherite armor with Vex trim + Gold material
        ItemStack chest  = makeNetheriteWithVexTrim(world, Items.NETHERITE_CHESTPLATE);
        ItemStack legs   = makeNetheriteWithVexTrim(world, Items.NETHERITE_LEGGINGS);
        ItemStack boots  = makeNetheriteWithVexTrim(world, Items.NETHERITE_BOOTS);
        // Make unbreakable — they're the boss's gear, don't drop or break
        chest.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
        legs.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
        boots.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
        this.equipStack(EquipmentSlot.CHEST, chest);
        this.equipStack(EquipmentSlot.LEGS, legs);
        this.equipStack(EquipmentSlot.FEET, boots);

        // Mainhand: Leomord Spear (Phase 1 + 2). Phase 3 swaps to Crossbow.
        this.equipStack(EquipmentSlot.MAINHAND, LeomordSpear.createBossSpear());

        // Apply armor attribute nerfs (so Netherite set = Iron set protection)
        applyArmorNerfs();
    }

    /** Make a Netherite armor piece with Vex trim + Gold material. */
    private ItemStack makeNetheriteWithVexTrim(ServerWorld world, net.minecraft.item.Item item) {
        ItemStack stack = new ItemStack(item);
        // Apply Vex trim + Gold material
        Registry<ArmorTrimPattern> patternReg = world.getRegistryManager()
                .getOrThrow(RegistryKeys.TRIM_PATTERN);
        Registry<ArmorTrimMaterial> materialReg = world.getRegistryManager()
                .getOrThrow(RegistryKeys.TRIM_MATERIAL);
        RegistryEntry<ArmorTrimPattern> vex =
                patternReg.getOrThrow(ArmorTrimPatterns.VEX);
        RegistryEntry<ArmorTrimMaterial> gold =
                materialReg.getOrThrow(ArmorTrimMaterials.GOLD);
        ArmorTrim trim = new ArmorTrim(gold, vex);
        stack.set(DataComponentTypes.TRIM, trim);
        return stack;
    }

    /**
     * Apply negative attribute modifiers so the Netherite set's total
     * protection matches vanilla Iron set:
     *   Netherite set: armor=20, toughness=12, knockback_res=0.4
     *   Iron set:     armor=15, toughness=0,  knockback_res=0.0
     *   Delta:        -5,        -12,         -0.4
     */
    private void applyArmorNerfs() {
        var armorAttr = this.getAttributeInstance(EntityAttributes.ARMOR);
        if (armorAttr != null) {
            // Idempotent: remove existing modifier with same ID before re-adding
            // (otherwise vanilla 1.21.11 throws IllegalArgumentException)
            armorAttr.removeModifier(ARMOR_NERF_ID);
            armorAttr.addTemporaryModifier(new EntityAttributeModifier(
                    ARMOR_NERF_ID, -5.0,
                    EntityAttributeModifier.Operation.ADD_VALUE));
        }
        var toughAttr = this.getAttributeInstance(EntityAttributes.ARMOR_TOUGHNESS);
        if (toughAttr != null) {
            toughAttr.removeModifier(TOUGH_NERF_ID);
            toughAttr.addTemporaryModifier(new EntityAttributeModifier(
                    TOUGH_NERF_ID, -12.0,
                    EntityAttributeModifier.Operation.ADD_VALUE));
        }
        var kbAttr = this.getAttributeInstance(EntityAttributes.KNOCKBACK_RESISTANCE);
        if (kbAttr != null) {
            kbAttr.removeModifier(KB_NERF_ID);
            kbAttr.addTemporaryModifier(new EntityAttributeModifier(
                    KB_NERF_ID, -0.4,
                    EntityAttributeModifier.Operation.ADD_VALUE));
        }
    }

    // === Loot / equipment drops (prevented) ===

    @Override
    protected void dropEquipment(ServerWorld world, DamageSource source, boolean causedByPlayer) {
        // No equipment drops — the boss's netherite + crown stay with the boss
    }

    @Override
    protected void dropLoot(ServerWorld world, DamageSource source, boolean causedByPlayer) {
        // No vanilla loot — only LeomordLootBag from death sequence
    }

    @Override
    protected int getExperienceToDrop(ServerWorld world) {
        return 0;
    }

    @Override
    public boolean canPickUpLoot() {
        return false;
    }

    // === FIRE / LAVA IMMUNITY (wither skeleton is already fire-immune — confirm) ===

    @Override
    public boolean isFireImmune() {
        return true; // 100% fire + lava immunity
    }

    @Override
    public boolean isImmuneToExplosion(net.minecraft.world.explosion.Explosion explosion) {
        return true; // boss immune to explosion damage
    }

    // === BOSS BAR CLEANUP ===

    @Override
    public void onRemoved() {
        if (bossBar != null) {
            bossBar.setPercent(0);
            bossBar.clearPlayers();
        }
        LeomordProjectiles.clear();
        super.onRemoved();
    }

    // === DISGUISE (for Bedrock compatibility — send vanilla WITHER_SKELETON spawn packet) ===
    // Note: LeomordEntity IS a WitherSkeletonEntity subclass. The registered
    // EntityType is leomordmod:leomord — vanilla clients don't know about it.
    // We disguise as vanilla WITHER_SKELETON so the client renders correctly.

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket(EntityTrackerEntry entry) {
        return new EntitySpawnS2CPacket(
                this.getId(),
                this.getUuid(),
                entry.getPos().x, entry.getPos().y, entry.getPos().z,
                entry.getPitch(), entry.getYaw(),
                EntityType.WITHER_SKELETON,
                0,
                entry.getVelocity(),
                entry.getHeadYaw()
        );
    }

    // === UTILITIES ===

    private void autoAddPlayers(ServerWorld world) {
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (p.squaredDistanceTo(this) < COMBAT_RADIUS * COMBAT_RADIUS
                    && !bossBar.getPlayers().contains(p)) {
                bossBar.addPlayer(p);
            }
        }
    }

    private PlayerEntity findClosestPlayer(ServerWorld world, double radius) {
        double best = radius * radius;
        PlayerEntity bestP = null;
        for (PlayerEntity p : world.getPlayers()) {
            if (!p.isAlive() || p.isSpectator() || p.isCreative()) continue;
            double d = p.squaredDistanceTo(this);
            if (d < best) {
                best = d;
                bestP = p;
            }
        }
        return bestP;
    }

    /** Find closest enemy (any LivingEntity) — excludes creative players,
     *  spectators, peaceful animals, self. */
    private LivingEntity findClosestEnemy(ServerWorld world, double radius) {
        List<LivingEntity> enemies = LeomordProjectiles.findEnemiesInRadius(world, this, radius);
        if (enemies.isEmpty()) return null;
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : enemies) {
            double d = e.squaredDistanceTo(this);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    /**
     * Public accessor — called from LeomordMod summon flow to apply equipment
     * after the boss has spawned (needs server registry access).
     *
     * Each step is wrapped in its own try-catch so that a failure in one
     * (e.g., crownmod not present, broken registry entry) does NOT prevent
     * the rest from running. The most critical step — spawning the horse
     * and starting the ride — runs LAST so it always succeeds.
     */
    public void onSummoned(ServerWorld world) {
        // === Defensive cleanup: discard any leftover death horses from a
        //     previous Leomord's death sequence (so the new boss doesn't aggro
        //     on them and so the world isn't polluted with immortal horses). ===
        try {
            for (LeomordHorseEntity leftover : world.getEntitiesByType(
                    LeomordMod.LEOMORD_HORSE,
                    h -> h.isAlive() && h.getCommandTags().contains("LeomordDeathHorse"))) {
                leftover.discard();
            }
        } catch (Throwable t) {
            LeomordMod.LOGGER.error("[leomordmod] onSummoned death-horse cleanup failed: {}", t.toString());
        }

        // Step 1: apply armor attribute nerfs (idempotent, safe)
        try {
            applyArmorNerfs();
        } catch (Throwable t) {
            LeomordMod.LOGGER.error("[leomordmod] Failed to apply armor nerfs: {}", t.toString());
        }

        // Step 2: apply equipment (Crown + Netherite + Vex trim)
        try {
            applyEquipmentIfNeeded(world);
        } catch (Throwable t) {
            LeomordMod.LOGGER.error("[leomordmod] Failed to apply equipment: {}", t.toString());
        }

        // Step 3: spawn the phase-1 horse and start riding — CRITICAL, must succeed
        try {
            LeomordHorseEntity horse = LeomordMod.LEOMORD_HORSE.create(world,
                    net.minecraft.entity.SpawnReason.TRIGGERED);
            if (horse != null) {
                horse.refreshPositionAndAngles(this.getX(), this.getY(), this.getZ(),
                        this.getYaw(), 0);
                world.spawnEntity(horse);
                this.startRiding(horse, true, true);
            }
        } catch (Throwable t) {
            LeomordMod.LOGGER.error("[leomordmod] Failed to spawn horse: {}", t.toString());
        }
        // The announce broadcast is performed by LeomordMod's UseItemCallback.
    }
}
