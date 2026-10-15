# QuestBook

One **Quest Book** that shows players everything the server adds, through small one-time quests and skill
milestones, with the detailed guide books one click away. It replaces handing out a pile of books: the Quest Book is
the only one players carry.

Paper 26.3, Java 25. Works on its own; with Additional Bosses, BloomPets and mcMMO it fills in their boards. Nothing
needs a resource pack, and it's simple enough to use from Bedrock (Geyser).

## For players

- Everyone gets a Quest Book the first time they join (players who were already around get one on their next login).
  Right-click it, or type `/quests`. It stays with you when you die. Lost it? `/quests book`.
- **The first page**: a Welcome Gift to claim, the boards (Bosses, Pets, Work Skills, Combat Skills, Misc Skills) and,
  along the bottom, the guide books (Boss Book I and II, Pet Guide), which open on screen without going in your bag.
- **Quest boards**: every quest is visible from the start, one item each, showing what to do and its reward.
  Once it's done, the item says "Done! Click to claim": click it for the reward (it waits until you have room in
  your bag). A claimed quest shines, and shows a short *Insight* about what you just learned.
- **Skill boards** (mcMMO): three milestones per skill (levels 50, 100 and 300; Misc skills 25, 50 and 100). No
  rewards: each milestone says what the skill does around that level. They fill in on their own, including levels
  you already had.
- Things you did before the Quest Book was installed count (boss kills, relics, pets, skill levels...).

## Commands

| Command | |
|---|---|
| `/quests` (`/quest`, `/questbook`) | Open the Quest Book |
| `/quests book` | A new Quest Book, if you lost yours |
| `/quests flag <player> <flag>` | Admin: mark a flag (see below) |
| `/quests reset <player>` | Admin: un-claim everything (to test) |
| `/quests reload` | Admin: reload config.yml and quests.yml |

Permissions: `questbook.use` (everyone), `questbook.admin` (op).

## Config

- `config.yml`: the book (give on first join, keep on death), the Welcome Gift, and the guide books along the bottom
  of the first page (each one is a command run as the player that shows a book). Older config files are updated
  automatically (a backup is kept).
- `quests.yml`: the boards and quests. Everything is there: names, icons, where each sits, objectives, rewards,
  Insights and what completes it. Comments at the top explain the format.

A quest completes when one of its **triggers** is true:

| Trigger | True when |
|---|---|
| `flag:<name>` | another plugin marked that flag on the player (`flag:<name>>=3`: at least 3 times) |
| `item:<namespace:key>` | the player carries an item with that data (`=VALUE` to match a value) |
| `mcmmo:<SKILL>>=<level>` | the player's mcMMO skill level |
| `permission:<node>` | the player has a permission |

Join several with ` & ` to need all of them. `requires:` on a board, quest or reward hides it when something isn't on
the server: `plugin:<Name>`, `config:<Plugin>:<path>` (that plugin's config says true), or `mcmmo`. Without mcMMO, the
skill boards and the mcMMO pet quests don't show; with BloomPets' happiness turned off, "Needs a Break" doesn't show.

## Hooking in another plugin

A plugin tells the Quest Book what a player did by counting up a number on the player, under the `questbook`
namespace. It doesn't need QuestBook on its build path, or even installed:

```java
NamespacedKey key = NamespacedKey.fromString("questbook:found_the_temple");
PersistentDataContainer pdc = player.getPersistentDataContainer();
pdc.set(key, PersistentDataType.INTEGER, pdc.getOrDefault(key, PersistentDataType.INTEGER, 0) + 1);
```

Then add a quest with `triggers: [flag:found_the_temple]` to quests.yml. (Plugins that do depend on QuestBook can call
`com.questbook.QuestFlags.mark(player, "found_the_temple")`; command blocks and scripts can use `/quests flag`.)

Additional Bosses and BloomPets already mark their flags; their READMEs list them.

Progress is saved per player in `plugins/QuestBook/players/<uuid>.yml`. A quest that was done stays done, even if the
item that completed it leaves the player's bag.
