# Changes in this copy of mcMMO

This is mcMMO 2.3.003 (GPLv3, see LICENSE) with a few server-specific changes. Each changed spot in the code is
marked with a "Server-specific" comment.

- **More XP from bosses** (Additional Bosses plugin): combat XP is multiplied by the boss's rank, with an extra
  bonus for a Nemesis, and reduced for mobs a boss summoned. Set in `experience.yml` under
  `Experience_Values.Combat.Additional_Bosses`. New file: `util/AdditionalBossesHook.java`; changed:
  `util/skills/CombatUtils.java`, `config/experience/ExperienceConfig.java`.
- **Boss names are left alone**: mcMMO's mob health bar no longer replaces the names of Additional Bosses bosses
  or their statues (they already have a boss bar). Changed: `util/MobHealthbarUtils.java`.
- **Compact /mcstats board**: shows only trained skills, highest first, up to 8 (plus power level). Set in
  `config.yml` under `Scoreboard.Compact_Stats`. Changed: `util/scoreboards/ScoreboardWrapper.java`,
  `config/GeneralConfig.java`.
- **Cleaner chat by default**: level-ups and other players' ability alerts show above the hotbar without a chat
  copy (`advanced.yml`, `Feedback.ActionBarNotifications`).
- **Ability charge bar**: a live line above the hotbar for super abilities. A readied tool shows which ability is
  ready, what triggers it and a bar draining until the tool lowers; an active ability counts down its time left;
  and holding a tool whose ability is on cooldown shows the cooldown filling back up. It waits 2 seconds after any
  other mcMMO action bar message, and never paints over Additional Bosses messages (that plugin marks the player
  with `additionalbosses:actionbar_busy_until`). Set in `config.yml` under `Abilities.Charge_Bar`; text in
  `locale_en_US.properties` (`Skills.ChargeBar.*`). New file: `runnables/skills/AbilityChargeBarTask.java`;
  changed: `datatypes/player/McMMOPlayer.java`, `util/player/NotificationManager.java`,
  `runnables/player/PlayerProfileLoadingTask.java`, `config/GeneralConfig.java`, `util/AdditionalBossesHook.java`.
