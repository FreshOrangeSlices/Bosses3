package com.additionalbosses.guide;

import com.additionalbosses.AdditionalBosses;
import com.additionalbosses.boss.BossRank;
import com.additionalbosses.config.EmpowermentStat;
import com.additionalbosses.config.FeatureSettings;
import com.additionalbosses.config.MobCategory;
import com.additionalbosses.config.PluginSettings;
import com.additionalbosses.config.RankSettings;
import com.additionalbosses.item.ItemService;
import com.additionalbosses.nemesis.NemesisRecord;
import com.additionalbosses.relic.RelicEffect;
import com.additionalbosses.trait.BossTrait;
import com.additionalbosses.trait.Synergies;
import com.additionalbosses.trait.TraitCategory;
import com.additionalbosses.util.Keys;
import com.additionalbosses.util.PlayerData;
import com.additionalbosses.util.Text;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.WrittenBookContent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Builds the "Boss Hunter's Compendium": an in-game written book that explains everything the plugin adds.
 * It is generated from the live config, so every number in it matches the server's actual settings, and it ends
 * with the reader's own boss-hunting record.
 */
public final class GuideBook {

    private static final TextColor INK = NamedTextColor.BLACK;
    private static final TextColor SOFT = NamedTextColor.DARK_GRAY;
    private static final TextColor TITLE = TextColor.color(0x7A1F1F);
    private static final TextColor RELIC = NamedTextColor.DARK_PURPLE;
    private static final TextColor CURSE = NamedTextColor.DARK_RED;
    private static final TextColor EMPOWER = NamedTextColor.DARK_AQUA;

    private final AdditionalBosses plugin;

    public GuideBook(AdditionalBosses plugin) {
        this.plugin = plugin;
    }

    /** Gives the guide (one book, or two volumes if it ever grows past the 100-page book limit). */
    public void give(Player player) {
        for (ItemStack book : create(player)) {
            for (ItemStack left : player.getInventory().addItem(book).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
        }
        player.sendMessage(plugin.settings().messages.prefixed("guide-received"));
    }

    public List<ItemStack> create(Player reader) {
        PluginSettings s = plugin.settings();
        BookWriter w = new BookWriter();
        Map<String, Integer> sections = new LinkedHashMap<>();

        // ---------------- How bosses appear ----------------
        sections.put("How Bosses Appear", w.currentPage());
        w.heading("How Bosses Appear", TITLE);
        w.text("When a hostile mob spawns, it has a small chance (" + Text.num(s.spawnChance)
            + "%) to rise as a boss.", INK);
        w.blank();
        w.text("A second, separate roll decides its rank. Some mobs lean toward stronger ranks than others.", INK);
        w.blank();
        w.text("You'll know one when you see it:", INK);
        w.add(Component.text("★★★★★ Legendary Undying Zombie", BossRank.GOLD.bookColor()).decorate(TextDecoration.BOLD), true);
        w.blank();
        w.text("Bosses are bigger" + (s.features.armorSets ? ", wear matching trimmed armor" : "")
            + " and shimmer faintly. Fight one and its health bar appears at the top of your screen.", INK);
        w.blank();
        w.text("At most " + s.maxActive + " bosses can be loaded at once.", SOFT);
        w.newPage();

        // ---------------- Ranks ----------------
        sections.put("Boss Ranks", w.currentPage());
        w.heading("Boss Ranks", TITLE);
        for (BossRank rank : BossRank.values()) {
            RankSettings rs = s.rank(rank);
            w.reserve(4);
            w.add(Component.text(rs.title(), rank.bookColor()).decorate(TextDecoration.BOLD), true);
            if (!rank.natural()) {
                w.text("Only reached by promoting a boss with trophies.", SOFT);
            }
            w.text("Health x" + Text.num(rs.stats().health()) + ", damage x" + Text.num(rs.stats().damage()), INK);
            String traits = rs.traits().min() == rs.traits().max() ? String.valueOf(rs.traits().min())
                : rs.traits().min() + "-" + rs.traits().max();
            w.text("Traits: " + traits + "  XP: x" + Text.num(rs.xpMultiplier()) + " +" + rs.xpBonus(), SOFT);
            w.blank();
        }
        w.newPage();

        // ---------------- Rank odds ----------------
        sections.put("Rank Odds by Mob", w.currentPage());
        w.heading("Rank Odds by Mob", TITLE);
        Component legend = Component.empty();
        for (BossRank rank : BossRank.values()) {
            if (!rank.natural()) {
                continue;
            }
            if (rank.ordinal() > 0) {
                legend = legend.append(Component.text("/", SOFT));
            }
            legend = legend.append(Component.text(Text.pretty(rank.name()), rank.bookColor()));
        }
        w.add(legend, false);
        w.blank();
        for (MobCategory category : s.categories) {
            w.reserve(4);
            w.add(Component.text(category.displayName() + (category.enabled() ? "" : " (off)"), INK)
                .decorate(TextDecoration.BOLD), true);
            TextComponent.Builder odds = Component.text();
            for (BossRank rank : BossRank.values()) {
                if (!rank.natural()) {
                    continue;
                }
                if (rank.ordinal() > 0) {
                    odds.append(Component.text("/", SOFT));
                }
                odds.append(Component.text(Text.num(category.percent(rank)), rank.bookColor()));
            }
            odds.append(Component.text(" %", SOFT));
            w.add(odds.build(), false);
            if (!category.description().isBlank()) {
                w.text(category.description(), SOFT);
            }
            w.text("e.g. " + examples(category), SOFT);
            w.blank();
        }
        w.newPage();

        // ---------------- Traits ----------------
        sections.put("Traits", w.currentPage());
        w.heading("Traits", TITLE);
        w.text("Traits change how a boss fights. Higher ranks get more of them, and stronger.", INK);
        w.blank();
        StringJoiner perRank = new StringJoiner(" / ");
        for (BossRank rank : BossRank.values()) {
            RankSettings.TraitRoll tr = s.rank(rank).traits();
            perRank.add(tr.min() == tr.max() ? String.valueOf(tr.min()) : tr.min() + "-" + tr.max());
        }
        w.text("Per rank: " + perRank, SOFT);
        w.text("The first trait names the boss: a Vampiric zombie becomes a Ravenous Zombie.", SOFT);
        for (TraitCategory category : TraitCategory.values()) {
            List<BossTrait> traits = new ArrayList<>();
            for (BossTrait t : plugin.traits().enabledTraits()) {
                if (t.category() == category) {
                    traits.add(t);
                }
            }
            if (traits.isEmpty()) {
                continue;
            }
            w.newPage();
            w.heading(category.displayName(), category.bookColor());
            for (BossTrait t : traits) {
                w.reserve(4);
                String name = t.displayName().equals(t.adjective()) ? t.displayName()
                    : t.displayName() + " (\"" + t.adjective() + "\")";
                w.add(Component.text(name, category.bookColor()).decorate(TextDecoration.BOLD), true);
                w.text(t.description(), INK);
                w.blank();
            }
        }
        w.newPage();

        // ---------------- Synergies + tells ----------------
        sections.put("Synergies & Tells", w.currentPage());
        w.heading("Synergies", TITLE);
        w.text("Some trait pairs fuse. The boss is named after the synergy and its traits hit "
            + Math.round((Synergies.POWER_BONUS - 1) * 100) + "% harder.", INK);
        w.blank();
        for (Synergies.Synergy syn : Synergies.ALL) {
            BossTrait a = plugin.traits().get(syn.first());
            BossTrait b = plugin.traits().get(syn.second());
            if (a == null || b == null || !plugin.traits().isEnabled(a) || !plugin.traits().isEnabled(b)) {
                continue;
            }
            w.reserve(2);
            w.add(Component.text(syn.title(), TITLE).decorate(TextDecoration.BOLD), true);
            w.text(a.displayName() + " + " + b.displayName(), SOFT);
        }
        w.newPage();
        w.heading("Boss Tells", TITLE);
        w.text("Big attacks are telegraphed. Watch for them and move:", INK);
        w.blank();
        w.entry("Quaking", TITLE, "it rears up and a red ring shows where the slam lands.", INK);
        w.entry("Gravitic", TITLE, "a violet ring and a low hum before the pull.", INK);
        w.entry("Leaping", TITLE, "it crouches and scrapes the ground before it jumps.", INK);
        w.entry("Charging", TITLE, "it roars before it rushes you.", INK);
        w.newPage();

        // ---------------- Last Stand + anti-trap ----------------
        FeatureSettings f = s.features;
        sections.put("Last Stand", w.currentPage());
        w.heading("Last Stand", TITLE);
        StringJoiner lsRanks = new StringJoiner(" and ");
        for (BossRank rank : f.lastStandRanks) {
            lsRanks.add(s.rank(rank).name());
        }
        w.text((lsRanks.length() == 0 ? "Nemesis" : lsRanks + " bosses and every Nemesis") + " make a Last Stand once, at "
            + Text.num(f.lastStandHealthPercent) + "% health:", INK);
        w.text("- traits " + Text.num(f.lastStandPowerBonus) + "% stronger", INK);
        w.text("- " + Text.num(f.lastStandSpeedBonus) + "% faster", INK);
        if (f.lastStandDormantTrait) {
            w.text("- a dormant trait awakens", INK);
        }
        w.blank();
        w.text("No cheese: bosses can't be put in boats, minecarts or on leads, and a boss that can't reach you"
            + " for a few seconds tears itself free.", SOFT);
        w.newPage();

        // ---------------- Rewards ----------------
        sections.put("Rewards", w.currentPage());
        w.heading("Rewards", TITLE);
        w.text("Each reward has its own roll, so a single boss can drop several at once. Bosses always give extra XP.", INK);
        w.blank();
        for (BossRank rank : BossRank.values()) {
            RankSettings rs = s.rank(rank);
            RankSettings.Rewards r = rs.rewards();
            w.reserve(4);
            w.add(Component.text(rs.title(), rank.bookColor()).decorate(TextDecoration.BOLD), true);
            w.text("Gear " + Text.num(r.bossGear()) + "%  Rune " + Text.num(r.empowerment()) + "%", INK);
            String line = "Relic " + Text.num(r.relic()) + "%";
            if (s.catalystEnabled && r.relicCatalyst() > 0) {
                line += "  Catalyst " + Text.num(r.relicCatalyst()) + "%";
            }
            w.text(line, INK);
            w.blank();
        }
        FeatureSettings fs = s.features;
        if (fs.rewardFloorFrom != null) {
            w.text(s.rank(fs.rewardFloorFrom).name() + " bosses and up always drop at least one item.", INK);
        }
        if (fs.pityAfter > 0) {
            w.text("Below that, after " + fs.pityAfter + " empty kills in a row the next one is guaranteed.", INK);
        }
        w.text("Reward drops glow, float their name, and can't burn or despawn.", SOFT);
        w.newPage();

        // ---------------- Boss Gear ----------------
        sections.put("Boss Gear", w.currentPage());
        w.heading("Boss Gear", TITLE);
        w.text("A boss can drop one piece of special equipment, shown with its rank colour and stars:", INK);
        w.add(Component.text("★★★★ Nightmare Diamond Sword", BossRank.PURPLE.bookColor()), false);
        w.blank();
        w.text("Higher ranks bring better materials, more enchantments and higher levels.", INK);
        StringJoiner overMax = new StringJoiner(", ");
        for (BossRank rank : BossRank.values()) {
            RankSettings.Gear g = s.rank(rank).gear();
            if (g.overMaxLevels() > 0 && g.overMaxChance() > 0) {
                overMax.add(s.rank(rank).name());
            }
        }
        if (overMax.length() > 0) {
            w.blank();
            w.text(overMax + " gear can even roll enchantments above the normal maximum.", INK);
        }
        w.blank();
        w.text("Material and quality are rolled separately. Quality: Crude, Standard, Fine or Masterwork"
            + " (more and stronger enchantments).", INK);
        w.newPage();
        w.heading("Gear by Rank", TITLE);
        for (BossRank rank : BossRank.values()) {
            RankSettings.Gear g = s.rank(rank).gear();
            w.reserve(3);
            w.add(Component.text(s.rank(rank).name(), rank.bookColor()).decorate(TextDecoration.BOLD), true);
            w.text(weights(g.materials()), INK);
            w.blank();
        }
        w.text("Pickaxes, shovels and hoes drop too, and can carry a weapon enchant like Sharpness.", INK);
        if (s.features.armorSets) {
            w.text("The armor a boss wears is just for show and never drops.", SOFT);
        }
        w.newPage();

        // ---------------- Empowerment ----------------
        sections.put("Empowerment", w.currentPage());
        w.heading("Empowerment", TITLE);
        w.text("Empowerment Runes add a permanent stat bonus to an item you choose.", INK);
        w.blank();
        w.add(Component.text("How to use:", EMPOWER).decorate(TextDecoration.BOLD), true);
        w.text("Pick the rune up in your inventory and click it onto the item.", INK);
        w.text("Or: item in main hand, rune in off hand, then /bosses apply.", SOFT);
        w.blank();
        w.text("Up to " + s.empowermentMaxPerItem + " per item. They stack with enchantments and relics.", INK);
        w.newPage();
        w.heading("Rune Stats", EMPOWER);
        for (EmpowermentStat stat : s.empowermentStats) {
            w.reserve(4);
            w.add(Component.text(stat.displayName(), EMPOWER).decorate(TextDecoration.BOLD), true);
            w.text("Fits: " + stat.fitsText(), INK);
            w.text(rangeLine(stat), SOFT);
            w.blank();
        }
        w.newPage();

        // ---------------- Relics ----------------
        sections.put("Relics", w.currentPage());
        w.heading("Relics", TITLE);
        w.text("Relics grant a unique ability to the equipment you bind them to.", INK);
        w.blank();
        w.text("- " + s.relicBaseSlots + " Relic per item", INK);
        w.text("- Binding is permanent", INK);
        w.text("- Held weapons/tools and worn armor or shields activate them", INK);
        w.text("- Some act differently in hand and on armor", INK);
        w.blank();
        w.text("Apply them like runes. You'll be asked to confirm first.", SOFT);
        w.newPage();
        for (RelicEffect relic : plugin.relics().enabledEffects(false)) {
            w.reserve(4);
            w.add(Component.text("✦ " + relic.displayName(), RELIC).decorate(TextDecoration.BOLD), true);
            w.text(relic.description(), INK);
            w.blank();
        }
        w.newPage();

        // ---------------- Curses ----------------
        sections.put("Curses", w.currentPage());
        w.heading("Corruption", CURSE);
        w.text("Every Relic always has its good effect. But " + Text.num(s.corruptionChance)
            + "% are corrupted and carry a Curse as well.", INK);
        w.blank();
        w.text(s.revealCorruption ? "Corrupted relics are marked before you bind them."
            : "You only find out when you bind it...", INK);
        w.blank();
        w.text("A curse doesn't use up a relic slot. Cursed gear can be powerful, but weird.", SOFT);
        w.newPage();
        for (RelicEffect curse : plugin.relics().enabledEffects(true)) {
            w.reserve(4);
            w.add(Component.text("☠ " + curse.displayName(), CURSE).decorate(TextDecoration.BOLD), true);
            w.text(curse.description(), INK);
            w.blank();
        }
        w.newPage();

        // ---------------- Catalyst ----------------
        if (s.catalystEnabled) {
            sections.put("Relic Catalyst", w.currentPage());
            w.heading("Relic Catalyst", TITLE);
            w.text("An extremely rare drop that gives a piece of equipment one more Relic slot.", INK);
            w.blank();
            w.text("Maximum " + s.catalystMaxSlots + " Relic slots per item.", INK);
            w.blank();
            StringJoiner from = new StringJoiner(", ");
            for (BossRank rank : BossRank.values()) {
                if (s.rank(rank).rewards().relicCatalyst() > 0) {
                    from.add(s.rank(rank).name());
                }
            }
            w.text("Dropped by: " + (from.length() == 0 ? "nobody (yet)" : from.toString()) + " bosses.", SOFT);
            w.newPage();
        }

        // ---------------- The Hunt: compass, totem, escalation, trophies ----------------
        sections.put("The Hunt", w.currentPage());
        w.heading("The Hunt", TITLE);
        if (f.compassEnabled) {
            w.add(Component.text("Hunter's Compass", TITLE).decorate(TextDecoration.BOLD), true);
            w.text("Hold it to track the nearest boss: distance and which way to turn.", INK);
            if (f.compassRecipe) {
                w.text("Craft: Compass + Eye of Ender + Bone.", SOFT);
            }
            w.text("Upgrade it by clicking an Empowerment Rune onto it.", SOFT);
            w.blank();
            for (int i = 1; i <= f.compassTiers.size(); i++) {
                FeatureSettings.CompassTier t = f.compassTier(i);
                w.text("Tier " + Text.roman(i) + ": " + Math.round(t.range()) + " blocks, rank seen within "
                    + Math.round(t.revealDistance()), INK);
            }
            w.blank();
            w.text("It pulses like a heartbeat near Purple, Gold and Nemesis bosses.", SOFT);
            w.newPage();
        }
        if (f.totemEnabled) {
            w.add(Component.text("Boss Totem", TITLE).decorate(TextDecoration.BOLD), true);
            w.text("A rare drop (" + Text.num(f.totemDropChance.getOrDefault(BossRank.GRAY, 0.0)) + "-"
                + Text.num(f.totemDropChance.getOrDefault(BossRank.GOLD, 0.0)) + "% per boss). Right-click it and,"
                + " after a short ritual, a boss arrives to fight you.", INK);
            w.blank();
        }
        if (f.escalationEnabled) {
            w.reserve(5);
            w.add(Component.text("Escalation", TITLE).decorate(TextDecoration.BOLD), true);
            w.text("Kill " + f.escalationKills + " bosses within " + Text.num(f.escalationWindowDays)
                + " Minecraft day" + (f.escalationWindowDays == 1 ? "" : "s") + " and " + f.escalationBosses
                + " powerful bosses come for you.", INK);
            w.blank();
        }
        if (f.trophiesEnabled) {
            w.reserve(5);
            w.add(Component.text("Trophies", TITLE).decorate(TextDecoration.BOLD), true);
            w.text("Bosses sometimes leave a trophy: a Blaze Core, a Ravager Horn, a Withered Skull... Gold"
                + " bosses always do." + (f.trophyPlacing ? " Place it to get a tiny copy of the boss." : ""), INK);
        }
        w.newPage();

        // ---------------- Difficulty ----------------
        sections.put("Threat & Difficulty", w.currentPage());
        w.heading("Threat", TITLE);
        w.text("Bosses are " + Math.round((f.difficultyHealth - 1) * 100) + "% tougher and hit "
            + Math.round((f.difficultyDamage - 1) * 100) + "% harder than their rank alone.", INK);
        if (f.threatEnabled) {
            w.blank();
            w.text("When a fight starts, the boss sizes up the best-geared player nearby. Strong gear means up to +"
                + Math.round(f.threatMaxHealth) + "% health and +" + Math.round(f.threatMaxDamage)
                + "% damage, but also up to +" + Math.round(f.threatRewardBonus) + "% better reward odds.", INK);
        }
        w.newPage();

        // ---------------- Promotion + Ascendant ----------------
        if (f.promotionEnabled) {
            sections.put("Promotion & Ascendant", w.currentPage());
            w.heading("Promotion", TITLE);
            w.text("Right-click a boss with a trophy, or throw (drop) the trophy at it, and the boss rises."
                + " Ranks can be skipped:", INK);
            w.blank();
            for (BossRank rank : BossRank.values()) {
                int[] r = f.promotionSteps.get(rank);
                if (r == null) {
                    continue;
                }
                String text = r[0] == r[1] ? "+" + r[0] : "+" + r[0] + " to +" + r[1];
                if (rank == BossRank.GRAY) {
                    text += " (" + Text.num(f.grayPromotionChance) + "% chance)";
                }
                w.add(Component.text(s.rank(rank).name() + " trophy: ", rank.bookColor()).append(Component.text(text, INK)), false);
            }
            w.blank();
            w.text("It heals fully and gains traits for its new rank. Nemeses can't be promoted.", SOFT);
            w.newPage();
            w.heading("Ascendant", BossRank.ASCENDANT.bookColor());
            w.text("Promote a boss past Legendary and it becomes Ascendant (6 stars): the strongest rank,"
                + " with top-tier loot.", INK);
            if (f.ascendantPhases) {
                w.blank();
                w.text("At " + phases(f) + "% health it breaks into a new phase: a shockwave and a new trait.", INK);
            }
            if (f.waystonesEnabled && f.waystoneAscendantDrops > 0) {
                w.blank();
                w.text("It drops " + f.waystoneAscendantDrops + " Waystones.", INK);
            }
            w.newPage();
        }

        // ---------------- Waystones ----------------
        if (f.waystonesEnabled) {
            sections.put("Waystones", w.currentPage());
            w.heading("Waystones", TITLE);
            w.text("Place one and it joins the network. Every waystone, from every player, links to every other."
                + " Right-click one to travel, free.", INK);
            w.blank();
            w.text("- rename it in an anvil before placing (or use a name tag on it)", INK);
            w.text("- sneak + right-click it with an item to set its icon", INK);
            w.text("- stand still " + Text.num(f.waystoneWarmupSeconds) + "s; damage cancels", INK);
            if (f.waystoneBlockInCombat) {
                w.text("- no escaping mid boss fight", INK);
            }
            w.blank();
            w.text("Only the owner can take it down. Explosions and pistons can't.", SOFT);
            w.newPage();
        }

        // ---------------- Nemesis ----------------
        if (f.nemesisEnabled) {
            sections.put("Nemesis", w.currentPage());
            w.heading("Nemesis", CURSE);
            w.text("A boss that kills you becomes your Nemesis. Flee from a " + s.rank(f.nemesisEscapeMinRank).name()
                + "+ boss after a real fight and it may too.", INK);
            w.blank();
            w.text("It gets a name of its own, like \"Returned Scrawl the Bulwark\", shown with a single white star.", INK);
            w.blank();
            w.text("Then it slinks off" + (f.nemesisProwlMinutes > 0 ? " and prowls for " + Text.num(f.nemesisProwlMinutes)
                + " minutes, hunting other mobs" : "") + ", and returns for you after " + Text.num(f.nemesisReturnDays)
                + " Minecraft days. It ignores the boss cap and never despawns.", INK);
            w.newPage();
            w.heading("It Remembers", CURSE);
            w.text("- every death to it: +" + f.nemesisLevelsOnKill + " levels", INK);
            w.text("- every escape: +" + f.nemesisLevelsOnEscape + " level", INK);
            if (f.nemesisKillLevelsPerOuting > 0) {
                w.text("- every mob it kills: +1 level (up to " + f.nemesisKillLevelsPerOuting + " each time it's out)", INK);
            }
            w.text("- a rank every " + f.nemesisLevelsPerRank + " levels, a new trait every "
                + f.nemesisLevelsPerTrait + " (up to level " + f.nemesisMaxLevel + ")", INK);
            w.text("- it adapts: archers face wards, brawlers face thorns, runners face speed", INK);
            w.blank();
            w.text("It earns titles: Returned, Twice-Fled, Relentless, Unbroken... Its gear never looks worse"
                + " than the boss it came from, and it keeps its look (baby or not) every time.", SOFT);
            w.newPage();
            w.heading("Revenge", CURSE);
            w.text("Slay it for guaranteed Masterwork gear, a rune, better relic odds and a Nemesis Statue"
                + " of it to place in your base.", INK);
            w.blank();
            w.text("Killing a boss that killed you is Revenge: x" + Text.num(f.nemesisRevengeXpMultiplier)
                + " XP and an extra reward roll.", INK);
            w.blank();
            w.text("Statue: right-click a block to place, sneak + right-click to pick up.", SOFT);
            w.newPage();
        }

        // ---------------- Commands ----------------
        sections.put("Commands", w.currentPage());
        w.heading("Commands", TITLE);
        command(w, "/bosses guide", "Get a fresh copy of this book.");
        command(w, "/bosses apply", "Use the rune or relic in your off hand on your main-hand item.");
        command(w, "/bosses inspect", "See every layer of your held item.");
        command(w, "/bosses stats", "Your boss-hunting record.");
        command(w, "/bosses nemesis", "Your Nemeses and when they return.");
        if (reader.hasPermission("additionalbosses.admin")) {
            w.newPage();
            w.heading("Admin", TITLE);
            command(w, "/bosses spawn <mob> [rank] [traits]", "Spawn a boss.");
            command(w, "/bosses give <player> <gear|rune|relic|catalyst|compass|totem|waystone|guide>", "Create items.");
            command(w, "/bosses nemesis list|summon|clear <player>", "Manage Nemeses.");
            command(w, "/bosses escalate <player>", "Trigger an Escalation.");
            command(w, "/bosses promote [ranks]", "Promote the boss you look at.");
            command(w, "/bosses give <player> waystone [amount]", "Give waystones.");
            command(w, "/bosses list", "Active bosses.");
            command(w, "/bosses killall", "Remove all bosses.");
            command(w, "/bosses reload", "Reload config.yml.");
        }
        w.newPage();

        // ---------------- Personal record ----------------
        sections.put("Your Record", w.currentPage());
        w.heading("Your Record", TITLE);
        w.text("Bosses slain by " + reader.getName() + ":", INK);
        w.blank();
        for (BossRank rank : BossRank.values()) {
            w.add(Component.text(s.rank(rank).title() + ": ", rank.bookColor())
                .append(Component.text(String.valueOf(PlayerData.kills(reader, rank)), INK)), false);
        }
        w.blank();
        w.text("Total: " + PlayerData.totalKills(reader), INK);
        w.text("Relics bound: " + PlayerData.relicsBound(reader), INK);
        if (f.escalationEnabled) {
            w.text("Escalation: " + plugin.escalation().progress(reader) + "/" + f.escalationKills + " today", INK);
        }
        if (f.nemesisEnabled) {
            List<NemesisRecord> mine = plugin.nemesis().forOwner(reader.getUniqueId());
            w.blank();
            w.add(Component.text("Nemeses: " + mine.size(), CURSE).decorate(TextDecoration.BOLD), true);
            for (NemesisRecord r : mine) {
                w.reserve(2);
                w.text(plugin.nemesis().plainName(r) + " (Lv " + r.level + ")", INK);
                w.text(plugin.nemesis().describeReturn(r), SOFT);
            }
        }

        List<Component> body = w.finish();

        // ---------------- Front matter: title page + clickable contents (as many pages as it needs) ----------
        List<Map.Entry<String, Integer>> all = new ArrayList<>(sections.entrySet());
        if (1 + contentsPages(all, 0, null).size() + body.size() <= MAX_PAGES) {
            return List.of(book("Boss Hunter's Compendium", null, all, body));
        }
        // Too long for one book: split into two volumes at the section boundary nearest the middle.
        int split = 1;
        for (int i = 1; i < all.size(); i++) {
            if (Math.abs(all.get(i).getValue() - body.size() / 2) < Math.abs(all.get(split).getValue() - body.size() / 2)) {
                split = i;
            }
        }
        int cut = all.get(split).getValue();
        List<Map.Entry<String, Integer>> second = new ArrayList<>();
        for (Map.Entry<String, Integer> e : all.subList(split, all.size())) {
            second.add(Map.entry(e.getKey(), e.getValue() - cut));
        }
        return List.of(
            book("Boss Hunter's Compendium I", "Volume I", all.subList(0, split), body.subList(0, cut)),
            book("Boss Hunter's Compendium II", "Volume II", second,
                body.subList(cut, Math.min(body.size(), cut + MAX_PAGES - 2 - contentsPages(second, 0, null).size()))));
    }

    private static final int MAX_PAGES = 100;

    private ItemStack book(String title, @org.jetbrains.annotations.Nullable String volume,
                           List<Map.Entry<String, Integer>> sections, List<Component> body) {
        int contentsCount = contentsPages(sections, 0, volume).size();
        int offset = 1 + contentsCount; // title page + contents pages come before the body
        List<Component> pages = new ArrayList<>();
        pages.add(titlePage(volume));
        pages.addAll(contentsPages(sections, offset, volume));
        pages.addAll(body);
        ItemStack book = ItemStack.of(Material.WRITTEN_BOOK);
        book.setData(DataComponentTypes.WRITTEN_BOOK_CONTENT,
            WrittenBookContent.writtenBookContent(title, "Additional Bosses")
                .addPages(pages.subList(0, Math.min(MAX_PAGES, pages.size())))
                .resolved(true)
                .build());
        book.editPersistentDataContainer(pdc ->
            pdc.set(Keys.ITEM_KIND, PersistentDataType.STRING, ItemService.Kind.GUIDE.name()));
        return book;
    }

    /** The contents list, spread over as many pages as needed (each line is a clickable link). */
    private static List<Component> contentsPages(List<Map.Entry<String, Integer>> sections, int offset,
                                                 @org.jetbrains.annotations.Nullable String volume) {
        List<Component> pages = new ArrayList<>();
        TextComponent.Builder b = Component.text();
        b.append(Component.text(volume == null ? "Contents" : "Contents - " + volume, TITLE).decorate(TextDecoration.BOLD))
            .append(Component.newline()).append(Component.newline());
        int used = 2;
        for (Map.Entry<String, Integer> e : sections) {
            String label = "▸ " + e.getKey();
            int lines = BookWriter.lineCount(label, false);
            if (used + lines > 14) {
                pages.add(b.build());
                b = Component.text();
                used = 0;
            }
            int page = e.getValue() + offset + 1;
            b.append(Component.text(label, NamedTextColor.DARK_BLUE)
                .clickEvent(ClickEvent.changePage(page))
                .hoverEvent(HoverEvent.showText(Component.text("Go to page " + page))));
            b.append(Component.newline());
            used += lines;
        }
        pages.add(b.build());
        return pages;
    }

    private static Component titlePage(@org.jetbrains.annotations.Nullable String volume) {
        return Component.text()
            .append(Component.text(volume == null ? "" : "            " + volume, SOFT))
            .append(Component.newline())
            .append(Component.text("   ADDITIONAL", TITLE).decorate(TextDecoration.BOLD)).append(Component.newline())
            .append(Component.text("      BOSSES", TITLE).decorate(TextDecoration.BOLD)).append(Component.newline())
            .append(Component.newline())
            .append(Component.text("Boss Hunter's", SOFT).decorate(TextDecoration.ITALIC)).append(Component.newline())
            .append(Component.text("Compendium", SOFT).decorate(TextDecoration.ITALIC)).append(Component.newline())
            .append(Component.newline())
            .append(Component.text("★", BossRank.GRAY.bookColor()))
            .append(Component.text("★", BossRank.GREEN.bookColor()))
            .append(Component.text("★", BossRank.RED.bookColor()))
            .append(Component.text("★", BossRank.PURPLE.bookColor()))
            .append(Component.text("★", BossRank.GOLD.bookColor()))
            .append(Component.newline()).append(Component.newline())
            .append(Component.text("Ordinary mobs don't always stay ordinary.", INK))
            .append(Component.newline()).append(Component.newline())
            .append(Component.text("Turn the page →", SOFT))
            .build();
    }

    private static Component contentsPage(Map<String, Integer> sections, int offset) {
        TextComponent.Builder b = Component.text();
        b.append(Component.text("Contents", TITLE).decorate(TextDecoration.BOLD)).append(Component.newline());
        b.append(Component.newline());
        for (Map.Entry<String, Integer> e : sections.entrySet()) {
            int page = e.getValue() + offset + 1;
            b.append(Component.text("▸ " + e.getKey(), NamedTextColor.DARK_BLUE)
                .clickEvent(ClickEvent.changePage(page))
                .hoverEvent(HoverEvent.showText(Component.text("Go to page " + page))));
            b.append(Component.newline());
        }
        return b.build();
    }

    private static void command(BookWriter w, String command, String description) {
        w.reserve(3);
        w.add(Component.text(command, NamedTextColor.DARK_BLUE), false);
        w.text(description, SOFT);
        w.blank();
    }

    private static String phases(FeatureSettings f) {
        StringJoiner out = new StringJoiner(" and ");
        for (double d : f.ascendantPhaseThresholds) {
            out.add(Text.num(d));
        }
        return out.toString();
    }

    private static <T extends Enum<T>> String weights(Map<T, Integer> weights) {
        int total = 0;
        for (int v : weights.values()) {
            total += Math.max(0, v);
        }
        StringJoiner out = new StringJoiner(", ");
        for (Map.Entry<T, Integer> e : weights.entrySet()) {
            if (e.getValue() > 0 && total > 0) {
                out.add(Text.pretty(e.getKey().name()) + " " + Math.round(e.getValue() * 100.0 / total) + "%");
            }
        }
        return out.toString();
    }

    private static String rangeLine(EmpowermentStat stat) {
        return BossRank.GRAY.name().charAt(0) + BossRank.GRAY.name().substring(1).toLowerCase() + " "
            + stat.rangeText(BossRank.GRAY) + "  Gold " + stat.rangeText(BossRank.GOLD);
    }

    private static String examples(MobCategory category) {
        StringJoiner joiner = new StringJoiner(", ");
        int n = 0;
        for (EntityType type : category.mobs().stream().sorted().toList()) {
            if (n++ >= 3) {
                break;
            }
            joiner.add(Text.pretty(type.name()));
        }
        return n > 3 ? joiner + "..." : joiner.toString();
    }
}
