# Eira Custom Fabric Mods — Open Source
Server-side-only Fabric mods for Minecraft 1.21.11. Bedrock-compatible via Geyser.
Author: Eira. License: MIT.

## Mods
- totemmod — 5 totems, emerald filter, Warden drops nether star, recovery heart, graves, portable ender chest
- villagermod — Villager economy reform, alchemist career, wandering trader overhaul
- creepermod — Creepers don't destroy blocks, 2x damage
- elytramod — Elytra nerf, craftable, phantom membrane repair only
- firemod — Enhanced fire spread, extra flammable blocks
- fireworkmod — 8 fireworks instead of 3
- modlogmod — Routes totemmod/villagermod logs to logs/mods.log
- adminmod — Auto-restart at 05:00 MSK + manual restart via trigger file
- bettercrafter — Crafter doesn't consume the last item
- larrymod — Larry mini-boss, combo system, sharpened bone weapon, surface-only spawns
- vulcadavmod — Vulcadav 3-phase boss, wolf box, wolf bow, wolf rod, wolf berries, graves, loot bags
- settlementdestroyer — Ravager boss "Settlement Destroyer" with 2 phases, converging fang ring, dash attack, Three Fangs Trident with /dtr PvP toggle
- hellimp — Бес (адский вредин) в Незере, дропает Сердце Демона для расширения слотов аксессуаров (6→7), команда /aks
- leomordmod — Leomord boss (WitherSkeleton base, 3 phases), FireSymbol accessory, ExplosiveSpear, LiftSword, FireBow, depends on hellimp + crownmod
- crownmod — Java resource pack: Royal Crown textures (golden helmet override for Even More Crowns)

## Resource Packs
- crownmod — Java-only resource pack that overrides golden helmet textures with Royal Crown textures. Required by leomordmod for visual consistency.

## Documentation
- `LESSONS_LEARNED.md` — напутствие для следующей нейросети: mixin грабли, паттерны, частые ошибки
- `settlementdestroyer/SETTLEMENT_DESTROYER.md` — игровое описание босса для игроков

## Build
Each mod is a standalone Gradle project. Build order:
  1. totemmod, villagermod, creepermod, elytramod, firemod, fireworkmod, modlogmod, adminmod, bettercrafter (independent)
  2. vulcadavmod (independent)
  3. larrymod (depends on vulcadavmod jar)
  4. settlementdestroyer (independent)

`cd <mod_name> && ./gradlew build`
Output: `build/libs/<mod_name>-1.0.0.jar`

Requirements: JDK 21, Gradle 9.5.0 (wrapper included), internet for Maven deps.

## Commands
- `/totemgive blank|inventory|end|nether|sea|enderchest|all`
- `/vulcgive box|bow|rod|berry|compass|bossbag|all`
- `/larrybone` — gives Larry's Sharpened Bone
- `/summon larry` — summons Larry
- `/summon vulcadav` — summons Vulcadav
- `/summon settlement_destroyer` — summons Settlement Destroyer
- `/destroyergive summon|lootbag|trident|all` — gives custom items
- `/dtr` — toggle Three Fangs trident PvP mode (default OFF)
- `/aks` — opens accessories GUI (6 slots, 7 with Heart of Demon)
- `/hellimpgive cloak|food|gravity|heart|all` — gives hellimp accessories
- `/leomordgive` — gives Leomord items (refer to leomordmod source for details)

## Build order (with dependencies)
1. Independent mods: totemmod, villagermod, creepermod, elytramod, firemod, fireworkmod, modlogmod, adminmod, bettercrafter
2. vulcadavmod (independent)
3. larrymod (depends on vulcadavmod jar)
4. settlementdestroyer (independent)
5. hellimp (independent)
6. crownmod (resource pack, no build needed)
7. leomordmod (depends on hellimp + crownmod jars — place them in `leomordmod/libs/`)

## Server setup
- Minecraft 1.21.11 Fabric
- online-mode=false (don't change)
- Geyser + Floodgate for Bedrock clients
- All mods are `environment: server`
