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
| **Bosses** | Any eligible hostile mob can rise as a boss when it spawns (roll #1), then gets a rank (roll #2), weighted by mob type. Zombies and Skeletons are suspiciously likely to be Legendary. Rideable mobs (skeleton/zombie horses, camel husks...) are never bosses. |
| **6 ranks** | ★ Hard (Gray) · ★★ Very Hard (Green) · ★★★ Brutal (Red) · ★★★★ Nightmare (Purple) · ★★★★★ Legendary (Gold) · ★★★★★★ Ascendant (pearl white, only by promotion). Rank scales health, damage, armor, size, XP, traits and reward odds. |
| **Promotion** | Right-click a boss with a trophy, or throw the trophy at it, and it rises: Green +1, Red +1–2, Purple +1–3, Gold +2–4 (Gray trophies 25% for +1). Ranks can be skipped; past Legendary it becomes **Ascendant**, which breaks into new phases at 66% and 33% health. |
| **Waystones** | Ascendant bosses drop 2 (Gold bosses 3% for 1). Place one and it joins a single public network: every waystone from every player links to every other, free travel after a 3-second warm-up. Name them in an anvil or with a name tag, set a menu icon, and only the owner can break them. |
| **Difficulty + Threat Scaling** | Bosses are 30% tougher and hit 20% harder, and when a fight starts they size up the best-geared nearby player and get up to +60% health / +30% damage, with up to +25% better reward odds. |
| **Reward floor** | Red bosses and up always drop at least one item; below that, 3 empty kills in a row guarantees the next drop. |
| **22 traits** | Offense, Defense, Movement, Control and Special traits that change *how* a boss fights. Some are mob-specific (Volley archers, Volatile creepers). |
| **Trait synergies** | Six trait pairs fuse into a named synergy with 15% stronger traits: Bloodhunter (Swift + Vampiric), Unyielding (Bulwark + Regenerating), Headsman (Berserk + Executioner), Shadowstep (Blinking + Shadowed), Earthbreaker (Gravitic + Quaking), Stormbow (Deadeye + Volley). |
| **Boss tells** | Big attacks are telegraphed: Quaking rears up and shows a red danger ring, Gravitic hums with a violet ring, Leaping crouches first. |
| **Last Stand** | Purple and Gold bosses (and every Nemesis) make a Last Stand once at 25% health: stronger traits, faster, and a dormant trait wakes up. |
| **Rank personality** | Higher ranks notice you from further away, and Red+ bosses surge after a target that backs off. |
| **Anti-trap** | Bosses can't be put in boats or minecarts or leashed, and a boss that can't reach you for 6 seconds tears itself free. |
| **Boss bars** | Shown only to players actually fighting the boss, with rank colour and live health. They hide when combat ends. |
| **Boss armor** | Zombies, skeletons and piglins wear a matching cosmetic set: one material, one trim colour and one trim pattern, from their rank's palette. Nemeses' gear and weapons visibly upgrade as they level. |
| **Presentation** | Rank-coloured names, bigger bodies, subtle particles, sounds and announcements that scale with rank, plus mob-themed flavour (Blazes smoulder, Drowned drip, skeletons shed bone dust). Legendary kills are announced to the whole server. |
| **Rewards** | Extra XP plus *independent* rolls for Boss Gear, Empowerment Runes, Relics and Relic Catalysts. Drops glow, show their name and can't burn or despawn. |
| **Boss Gear** | One rank-branded equipment piece (e.g. `★★★★ Nightmare Diamond Sword`) with enchantments. Includes pickaxes, shovels and hoes, which can roll a weapon enchant like Sharpness. Material (Chainmail → Netherite) and quality (Crude, Standard, Fine, Masterwork) are rolled separately per rank. High ranks can go above vanilla enchantment limits. |
| **Empowerment** | Runes that add a permanent stat (Attack Damage, Max Health, Armor, Speed, Reach...) to any item you choose. **Loot** and **Fortune** runes are multipliers (x1.05–x1.5) on mob drops and ore drops, capped at x3 and exploit-safe. |
| **Relics** | 27 unique abilities you bind permanently to equipment: combat relics (Blood Pact, Second Dawn, Windstep, Stormcaller...), auras (Iron Will, Skybound, Nightstalker, Sunblessed...) and risky "Burdened" relics (Greed, Heavy Crown). 10% are corrupted and also carry one of 16 curses (Terror, Echoes, Reduction, Mother Hen, Matador, Butterfingers, Pariah, Herbivore...). |
| **Relic Catalyst** | Extremely rare Nightmare/Legendary drop that gives an item a second Relic slot. |
| **Hunter's Compass** | Craft a Compass + Eye of Ender + Bone. Hold it to track the nearest boss: distance and an arrow on the action bar, rank only revealed up close, a heartbeat near Purple/Gold/Nemesis bosses. Click Empowerment Runes onto it to upgrade (3 tiers). |
| **Boss Totem** | Rare drop. Right-click for a 3-second ritual and a boss arrives to fight you. |
| **Escalation Chain** | Kill 5 bosses within one Minecraft day and 2 Purple/Gold bosses come hunting you. |
| **Nemesis** | A boss that kills you (or that you flee from after a real fight) becomes *your* Nemesis, with a unique name of its own: `☆ Returned Scrawl the Bulwark [Lv 7]` (history title, name, what it was known for). It prowls off for a few minutes hunting other mobs (+1 level per kill), then returns for you after 3 Minecraft days. It levels up to 50, never drops below its starting rank, earns titles (Returned, Twice-Fled, Relentless, Unbroken...), learns counter-traits against how you fight, keeps its look (baby or adult) every time, and its gear and weapon visibly upgrade. Ignores the boss cap, never despawns, up to 5 per player. Killing it drops Masterwork gear, a rune, boosted relic odds and a **Nemesis Statue** you can place in your base. |
| **Revenge** | Killing a boss that killed you gives double XP and an extra reward roll. |
| **Trophies** | Collectibles named after the boss (Blaze Core, Ravager Horn, Withered Skull...). Gold bosses always drop one. Place one to get a tiny frozen copy of the boss (a tenth of normal size), or use it to promote a boss. |
| **Guide book** | `/bosses guide` gives the *Boss Hunter's Compendium*: a clickable in-game book with a multi-page contents list, generated from your config, explaining everything plus your personal boss record. New players get it automatically. |

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
| `/bosses nemesis` | everyone | Your Nemeses, their level and when they return |
| `/bosses spawn <mob> [rank] [trait,trait]` | admin | Spawn a boss in front of you, e.g. `/bosses spawn zombie gold undying,vampiric` |
| `/bosses give <player> gear [rank] [kind]` | admin | Boss Gear, e.g. `gear purple sword` |
| `/bosses give <player> rune [rank] [stat]` | admin | Empowerment Rune |
| `/bosses give <player> relic [relic\|random] [curse\|none\|random]` | admin | Relic |
| `/bosses give <player> catalyst [amount]` / `guide` | admin | Catalyst or guide book |
| `/bosses give <player> compass [tier]` / `totem [rank]` | admin | Hunter's Compass or Boss Totem |
| `/bosses nemesis list\|summon\|clear <player>` | admin | See, call back early, or remove a player's Nemeses |
| `/bosses escalate <player>` | admin | Trigger an Escalation Chain |
| `/bosses promote [ranks]` | admin | Promote the boss you are looking at |
| `/bosses give <player> waystone [amount]` | admin | Waystones |
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
- `last-stand`, `rank-personality`, `anti-trap`: how bosses behave in a fight.
- `hunters-compass`, `boss-totem`, `escalation`, `trophies`: the hunting features.
- `nemesis`: every number of the Nemesis system (chances, levels, return time, scaling, gear evolution).
- `difficulty`, `reward-floor`, `promotion`, `ascendant`, `waystones`, `boss-armor`: the newer systems.
- `messages`: all player-facing text (MiniMessage).

Nemeses are saved in `plugins/AdditionalBosses/nemesis.yml` and waystones in `waystones.yml`.

New settings always have built-in defaults, so an older `config.yml` keeps working. When a default changes, the plugin edits only that line, and only if you never changed it (it keeps a backup as `config.yml.before-vN`).

> **Updating from an older version?** Your existing `config.yml` is kept and missing sections use the defaults. To see every new option with its comments, rename your old `config.yml`, restart, and copy back anything you changed (such as `spawn-chance`).

---

## Project layout

```
src/main/java/com/additionalbosses/
  AdditionalBosses.java        plugin entry point
  boss/       BossManager (spawning, ranks, stats, lifecycle, ticker, Last Stand, anti-trap, Threat,
              promotion, Ascendant phases), Boss, BossRank, BossBarManager, Presentation, BossMobs, BossArmor
  nemesis/    NemesisManager (becoming, growing, returning, loot), NemesisRecord
  feature/    CompassManager, EscalationManager, FeatureListener (totems, statues, trophies, promotion,
              compass recipe), LootListener (Loot/Fortune runes)
  waystone/   WaystoneManager (network, menu, travel, floating names), WaystoneListener, Waystone
  trait/      BossTrait interface, TraitManager (registry + rolling), Synergies, impl/ (one class per trait)
  reward/     RewardManager (independent rolls), BossGearFactory, GearKind, GearTier, GearQuality
  item/       ItemService (runes, relics, catalysts, compass, totem, applying, lore), Trophies, EquipmentType
  relic/      RelicEffect interface, RelicManager (registry + activation), effects/Relics, effects/Curses
  listener/   BossListener, CombatListener (one damage pipeline), ItemListener, PlayerListener
  command/    BossesCommand (/bosses)
  guide/      GuideBook, BookWriter (page layout)
  config/     PluginSettings (parsed config), RankSettings, MobCategory, MobProfile, EmpowermentStat, Messages
  util/       Keys (all PersistentDataContainer keys), Text, Rng, Fx, PlayerData, SafeSpots, Clock
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
- **Nemeses:** `nemesis.yml` (a Nemesis mob is never saved in the world; its record brings it back).
- **Waystones:** `waystones.yml` (name, owner, location, icon).

### Performance notes
- Bosses are created in the spawn event; nothing scans the world.
- One shared ticker (every 10 ticks) handles every boss and stops itself when no bosses are loaded. Relic passives use one task that only runs while someone has a passive relic equipped.
- Each player's active relics are cached and only re-read when their equipment changes.
- Boss bars, minions and tracking are cleaned up on death, despawn, chunk unload and plugin disable.
