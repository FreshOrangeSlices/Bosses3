# BloomPets

A pet plugin for Paper 26.3 (Java 25). Bond with an animal by feeding it, then call it with your **Pet Toy**, ride it, keep things in its storage and level it up to 10. It's a separate plugin from Additional Bosses, but the two know about each other (see the end of this file).

## Getting a pet

**Sneak and right-click** a wild animal with its favourite food. After a few feedings (5 for most, 8 for an Iron Golem, 6 for a Sniffer, 3 for a Vex) it's yours. Hearts above the hotbar show your progress.

Feeding without sneaking still breeds animals as usual.

When an animal bonds with you:

- it gets a cute name (or keeps the name tag it already had);
- you get a **Pet Toy** if you don't have one yet;
- with your very first pet, you also get the **Pet Guide**, a picture book of every pet and its favourite food.

## The Pet Toy

One item for all your pets (it looks like a popped chorus fruit).

| Do this | What happens |
|---|---|
| Right-click (air or ground) | Call the pet you had out last, or send it home |
| Punch (air, a block or a mob) | Open the pet menu with **all** your pets |
| Use it on your pet | Ride it |

You keep the toy when you die. If you lose it, right-click any of your pets (or use the menu button or `/pets toy`) for a new one, once a minute.

## The pet menu

Holds all your pets (up to 27 by default), each shown as its own flower.

- **Left-click** a pet to summon it (it swaps with the one that's out) or send it home.
- **Shift-click** a pet to open its storage while it's out.
- **Right-click** a pet to take its **Pet Bloom**: an optional flower that calls just that pet. Blooms only work for their owner, and you keep them when you die.
- **The book** opens the Pet Guide.
- **The wool** turns pet PvP on (green) or off (red): whether your combat pet joins your fights with other players. Off by default.
- Each pet shows its **mood** as 1 to 10 hearts.
- **Release pets...** lets you pick any number of pets and say goodbye to all of them at once (it asks twice). Anything they carry comes back to you.

## Your pet

Only one pet can be out at a time. It follows you, keeps up, and pops back to your side if it falls behind, gets stuck, or you teleport or change worlds.

Right-click your pet with:

- **Its food:** heals it by 25%. At most every 30 seconds this also gives bond XP.
- **A name tag:** renames it.
- **Your Pet Toy (or its bloom):** ride it.
- **Anything else:** opens the pet menu, to swap to another pet.

Sneak + right-click your pet **with an empty hand** to pet it. Sneak + right-click it **holding anything else** (the Pet Toy too) to open its storage (or the Vex's gear).

Bees, Allays, Bats and the Vex flit beside your head while you walk, and stop and hover where they are when you stand still, so they're easy to reach.

**Play fetch:** take a Fetch Stick from the pet menu (or `/pets fetch`) and right-click to throw it. Your pet runs after it and brings it back for a little bond XP. Every pet except the Vex and the Iron Golem plays.

## Stations (level 8+)

Sneak and punch your pet with the Pet Toy to leave it at a **post** right where it stands (or use `/pets station`). You can then call out another pet. Up to 3 pets can keep posts at once.

| Pet at its post | Job |
|---|---|
| Combat pets (Wolf, Iron Golem, Polar Bear, Goat, Panda, Vex) | Guard: they attack monsters within 16 blocks of their post (not creepers; endermen and zombified piglins only once they're angry) |
| Bee | Tends the crops around its post |
| Allay | Collects drops and loose XP around its post into its storage, while you're the nearest player |
| Sniffer | Digs up finds around its post now and then |
| Cat, Ocelot | Creepers and phantoms near the post leave everyone alone |
| Llama | Spits at monsters that come close |
| Others | Wait at their post |

Stationed pets stroll around within a few blocks of their post, head off to deal with trouble, and come back afterwards. They keep working while you're away, as long as their spot is loaded. One that faints comes back to its post after resting. Sneak + right-click a stationed pet to open its storage (you have to be at its post). To call it back, sneak + punch it with the Pet Toy, or left-click it in the pet menu.

## Happiness

Every pet has a mood, shown as 1 to 10 hearts in the pet menu, in `/pets list`, and when you feed or pet it. The colour tells you how it's doing: red is sad, gold and yellow are okay, green is happy, and pink means it loves you.

| | |
|---|---|
| Feeding it | Cheers it up a lot (at most once a minute, even when it isn't hurt) |
| Playing fetch | Cheers it up |
| Petting it (sneak + right-click with an empty hand) | Cheers it up a little (every 30 seconds) |
| Resting in its bloom | Cheers it up quickly |
| Being out with you | Slowly brings it down (a bit faster at a post) |

- **Tired:** after about an hour and a half out with you, a pet gets tired and its mood drops three times as fast. A few minutes resting in its bloom fixes that.
- **Loves you:** fill its hearts all the way by feeding, petting or fetch, and it loves you for 30 minutes: its mood drops much slower, and hearts float around it. Fainting ends this early.
- **Sad:** a sad pet droops under a little rain cloud. Until it cheers up, its bonus, its job at a post, its mcMMO skills and its storage are off. Nothing is lost, and it all comes back as soon as it feels better. It still follows you and still fights.

## mcMMO Taming skills

With mcMMO on the server, the combat pets (Iron Golem, Vex, Polar Bear, Goat and Panda) learn skills from **your mcMMO Taming level**, not from the pet's level. They work like mcMMO's wolf skills, with the same numbers, but every animal has its own names and a starting and special skill of its own. Wolves keep mcMMO's own wolf skills. `/pets taming <animal>` lists them with what you've unlocked.

| Taming | Iron Golem | Vex | Polar Bear | Goat | Panda | What it does |
|---|---|---|---|---|---|---|
| 1 | Iron Colossus | Double Strike | Unyielding | Momentum | Bamboo Blessing | See below |
| 100 | Construct Awareness | Phantom Awareness | Arctic Awareness | Mountain Awareness | Bamboo Sense | Steps out of fire, lava and cacti back to you; no fall damage |
| 150 | Crushing Blow | Spectral Rend | Mauling Blow | Headbutt | Heavy Paw | Chance of double damage (Taming / 10 %) |
| 200 | Guardian Intervention | Phase Strike | Crushing Presence | Ram | Soothing Presence | See below |
| 200 | Self Repair | Soul Siphon | Feast | Graze | Snack Break | 50% chance to heal by the damage it deals |
| 250 | Iron Plating | Ethereal Form | Thick Hide | Thick Horns | Dense Fur | Half damage from attacks and arrows (the golem: fire too); shakes off burning |
| 350 | Arcane Core | Voidfed | Winterborn | Mountain Spirit | Inner Balance | Magic, poison and wither heal it instead |
| 500 | Reinforced Frame | Stormproof | Avalanche Proof | Rock Solid | Unshaken | 1/6 damage from explosions and lightning |
| 750 | Forged Fists | Spectral Blade | Rending Claws | Hardened Horns | Heavy Paws | +2 damage on every hit |

- **Iron Colossus:** a walking fortress with 100+ health. **Guardian Intervention:** once per Minecraft day, a blow that would kill you leaves you at half a heart with Resistance II for 20 seconds. Only while the golem follows you (not while it guards a post), and a totem in your hand goes first.
- **Double Strike:** 2% chance to hit twice as hard at Taming 50, +2% every 50 levels, up to 10%. **Phase Strike:** its strikes ignore 25% of the target's armor (50% from Taming 500).
- **Unyielding:** the bear can't be knocked back, slowed or frozen (on top of its bonus for you). **Crushing Presence:** its hits slow (II) and weaken (I) for 5 seconds; Slowness IV and Weakness II from Taming 500.
- **Momentum:** every third hit lands 30% harder. **Ram:** that third hit also launches the target about 6 blocks into the air (players too, with pet PvP on).
- **Bamboo Blessing:** when the panda wins a fight, it and you get Regeneration I for 8 seconds. **Soothing Presence:** a chance on every hit (15% at Taming 200, rising to 60% at 1000) to heal the panda, you and friends within 6 blocks, and hurt the undead: 3 health at 200, +1 every 100 levels, up to 9 at 800.

Your combat pet's hits also give you Taming XP, worked out the way mcMMO does it for a wolf. A pet at its post only earns XP while you're within 30 blocks. A sad pet's skills rest until it cheers up. All the numbers and unlock levels are in the config (`mcmmo:`). On mcMMO's Standard scale (0-100) the levels are divided by 10 automatically. Without mcMMO, none of this does anything.

## Pets in PvP

Pets stay out of fights with other players unless you turn **pet PvP** on with the wool in the pet menu. With it on, your combat pet joins in once a hit between you and another player actually lands (so worlds with PvP off and protected areas still count), and that player can hit your pet back. Pets at a post never attack players, and pets never fight other pets.

## Max level

A level 10 pet:

- sparkles a little and has a gold name;
- gets a **second wind** once each time it comes out: the first blow that would make it faint leaves it at half health instead;
- covers a bigger area at its post.

Pets never die. When one runs out of health it **faints**, goes home and rests for 60 seconds. It comes back with half its health.

Pets can't be hurt by you, by other players (configurable), by falling or by suffocating. They never hurt players, can't be bred, leashed or stolen, and are never saved into the world: they live with you, not in the world.

## Categories

| Category | Moves | Storage |
|---|---|---|
| **Combat** (red) | Slowest. Fights whatever you fight and defends you | Medium |
| **Pack** (gold) | Medium speed, steps up 1.5 blocks, moderate jump | Large (up to a double chest) |
| **Speedster** (aqua) | Fastest, best jump | Small or medium |
| **Utility** (green) | Medium | Small or medium |

Storage grows with level:

- Small: 9 slots, 18 from level 5.
- Medium: 18 slots, 27 from level 6.
- Large: 27 slots, 36 at level 4, 45 at level 7, 54 (a double chest) at level 10.

## The roster

| Pet | Category | Food | Bonus while it's out |
|---|---|---|---|
| Wolf | Combat | Any meat | +8% melee damage |
| Polar Bear | Combat | Salmon | You can't freeze, and Slowness can't touch you |
| Iron Golem | Combat | Iron Ingot | +3 armor |
| Goat | Combat | Wheat | 40% less fall damage |
| Panda | Combat | Bamboo | Stand still a moment and you slowly heal |
| Vex | Combat | Emerald | +10% attack speed |
| Donkey | Pack | Golden Carrot | +2 hearts |
| Mule | Pack | Apple | +30% knockback resistance |
| Llama | Pack | Hay Bale | Spits at mobs that hit you |
| Camel | Pack | Cactus | Speed on sand, soul sand and snow |
| Sniffer | Pack | Torchflower Seeds | Every few minutes (on dirt, sand or gravel, while you are not AFK) it sniffs the ground and digs up treasure: sometimes an armor trim template (rare trims less often, at most 3 an hour), otherwise torchflower seeds, pitcher pods, seeds or flowers. Digging dirt yourself now and then turns up seeds or flowers too |
| Horse | Speedster | Sugar | +8% movement speed |
| Fox | Speedster | Sweet or Glow Berries | Mobs notice you from 25% shorter range |
| Rabbit | Speedster | Carrot or Dandelion | Jump Boost (II from level 8) |
| Ocelot | Speedster | Tropical Fish | Creepers and phantoms leave you alone, and you often wake up to a gift (same as the Cat) |
| Strider | Speedster | Warped Fungus | Fire Resistance in the Nether |
| Cat | Utility | Cod | Creepers and phantoms leave you alone, and you often wake up to a gift (same as the Ocelot) |
| Bee | Utility | Any flower | Crops within 5 blocks of you grow fast: 3 crops a stage every 3 seconds (faster with level; set in `bee:`) |
| Allay | Utility | Amethyst Shard | Pulls nearby drops to you and bottles loose XP into its storage |
| Chicken | Utility | Seeds | You glide down long drops |
| Cow | Utility | Wheat | Clears one bad effect every 2 minutes |
| Turtle | Utility | Seagrass | You can breathe underwater |
| Armadillo | Utility | Spider Eye | 20% less damage from arrows and other projectiles |
| Frog | Utility | Slime Ball | Dolphin's Grace while swimming |
| Pig | Utility | Carrot, Potato or Beetroot | Lumberjack: chop a tree's bottom log with an axe and the whole tree comes down (huge mushrooms and nether trees too). Only trees that grew there (standing on soil, with natural leaves); sneak to chop a single log |
| Axolotl | Utility | Tropical Fish (or a bucket of it) | You slowly heal while hurt, faster in water |
| Bat | Utility | Melon Slice | Night vision |

Every bonus gets a little stronger as the pet levels up (about 45% stronger at level 10).

**The Vex** is the exception:

- It has no storage and can't be ridden.
- It's the only pet that wears armor and a weapon. Sneak + right-click it to open its gear (helmet, chest, legs, boots, weapon).
- Its weapon adds to its damage.

Every species can be turned off in `config.yml`.

## Riding

**Cats, Wolves, Rabbits and Horses** can be ridden right away; every other pet from level 3. Use your Pet Toy on the pet to get on, steer with WASD, jump with space, sprint for a little extra speed, and sneak to get off. You don't need a saddle.

Landings are soft, like on a horse: only half the fall counts, so ordinary jumps never hurt (also for a few seconds after you get off).

- **Small pets** (Fox, Rabbit, Ocelot, Cat, Bee, Allay, Chicken, Turtle, Armadillo, Frog, Axolotl, Bat) either **grow** big enough to carry you, or **shrink you** to their size. This is decided once, at random, when you bond, and stays the same for that pet.
- **Bees, Allays and Bats hover.** Hold jump to rise (3 blocks at most); let go to drift gently down.
- **Turtles, Frogs and Axolotls swim** as fast as a boat. Look down to dive and up to rise, or hold jump to come up.
- **Rabbits hop** along as they go.

## Levels

Level 1 to 10. Bond XP comes from:

| Source | XP |
|---|---|
| Feeding it | 10 (at most every 30 seconds) |
| It kills a mob | 6 |
| You kill a hostile mob while it's out | 3 |
| Riding it | 1 per 10 blocks |
| Walking around with it out | 1 per 20 blocks |
| Playing fetch | 2 (at most every 15 seconds) |
| Every hit it lands in a fight | 1, or 2 for a big hit (at most every 1.5 seconds). A pet at its post only earns while you're within 30 blocks. The fastest way to level up |
| Spending time together | 2 per minute |

Going from level n to n+1 takes 40 + 30×n XP. Each level gives:

- more health: every pet reaches at least 20 at level 10 (combat pets 40), and sturdier ones like the Iron Golem keep their own, plus a little;
- a slightly stronger bonus;
- slightly better riding speed and jump;
- more storage at some levels.

## Commands

| Command | |
|---|---|
| `/pets` | Open your pets |
| `/pets summon <name>`, `/pets dismiss` | Summon or dismiss by name |
| `/pets list` | All your pets with their levels |
| `/pets rename <new name>` | Rename the pet that's out |
| `/pets toy` | A new Pet Toy, if you lost yours |
| `/pets bloom [name]` | A pet's own bloom |
| `/pets guide` | Read the Pet Guide |
| `/pets station` | Leave the pet that's out at a post (level 8+) |
| `/pets fetch` | A Fetch Stick |
| `/pets taming <animal>` | A combat pet's mcMMO Taming skills, and which ones you've unlocked |
| `/pets release <name>[, <name>...]` | Say goodbye to one or more pets for good (asks you to confirm). What they carry comes back to you |
| `/pets give <player> <species> [level]` | Admin: give someone a pet |
| `/pets reload` | Admin: reload `config.yml` |

`/pet` works too.

Permissions:

- `bloompets.use` (everyone) covers taming, summoning, riding and the player commands.
- `bloompets.admin` (op) covers `give` and `reload`.

## Config

`plugins/BloomPets/config.yml` covers:

- the most pets per player;
- which species can be pets;
- how many feedings each one takes;
- healing from food;
- the riding level, which pets can be ridden right away, and the hover height;
- fainting and rest time;
- bond XP amounts;
- whether other players can hurt your pets;
- whether the Allay bottles XP or just pulls it to you;
- the Bee's area, crops per pulse and speed (`bee:`);
- stations: the level they unlock at, how many per player, how far guards chase, and how far pets stroll around their post (`stationing:`);
- whether players get the pet PvP button (`pet-pvp:`);
- every happiness number (`happiness:`);
- the mcMMO Taming skills: unlock levels, chances, damage and every animal's numbers (`mcmmo:`).

Existing config files are updated automatically when the plugin starts (a backup is kept as `config.yml.before-v3`).

Pets are saved per player in `plugins/BloomPets/players/<uuid>.yml`.

## Additional Bosses and mcMMO

- Additional Bosses never turns a pet into a boss. Nemesis bosses don't hunt pets, and the Pariah and Matador curses leave them alone.
- Bosses, boss minions and statues can't be tamed.
- Pet messages above the hotbar make mcMMO's ability bar wait its turn, just like Additional Bosses' messages do.
- mcMMO is optional. With it, combat pets get Taming skills and give Taming XP (see above). BloomPets only uses mcMMO's public API and never changes mcMMO.
