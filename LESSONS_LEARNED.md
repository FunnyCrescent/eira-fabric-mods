# Напутствие для следующей нейросети, которая будет делать следующего босса

Это документ-передача. Прочитай его ВНИМАТЕЛЬНО перед тем как начать работать над новым боссом. Тут собран опыт реальных багов и фиксов — не повторяй наших ошибок.

---

## Контекст проекта

Серия кастомных боссов для Minecraft 1.21.11 (Fabric, server-side only, Bedrock-совместимый через Geyser). Автор модов — Eira. Все моды:

- `environment: server` — клиенту ничего ставить не нужно
- Используют ванильные предметы с NBT-маркерами (через `CUSTOM_DATA` компонент) — чтобы Bedrock через Geyser видел кастомные имена/лор
- Боссов можно "дизать" (disguise) через `createSpawnPacket()` override — клиент видит ванильного моба, сервер тречит кастомный класс
- Лут через "Loot Bags" — Gray Bundle с NBT-маркером, открывается по ПКМ
- Связки между модами через общие NBT-ключи (LarryBone, WolfBox, DestroyerSummon и т.д.)

## Установка окружения

1. **JDK 21** — качать с Oracle (или Temurin). Локально в `/home/z/.local/jdk/`. НЕ использует `apt` (нет sudo).
2. **Gradle wrapper** уже включён в каждый мод (`./gradlew`). Версия Gradle 9.5.0.
3. **fabric-loom 1.17.21** — плагин сборки.
4. **Minecraft 1.21.11 + Yarn mappings 1.21.11+build.6** — указываются в `gradle.properties`.
5. **Fabric Loader 0.16.10, Fabric API 0.141.6+1.21.11**.
6. **Кэш Gradle** живёт в `/home/z/.gradle/caches/fabric-loom/1.21.11/` — там же `mappings.tiny` для поиска имён методов.

## Как искать методы в mappings

```bash
# Найти класс по имени
grep -P "^c\t[a-z]+\tnet/minecraft/class_1297$" /path/to/mappings.tiny
# → c	cgk	net/minecraft/class_1297	net/minecraft/entity/Entity

# Найти методы класса (например, damage)
sed -n '/^c\tcgk\tnet\/minecraft\/class_1297/,/^c\t[^	]/p' mappings.tiny | grep "damage"

# Найти поле (например, lastDamageTime)
grep "field_6226\|lastDamageTime" mappings.tiny | head -3
```

## ГЛАВНОЕ: Mixin — какие грабли тут есть

### 1. Mixin применяет Yarn→Intermediary маппинг автоматически
Когда пишешь `@Inject(method = "onEntityHit")`, loom при сборке преобразует это в `method_7454` (intermediary). Не нужно `remap = false` для стандартных методов. **RefMap генерируется автоматически.**

### 2. Нельзя делать @Mixin на класс и @Inject на метод, которого в нём НЕТ
MixIn ищет метод **только в целевом классе**. Если метод унаследован или переопределён в подклассе — нужно таргетить правильный класс.

**Пример из settlementdestroyer:**
- `onEntityHit` определён в `ProjectileEntity` (class_1676)
- `PersistentProjectileEntity` (class_1665) **ПЕРЕОПРЕДЕЛЯЕТ** `onEntityHit`
- `TridentEntity` (class_1685) **ТОЖЕ ПЕРЕОПРЕДЕЛЯЕТ** `onEntityHit`

Если твой mixin на `ProjectileEntity` — он НЕ сработает для TridentEntity (вызывается версия из TridentEntity).
Если твой mixin на `PersistentProjectileEntity` — он НЕ сработает для TridentEntity (по той же причине).
**Правильно:** `@Mixin(TridentEntity.class)` напрямую.

Проверяй через декомпиляцию:
```bash
$JAVA_HOME/bin/javap -p 'net/minecraft/entity/projectile/TridentEntity.class' | grep "onEntityHit"
```

### 3. @Shadow не работает для методов родителя
Если метод определён в родителе (не переопределён в текущем классе), `@Shadow` его не найдёт. Нужно либо `@Invoker` (для вызова), либо `@Mixin` на родителя.

### 4. @Invoker для protected методов родителя
`asItemStack()` — protected в `PersistentProjectileEntity`. Чтобы вызвать его снаружи:
```java
@Mixin(PersistentProjectileEntity.class)
public interface PersistentProjectileEntityInvoker {
    @Invoker("asItemStack")
    ItemStack settlementdestroyer$asItemStack();

    @Invoker("isInGround")
    boolean settlementdestroyer$isInGround();
}
```
В коде: `((PersistentProjectileEntityInvoker) trident).settlementdestroyer$asItemStack()`.

### 5. Reflection НЕ РАБОТАЕТ для protected методов в production
`Method.getMethod("asItemStack")` вернёт метод только если он public. Для protected нужно `getDeclaredMethod()` + `setAccessible(true)`, но даже это не работает в production-jar, потому что методы переименованы в intermediary (`method_7445`).

**Всегда используй @Invoker вместо reflection.**

### 6. Mixins JSON — обязательный
Файл `<modid>.mixins.json` в `src/main/resources/`:
```json
{
  "required": true,
  "minVersion": "0.8",
  "package": "com.<modid>.mixin",
  "compatibilityLevel": "JAVA_21",
  "mixins": ["ClassName1", "ClassName2"],
  "injectors": { "defaultRequire": 1 }
}
```
И ОБЯЗАТЕЛЬНО пропиши его в `fabric.mod.json` в поле `"mixins": [...]`.

### 7. Проверка: mixin работает?
Добавь `LOGGER.info("[DEBUG] mixin called!")` в начале `@Inject` метода. После рестарта сервера и теста — ищи строку в `/logs/latest.log`:
```bash
grep "\[DEBUG\]" /logs/latest.log
```
Если строки нет — mixin не зарегистрирован или не находит метод.

## Боссы — общая архитектура

### 1. Entity класс
- extends подходящий ванильный класс (RavagerEntity, FoxEntity и т.д.)
- В конструкторе: 200 HP, BossBar, custom name, persistent
- `createSettlementDestroyerAttributes()` — статический метод для атрибутов
- `initGoals()` — очистка vanilla goals + свои (MeleeAttackGoal, RevengeGoal, ActiveTargetGoal)
- `tryAttack()` — override для добавления кастомных эффектов при ударе
- `tickMovement()` — основной цикл: фазы, кулдауны, спавн эффектов
- `onDeath()` — выдача лута
- `onRemoved()` — очистка BossBar (обязательно!)
- `createSpawnPacket()` — disguise через `EntitySpawnS2CPacket` с ванильным EntityType

### 2. Disguise (критично для Bedrock)
```java
@Override
public Packet<ClientPlayPacketListener> createSpawnPacket(EntityTrackerEntry entry) {
    return new EntitySpawnS2CPacket(
        this.getId(), this.getUuid(),
        entry.getPos().x, entry.getPos().y, entry.getPos().z,
        entry.getPitch(), entry.getYaw(),
        EntityType.RAVAGER,  // ← disguise! клиент видит RAVAGER
        0, entry.getVelocity(), entry.getHeadYaw()
    );
}
```
Без этого клиент видит "заглушку" (курицу) с крошечным хитбоксом.

### 3. BossBar
```java
this.bossBar = new ServerBossBar(
    Text.literal("Имя").formatted(Formatting.DARK_RED, Formatting.BOLD),
    BossBar.Color.RED, BossBar.Style.PROGRESS);
```
- В `tickMovement()`: `bossBar.setPercent(this.getHealth() / this.getMaxHealth())`
- **Автодобавление игроков**: в `tickMovement()` перебирай `serverWorld.getPlayers()` и добавляй тех, кто в радиусе
- В `onDeath()` и `onRemoved()`: `bossBar.setPercent(0); bossBar.clearPlayers()`

### 4. Фазы
```java
private int phase = 1;
// в tickMovement():
float hp = this.getHealth();
int newPhase = hp > 100.0f ? 1 : 2;
if (newPhase != phase) {
    phase = newPhase;
    onPhaseChange(serverWorld);  // тут же вызывается первая спецатака
}
```
Таймер спецатак — **абсолютный**, не сбрасывается при уроне.

### 5. AoE урон — используй mobAttack
```java
e.damage(world, world.getDamageSources().mobAttack(this), damage);
```
Это **режется бронёй**. Если нужен true damage — `world.getDamageSources().magic(...)` или `outOfWorld()`.

### 6. Invulnerability frames
В ваниле, после урона сущность получает 10 тиков (500мс) неуязвимости. Если спавнишь несколько челюстей одновременно — урон пройдёт только от первой.

**Решение** — mixin на `LivingEntity.damage()`:
```java
@Inject(method = "damage", at = @At("HEAD"))
private void resetFangInvuln(ServerWorld world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
    if (source.getSource() instanceof EvokerFangsEntity) {
        this.lastDamageTime = 0L;  // @Shadow private long lastDamageTime;
        this.lastDamageTaken = 0.0F;
    }
}
```

## Items — общая архитектура

### 1. Кастомный предмет через NBT-маркер
```java
public static ItemStack create() {
    ItemStack stack = new ItemStack(Items.ECHO_SHARD);  // или любой ванильный
    NbtCompound nbt = new NbtCompound();
    nbt.putBoolean("MyCustomItem", true);  // уникальный NBT-ключ!
    stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Имя").formatted(Formatting.GOLD, Formatting.BOLD));
    stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(...)));
    return stack;
}

public static boolean isMyItem(ItemStack stack) {
    if (!stack.isOf(Items.ECHO_SHARD)) return false;
    NbtComponent cd = stack.get(DataComponentTypes.CUSTOM_DATA);
    return cd != null && cd.copyNbt().getBoolean("MyCustomItem", false);
}
```

### 2. УНИКАЛЬНЫЕ NBT-ключи — критично!
Если твой мод использует `"LootBag"` как ключ, а другой мод тоже — конфликт. Всегда используй префикс:
- `SettlementDestroyerLootBag` (не `LootBag`)
- `ThreeFangs` (не `Trident`)
- `DestroyerSummon` (не `SummonItem`)

### 3. LootBag
Gray Bundle с NBT-маркером. ПКМ открывает через `UseItemCallback`:
```java
UseItemCallback.EVENT.register((player, world, hand) -> {
    if (world.isClient()) return ActionResult.PASS;
    ItemStack stack = player.getStackInHand(hand);
    if (!LootBag.isLootBag(stack)) return ActionResult.PASS;
    LootBag.open(stack, player);
    return ActionResult.CONSUME;  // предотвратить vanilla Bundle UI
});
```

### 4. Крафт
- Рецепт в `data/<modid>/recipe/`
- Для ingredient с NBT-мэтчингом: `fabric:custom_data` (works on Java, NOT on Bedrock) или `fabric:components` (тоже только Java)
- **Для Bedrock** делай упрощённый рецепт с обычным предметом
- **ВАЖНО:** `custom_name` и `lore` в `result.components` — это **объекты Text**, не строки JSON с экранированными кавычками!

Неправильно:
```json
"minecraft:custom_name": "{\"text\":\"Имя\",\"color\":\"red\"}"
```
Правильно:
```json
"minecraft:custom_name": {"text": "Имя", "color": "red"}
```

### 5. Banner patterns в рецепте
`banner_patterns` компонент — это **список Layer напрямую**, не объект с `patterns`:
```json
"minecraft:banner_patterns": [
  {"pattern": "minecraft:stripe_downleft", "color": "black"},
  {"pattern": "minecraft:border", "color": "white"}
]
```

## Команды

### Регистрация
```java
CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
    dispatcher.register(literal("mycommand")
        .requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))  // для админа
        .executes(ctx -> myAction(ctx.getSource()))
    );
});
```
Для публичной команды — без `.requires()`.

### Переключатель режима (toggle)
```java
private static final AtomicBoolean enabled = new AtomicBoolean(false);

private static int toggle(ServerCommandSource source) {
    boolean oldValue = enabled.get();
    boolean newValue = !oldValue;
    enabled.set(newValue);
    // broadcast to all players
    source.getServer().getPlayerManager().broadcast(message, false);
    return 1;
}
```
**Не используй `updateAndGet(lambda)`** — на Java 21 + fabric-loom это не компилируется. Используй `get()` + `set()`.

## EvokerFangsEntity — конструктор

```java
// EvokerFangsEntity(World, x, y, z, yaw, warmup, owner)
// warmup=0 → мгновенный урон (без задержки 1 сек)
// warmup=20 → стандартная задержка вызывателя
EvokerFangsEntity fangs = new EvokerFangsEntity(world, x, y, z, 0f, 0, owner);
world.spawnEntity(fangs);
```

Owner должен быть `LivingEntity` (если null — челюсти никому не принадлежат, могут бить всех).

## Физика перемещения босса

### НЕ используй setVelocity() без ограничителя
- `setVelocity()` задаёт **начальную скорость**, потом работает трение
- Трение зависит от поверхности (лёд/земля/вода) — непредсказуемо
- Босс может улететь в 2-3 раза дальше планируемого

### Правильно — tick-based movement
```java
private boolean dashing = false;
private double dashTargetX, dashTargetY, dashTargetZ;
private int dashTicksLeft = 0;
private static final double DASH_SPEED = 0.45;  // baby zombie speed

// в tickMovement():
if (dashing) processDash(serverWorld);

private void processDash(ServerWorld world) {
    double dx = dashTargetX - this.getX();
    double dz = dashTargetZ - this.getZ();
    double remaining = Math.sqrt(dx*dx + dz*dz);
    
    if (remaining <= DASH_SPEED || dashTicksLeft <= 0) {
        // Достигли цели — стоп
        this.setVelocity(0, 0, 0);
        this.refreshPositionAndAngles(dashTargetX, dashTargetY, dashTargetZ, ...);
        dashing = false;
        // спавн финального кластера челюстей
        return;
    }
    
    // Двигаем босса на DASH_SPEED в сторону цели
    double moveX = (dx / remaining) * DASH_SPEED;
    double moveZ = (dz / remaining) * DASH_SPEED;
    this.refreshPositionAndAngles(this.getX() + moveX, this.getY(), this.getZ() + moveZ, ...);
    this.setVelocity(moveX, 0, moveZ);  // для плавности на клиенте
    this.velocityDirty = true;
    dashTicksLeft--;
    
    // Постепенный спавн челюстей (по 1 блоку пути)
    // Ломание блоков вокруг текущей позиции
    // Урон в точке босса (через mobAttack — режется бронёй)
}
```

## Скорости мобов (для reference)

| Моб | Скорость (blocks/tick) | Скорость (blocks/sec) |
|---|---|---|
| Обычный зомби | 0.23 | 4.6 |
| Baby zombie | 0.45 | 9.0 |
| Скелет | 0.25 | 5.0 |
| Разоритель (Ravager) | 0.3 | 6.0 |
| Крипер | 0.20 | 4.0 |
| Эндермен | 0.30 | 6.0 |
| Игрок (бег) | 0.22 | 4.3 |
| Игрок (спринт) | 0.28 | 5.6 |

## Тестирование

### 1. Логи
После рестарта сервера проверь `/logs/latest.log` на ошибки mixin:
```bash
grep -E "Mixin apply.*failed|InvalidAccessor|InvalidMixin" /logs/latest.log
```

### 2. Debug-логи
Добавь `LOGGER.info("[MYMOD] action called!")` в начале методов. Проверь:
```bash
grep "\[MYMOD\]" /logs/latest.log
```

### 3. Bedrock совместимость
- Geyser переводит только **ванильные** предметы и компоненты
- `fabric:custom_data` и `fabric:components` ingredient types НЕ работают на Bedrock
- Кастомные NBT-маркеры не видны на Bedrock (но предметы работают)
- Disguise через `createSpawnPacket()` работает (клиент видит ванильного моба)

## Частые ошибки (что НЕ надо делать)

1. ❌ `setVelocity()` без ограничителя дистанции → босс улетает непредсказуемо
2. ❌ Mixin на родителя когда метод переопределён в подклассе → mixin не срабатывает
3. ❌ `@Shadow` для метода из родителя → `InvalidAccessorException`
4. ❌ Reflection для protected методов в production → `NoSuchMethodException`
5. ❌ Одинаковые NBT-ключи в разных модах → конфликты (например, `LootBag:1b` в двух модах)
6. ❌ `custom_name` как JSON-строка с экранированными кавычками → в игре виден сырой JSON
7. ❌ Спавн 80+ челюстей за 1 тик → лаг сервера (делай очередью с задержкой)
8. ❌ Тот же тип урона для всех челюстей → кадры неуязвимости съедают урон (делай mixin на сброс lastDamageTime)
9. ❌ Не очищать BossBar в `onRemoved()` → утечка BossBar при выгрузке чанка
10. ❌ `updateAndGet(lambda)` в AtomicBoolean → не компилируется на fabric-loom

## Полезные команды для разработки

```bash
# Найти класс по имени
grep -P "^c\t[a-z]+\tnet/minecraft/class_1297$" mappings.tiny

# Декомпилировать класс
$JAVA_HOME/bin/javap -p 'net/minecraft/entity/projectile/TridentEntity.class'

# Найти метод в mappings
grep "method_7454" mappings.tiny

# Найти поле в mappings  
grep "field_6226" mappings.tiny

# Собрать мод
./gradlew build --no-daemon

# Загрузить на сервер (через paramiko)
python3 /home/z/my-project/scripts/sftp_upload_<modid>.py
```

## Структура мода

```
<modid>/
├── build.gradle                    # fabric-loom 1.17.21
├── settings.gradle
├── gradle.properties               # MC 1.21.11, yarn 1.21.11+build.6, loader 0.16.10
├── gradle/wrapper/                 # Gradle 9.5.0 wrapper
├── gradlew
└── src/main/
    ├── java/com/<modid>/
    │   ├── <ModId>Mod.java         # entrypoint, регистрация entity/items/commands
    │   ├── <Boss>Entity.java       # extends ванильный mob, BossBar, фазы, спецатаки
    │   ├── <Item>.java             # static create() + is<Item>(stack)
    │   ├── LootBag.java            # Gray Bundle с NBT-маркером
    │   ├── <Give>Command.java      # /<modid>give <item>
    │   └── mixin/
    │       ├── <Entity>HitMixin.java       # @Inject на onEntityHit
    │       ├── <Entity>Invoker.java        # @Invoker для protected методов
    │       └── LivingEntityFangInvulnMixin.java  # сброс неуязвимости для fangs
    └── resources/
        ├── fabric.mod.json         # entrypoints + mixins + depends
        ├── <modid>.mixins.json     # список mixin-классов
        └── data/<modid>/recipe/    # крафты
```

## Связь с автором (Eira)

Eira пишет моды сам, разбирается в Java и Mixin. Когда работаешь с ним:

1. **Спрашивай** про способ реализации, не делай "самый простой вариант" молча
2. **Не предлагай** решение, если оно ломает геймплей (например, телепортация вместо физики)
3. **Объясняй** технические ограничения (например, "fabric:components не работает на Bedrock")
4. **Тестируй** на реальном сервере через SFTP, читай логи
5. **Обновляй** `eira_mods_opensource.zip` после значимых изменений

## Финал

Удачи. Этот проект — серия связанных боссов с уникальными механиками. Каждый босс сложнее предыдущего. Сохраняй консистентность с existing модами (vulcadavmod, larrymod, settlementdestroyer) — общие паттерны, NBT-ключи с префиксами, disguise через createSpawnPacket.

Если сомневаешься — спроси Eira, не додумывай.

— Нейросеть, которая делала settlementdestroyer (с большим трудом и многими итерациями)
