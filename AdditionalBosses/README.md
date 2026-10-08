# Additional Bosses

A Paper plugin for **Minecraft 26.3** that makes normal survival occasionally produce dangerous, memorable boss fights, without turning the game into a separate RPG.

**Normal mob spawns → small chance to become a Boss → rank + traits → you fight it → extra XP and independent reward rolls → you build stronger, personalised gear.**

```
★★★★★ Legendary Undying Zombie
```

---

## Features

| System | What it does |
|---|---|
| **Bosses** | Any eligible hostile mob can rise as a boss when it spawns (roll #1), then gets a rank (roll #2), weighted by mob type. Zombies and Skeletons are suspiciously likely to be Legendary. |
| **5 ranks** | ★ Hard (Gray) · ★★ Very Hard (Green) · ★★★ Brutal (Red) · ★★★★ Nightmare (Purple) · ★★★★★ Legendary (Gold). Rank scales health, damage, armor, size, XP, traits and reward odds. |
| **22 traits** | Offense, Defense, Movement, Control and Special traits that change *how* a boss fights. Some are mob-specific (Volley archers, Volatile creepers). |
| **Boss bars** | Shown only to players actually fighting the boss, with rank colour and live health. They hide when combat ends. |
| **Presentation** | Rank-coloured names, cosmetic dyed armor, subtle particles, sounds and announcements that scale with rank. Legendary kills are announced to the whole server. |
| **Rewards** | Extra XP plus *independent* rolls for Boss Gear, Empowerment Runes, Relics and Relic Catalysts. Drops glow, show their name and can't burn or despawn. |
| **Boss Gear** | One rank-branded equipment piece (e.g. `★★★★ Nightmare Diamond Sword`) with enchantments. High ranks can go above vanilla enchantment limits. |
| **Empowerment** | Runes that add a permanent stat (Attack Damage, Max Health, Armor, Speed...) to any item you choose. |
| **Relics** | 15 unique abilities you bind permanently to equipment (Blood Pact, Second Dawn, Windstep, Stormcaller...). 10% are corrupted and also carry one of 9 curses (Butterfingers, Fowl Omen, Insomnia...). |
| **Relic Catalyst** | Extremely rare Nightmare/Legendary drop that gives an item a second Relic slot. |
| **Guide book** | `/bosses guide` gives the *Boss Hunter's Compendium*: a clickable in-game book, generated from your config, explaining everything plus your personal boss record. New players get it automatically. |

A finished item can stack every layer:

```
★★★★ Nightmare Diamond Sword
Sharpness VI, Unbreaking III
Empowered: +2 Attack Damage
Relic: Blood Pact
Curse: Dread
```

---

## Build it with GitHub (no software needed on your PC)

This repository includes a GitHub Actions workflow that compiles the plugin for you.

1. Upload **all** files in this folder to your GitHub repository, including the hidden `.github` folder and `gradle/wrapper/gradle-wrapper.jar`.
2. Open the **Actions** tab. The **Build plugin** workflow runs on every push (or press **Run workflow**).
3. When it finishes (green check), open the run and download **AdditionalBosses** under *Artifacts*. Unzip it to get `AdditionalBosses-1.0.0.jar`.
4. Optional: create a tag such as `v1.0.0` (Releases → Draft a new release → new tag). The workflow attaches the jar to that Release automatically.

> **Actions tab is empty?** The `.github` folder was probably skipped during upload (it's hidden on many computers). In GitHub, click **Add file → Create new file**, name it `.github/workflows/build.yml`, and paste in the contents of that file from this project.

Build locally instead (needs Java 25): `./gradlew build` → `build/libs/AdditionalBosses-1.0.0.jar`.

## Install

1. Server: **Paper 26.3** running on **Java 25**.
2. Drop the jar into `plugins/` and restart.
3. Tweak `plugins/AdditionalBosses/config.yml`, then `/bosses reload`.

---

## Commands

| Command | Who | What |
|---|---|---|
| `/bosses guide` | everyone | Get the Boss Hunter's Compendium |
| `/bosses apply` | everyone | Use the Rune/Relic/Catalyst in your off hand on the item in your main hand |
| `/bosses inspect` | everyone | Look at a boss to see its traits, or inspect your held item |
| `/bosses stats` | everyone | Your boss kills by rank |
| `/bosses spawn <mob> [rank] [trait,trait]` | admin | Spawn a boss in front of you, e.g. `/bosses spawn zombie gold undying,vampiric` |
| `/bosses give <player> gear [rank] [kind]` | admin | Boss Gear, e.g. `gear purple sword` |
| `/bosses give <player> rune [rank] [stat]` | admin | Empowerment Rune |
| `/bosses give <player> relic [relic\|random] [curse\|none\|random]` | admin | Relic |
| `/bosses give <player> catalyst [amount]` / `guide` | admin | Catalyst or guide book |
| `/bosses list` · `killall` · `reload` | admin | Admin tools |

Aliases: `/ab`, `/boss`.

| Permission | Default |
|---|---|
| `additionalbosses.use` | everyone |
| `additionalbosses.admin` | operators |

**Applying runes and relics:** pick the item up in your inventory and click it onto a piece of equipment, or hold the equipment in your main hand and the rune/relic in your off hand and run `/bosses apply`. Relics and Catalysts are permanent, so you're asked to do it twice to confirm.

---

## Configuration

Everything balance-related is in `config.yml`, with comments. Highlights:

- `bosses.spawn-chance`: roll #1 (default 0.5%), plus caps, worlds and spawn reasons.
- `ranks.<RANK>`: stats, trait count and power, XP, reward chances, Boss Gear materials and enchant ceilings, presentation.
- `mob-categories`: which mobs can become bosses and their rank weights (roll #2). The Warden/Wither category exists but is off by default.
- `mob-profiles`: how strongly rank stats apply to creepers, small, large and flying mobs.
- `traits`: enable/disable, weights, numbers, and incompatible pairs.
- `boss-gear`, `empowerment`, `relics`: item weights, stat ranges, how many runes per item, corruption chance, catalyst slots, and every relic and curse.
- `messages`: all player-facing text (MiniMessage).

---

## Project layout

```
src/main/java/com/additionalbosses/
  AdditionalBosses.java        plugin entry point
  boss/       BossManager (spawning, ranks, stats, lifecycle, ticker), Boss, BossRank,
              BossBarManager, Presentation
  trait/      BossTrait interface, TraitManager (registry + rolling), impl/ (one class per trait)
  reward/     RewardManager (independent rolls), BossGearFactory, GearKind, GearTier
  item/       ItemService (runes, relics, catalysts, applying, lore), EquipmentType
  relic/      RelicEffect interface, RelicManager (registry + activation), effects/Relics, effects/Curses
  listener/   BossListener, CombatListener (one damage pipeline), ItemListener, PlayerListener
  command/    BossesCommand (/bosses)
  guide/      GuideBook, BookWriter (page layout)
  config/     PluginSettings (parsed config), RankSettings, MobCategory, MobProfile, EmpowermentStat, Messages
  util/       Keys (all PersistentDataContainer keys), Text, Rng, Fx, PlayerData
```

### Adding a trait
1. Create a class in `trait/impl/` extending `BaseTrait` and override the hooks you need (`onTick`, `onAttack`, `afterAttack`, `onDamaged`, `afterDamaged`, `onProjectileLaunch`, `onLethalDamage`...).
2. Register it in `TraitManager.registerDefaults()`.
3. Add `traits.<id>` to `config.yml` with its weight and any numbers your `load()` reads.

### Adding a relic or curse
1. Add a class to `relic/effects/Relics.java` (or `Curses.java`) extending `BaseRelic`.
2. Register it in `RelicManager.registerDefaults()`.
3. Add it under `relics.effects` or `relics.curses` in `config.yml`.

### How data is stored
Everything lives in PersistentDataContainers, so it survives restarts and chunk reloads:
- **Boss mobs:** rank, trait ids, category, Undying-used flag. Stat changes are attribute modifiers saved with the mob.
- **Items:** item kind, rank, rune stat/amount, relic/curse ids, list of empowerments, relics, curses, relic slot count.
- **Players:** kills per rank, relics bound, Second Dawn cooldown, guide-received flag.

### Performance notes
- Bosses are created in the spawn event; nothing scans the world.
- One shared ticker (every 10 ticks) handles every boss and stops itself when no bosses are loaded. Relic passives use one task that only runs while someone has a passive relic equipped.
- Each player's active relics are cached and only re-read when their equipment changes.
- Boss bars, minions and tracking are cleaned up on death, despawn, chunk unload and plugin disable.
