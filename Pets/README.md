# BloomPets

A pet plugin for Paper 26.3 (Java 25). Bond with an animal by feeding it, then summon it with its **Pet Bloom**, ride it, keep things in its storage and level it up to 10. It's a separate plugin from Additional Bosses, but the two know about each other (see the end of this file).

## Getting a pet

**Sneak and right-click** a wild animal with its favourite food. After a few feedings (5 for most, 8 for an Iron Golem, 6 for a Sniffer, 3 for a Vex) it's yours. Hearts above the hotbar show your progress.

Feeding without sneaking still breeds animals as usual.

When an animal bonds with you:

- it gets a cute name (or keeps the name tag it already had);
- you get its **Pet Bloom**, a flower in its category's colour.

## The Pet Bloom

| Do this | What happens |
|---|---|
| Right-click (air or ground) | Summon or dismiss that pet |
| Punch (air, a block or a mob) | Open **all** your pets in one menu, so you only need one bloom |
| Use it on your pet | Ride it (from level 3) |

A bloom only works for its owner. You keep your blooms when you die. A lost bloom is easy to get back: right-click the pet in the menu, or use `/pets bloom`.

The pet menu holds all your pets (up to 27 by default):

- **Left-click** a pet to summon or dismiss it.
- **Right-click** a pet to take its bloom.
- **Shift-click** a pet to open its storage while it's out.

## Your pet

Only one pet can be out at a time. It follows you, keeps up, and pops back to your side if it falls behind, gets stuck, or you teleport or change worlds.

Right-click your pet with:

- **Its food:** heals it by 25%. At most every 30 seconds this also gives bond XP.
- **A name tag:** renames it.
- **Its bloom:** ride it.
- **Anything else:** sends it back into its bloom.

Sneak + right-click your pet to open its storage (or the Vex's gear).

Pets never die. When one runs out of health it **faints**, goes back into its bloom and rests for 60 seconds. It comes back with half its health.

Pets can't be hurt by you, by other players (configurable), by falling or by suffocating. They never hurt players, can't be bred, leashed or stolen, and are never saved into the world: they live in the bloom.

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
| Sniffer | Pack | Torchflower Seeds | Digging dirt sometimes turns up seeds and rare flowers |
| Horse | Speedster | Sugar | +8% movement speed |
| Fox | Speedster | Sweet or Glow Berries | Mobs notice you from 25% shorter range |
| Rabbit | Speedster | Carrot or Dandelion | Jump Boost (II from level 8) |
| Ocelot | Speedster | Tropical Fish | Creepers won't come for you |
| Strider | Speedster | Warped Fungus | Fire Resistance in the Nether |
| Cat | Utility | Cod | Phantoms leave you alone, and you often wake up to a gift |
| Bee | Utility | Any flower | Crops around you grow faster |
| Allay | Utility | Amethyst Shard | Pulls nearby drops to you and bottles loose XP into its storage |
| Chicken | Utility | Seeds | You glide down long drops |
| Cow | Utility | Wheat | Clears one bad effect every 2 minutes |
| Turtle | Utility | Seagrass | You can breathe underwater |
| Armadillo | Utility | Spider Eye | 20% less damage from arrows and other projectiles |
| Frog | Utility | Slime Ball | Dolphin's Grace while swimming |

Every bonus gets a little stronger as the pet levels up (about 45% stronger at level 10).

**The Vex** is the exception:

- It has no storage and can't be ridden.
- It's the only pet that wears armor and a weapon. Sneak + right-click it to open its gear (helmet, chest, legs, boots, weapon).
- Its weapon adds to its damage.

Every species can be turned off in `config.yml`.

## Riding

Pets can be ridden from level 3. Use the pet's bloom on it to get on, steer with WASD, jump with space, sprint for a little extra speed, and sneak to get off. You don't need a saddle.

- **Small pets** (Fox, Rabbit, Ocelot, Cat, Bee, Allay, Chicken, Turtle, Armadillo, Frog) either **grow** big enough to carry you, or **shrink you** to their size. This is decided once, at random, when you bond, and stays the same for that pet.
- **Bees and Allays hover.** Hold jump to rise (3 blocks at most); let go to drift gently down.

## Levels

Level 1 to 10. Bond XP comes from:

| Source | XP |
|---|---|
| Feeding it | 10 (at most every 30 seconds) |
| It kills a mob | 6 |
| You kill a hostile mob while it's out | 3 |
| Riding it | 1 per 25 blocks |
| Spending time together | 2 per minute |

Going from level n to n+1 takes 40 + 30×n XP. Each level gives:

- a little more health;
- a slightly stronger bonus;
- slightly better riding speed and jump;
- more storage at some levels.

## Commands

| Command | |
|---|---|
| `/pets` | Open your pets |
| `/pets summon <name>`, `/pets dismiss` | Summon or dismiss without a bloom |
| `/pets list` | All your pets with their levels |
| `/pets rename <new name>` | Rename the pet that's out |
| `/pets bloom [name]` | Get a pet's bloom back |
| `/pets release <name>` | Say goodbye for good (asks you to confirm). Its storage comes back to you |
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
- the riding level and hover height;
- fainting and rest time;
- bond XP amounts;
- whether other players can hurt your pets;
- whether the Allay bottles XP or just pulls it to you.

Pets are saved per player in `plugins/BloomPets/players/<uuid>.yml`.

## Additional Bosses and mcMMO

- Additional Bosses never turns a pet into a boss. Nemesis bosses don't hunt pets, and the Pariah and Matador curses leave them alone.
- Bosses, boss minions and statues can't be tamed.
- Pet messages above the hotbar make mcMMO's ability bar wait its turn, just like Additional Bosses' messages do.
