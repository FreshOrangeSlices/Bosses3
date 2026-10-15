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
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * The two volumes: I. Bosses (how they appear, ranks, traits, how they fight) and II. Loot &amp; Nemesis (rewards,
     * gear, runes, relics, the hunt, promotion, the Nemesis and your record). Kept short: the details are numbers.
     */
    public List<ItemStack> create(Player reader) {
        return List.of(volumeOne(), volumeTwo(reader));
    }

    /** Volume 1 or 2 (anything else: 1). */
    public ItemStack volume(Player reader, int n) {
        return n == 2 ? volumeTwo(reader) : volumeOne();
    }

    private ItemStack volumeOne() {
        PluginSettings s = plugin.settings();
        FeatureSettings f = s.features;
        BookWriter w = new BookWriter();
        Map<String, Integer> sections = new LinkedHashMap<>();

        // ---------------- How bosses appear ----------------
        sections.put("How Bosses Appear", w.currentPage());
        w.heading("How Bosses Appear", TITLE);
        w.text("Any hostile mob has a " + Text.num(s.spawnChance) + "% chance to spawn as a boss.", INK);
        w.blank();
        w.add(Component.text("★★★★★ Legendary Undying Zombie", BossRank.GOLD.bookColor()).decorate(TextDecoration.BOLD), true);
        w.blank();
        w.text("Top ranks are very rare in the wild; the Nether breeds tougher ones.", INK);
        if (s.glowWithin > 0) {
            w.text("Bosses glow within " + Math.round(s.glowWithin) + " blocks.", SOFT);
        }
        if (s.despawnAfterKills > 0) {
            w.text("A boss that kills " + s.despawnAfterKills + " players leaves.", SOFT);
        }
        w.newPage();

        // ---------------- Ranks ----------------
        sections.put("Ranks", w.currentPage());
        w.heading("Ranks", TITLE);
        for (BossRank rank : BossRank.values()) {
            RankSettings rs = s.rank(rank);
            w.reserve(3);
            w.add(Component.text(rs.title(), rank.bookColor()).decorate(TextDecoration.BOLD), true);
            String traits = rs.traits().min() == rs.traits().max() ? String.valueOf(rs.traits().min())
                : rs.traits().min() + "-" + rs.traits().max();
            w.text("Health x" + Text.num(rs.stats().health()) + ", damage x" + Text.num(rs.stats().damage())
                + ", " + traits + " trait" + (rs.traits().max() == 1 ? "" : "s"), INK);
            if (!rank.natural()) {
                w.text("Only made with Boss Souls.", SOFT);
            }
        }
        w.newPage();

        // ---------------- Rank odds ----------------
        sections.put("Rank Odds", w.currentPage());
        w.heading("Rank Odds", TITLE);
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
            if (!category.enabled()) {
                continue;
            }
            w.reserve(2);
            w.add(Component.text(category.displayName(), INK).decorate(TextDecoration.BOLD), true);
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
            w.add(odds.append(Component.text(" %", SOFT)).build(), false);
        }
        w.newPage();

        // ---------------- Traits ----------------
        sections.put("Traits", w.currentPage());
        w.heading("Traits", TITLE);
        w.text("Traits change how a boss fights. Its first trait names it: a Vampiric zombie is a Ravenous Zombie.", INK);
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
                w.reserve(3);
                w.add(Component.text(t.displayName(), category.bookColor()).decorate(TextDecoration.BOLD), true);
                w.text(t.description(), INK);
                w.blank();
            }
        }
        w.newPage();

        // ---------------- Synergies + tells ----------------
        sections.put("Synergies & Tells", w.currentPage());
        w.heading("Synergies", TITLE);
        w.text("Some trait pairs fuse and hit harder:", INK);
        for (Synergies.Synergy syn : Synergies.ALL) {
            BossTrait a = plugin.traits().get(syn.first());
            BossTrait b = plugin.traits().get(syn.second());
            if (a == null || b == null || !plugin.traits().isEnabled(a) || !plugin.traits().isEnabled(b)) {
                continue;
            }
            w.add(Component.text(syn.title() + ": ", TITLE).decorate(TextDecoration.BOLD)
                .append(Component.text(a.displayName() + " + " + b.displayName(), SOFT)
                    .decoration(TextDecoration.BOLD, false)), false);
        }
        w.blank();
        w.heading("Tells", TITLE);
        w.text("Big attacks give a warning. Move!", INK);
        w.entry("Quaking", TITLE, "a red ring", INK);
        w.entry("Shadowed", TITLE, "gathering smoke", INK);
        w.entry("Gravitic", TITLE, "a violet ring", INK);
        w.entry("Leaping", TITLE, "a crouch", INK);
        w.entry("Charging", TITLE, "a roar", INK);
        w.newPage();

        // ---------------- Last Stand, threat ----------------
        sections.put("Last Stand & Threat", w.currentPage());
        w.heading("Last Stand", TITLE);
        w.text("Purple and Gold bosses (and every Nemesis) fight harder once, at "
            + Text.num(f.lastStandHealthPercent) + "% health.", INK);
        w.blank();
        w.heading("Threat", TITLE);
        if (f.threatEnabled) {
            w.text("Wearing or holding Boss Gear makes the bosses you fight tougher, and their loot better.", INK);
        }
        if (f.overflowArmor) {
            w.blank();
            w.text("Armor over " + Math.round(f.overflowArmorFrom) + ": each extra point is "
                + Text.num(f.overflowArmorPerPoint) + "% less damage from bosses.", INK);
        }
        w.blank();
        w.text("Bosses can't be trapped in boats, minecarts or leads.", SOFT);

        return book("Boss Compendium I", "Book I: Bosses", new ArrayList<>(sections.entrySet()), w.finish());
    }

    private ItemStack volumeTwo(Player reader) {
        PluginSettings s = plugin.settings();
        FeatureSettings f = s.features;
        BookWriter w = new BookWriter();
        Map<String, Integer> sections = new LinkedHashMap<>();

        // ---------------- Rewards ----------------
        sections.put("Rewards", w.currentPage());
        w.heading("Rewards", TITLE);
        w.text("Each drop rolls on its own:", SOFT);
        for (BossRank rank : BossRank.values()) {
            RankSettings.Rewards r = s.rank(rank).rewards();
            w.reserve(2);
            w.add(Component.text(s.rank(rank).name(), rank.bookColor()).decorate(TextDecoration.BOLD), true);
            w.text("Gear " + Text.num(r.bossGear()) + "  Rune " + Text.num(r.empowerment()) + "  Relic "
                + Text.num(r.relic()) + (r.extraRelics().isEmpty() ? "" : "+") + "%", INK);
        }
        w.newPage();

        // ---------------- Boss Gear ----------------
        sections.put("Boss Gear", w.currentPage());
        w.heading("Boss Gear", TITLE);
        w.add(Component.text("★★★★ Nightmare Diamond Sword", BossRank.PURPLE.bookColor()), false);
        w.blank();
        w.text("Higher ranks: better materials and enchantments. Weak bosses drop armor and tools, strong ones"
            + " weapons.", INK);
        if (s.overlappingEnchantsFrom != null) {
            w.blank();
            w.text(s.rank(s.overlappingEnchantsFrom).name() + "+ gear can mix enchantments like Sharpness and Smite.", SOFT);
        }
        w.newPage();

        // ---------------- Empowerment ----------------
        sections.put("Runes", w.currentPage());
        w.heading("Runes", TITLE);
        w.text("A rune adds a permanent stat to an item. Click it onto the item in your inventory. Up to "
            + s.empowermentMaxPerItem + " per item.", INK);
        w.blank();
        for (EmpowermentStat stat : s.empowermentStats) {
            w.reserve(2);
            w.add(Component.text(stat.displayName(), EMPOWER).decorate(TextDecoration.BOLD), true);
            w.text(stat.fitsText(), SOFT);
        }
        w.newPage();

        // ---------------- Relics ----------------
        sections.put("Relics", w.currentPage());
        w.heading("Relics", TITLE);
        w.text("Click a relic onto gear to bind a power to it, for good. " + Text.num(s.corruptionChance)
            + "% carry a curse too" + (s.revealCorruption ? "." : ": you find out when you bind it."), INK);
        w.blank();
        for (RelicEffect relic : plugin.relics().enabledEffects(false)) {
            w.reserve(3);
            w.add(Component.text("✦ " + relic.displayName(), RELIC).decorate(TextDecoration.BOLD), true);
            w.text(relic.description(), INK);
        }
        w.newPage();

        // ---------------- Curses ----------------
        sections.put("Curses", w.currentPage());
        w.heading("Curses", CURSE);
        w.text("Cursed armor can't be taken off until you die.", SOFT);
        w.blank();
        for (RelicEffect curse : plugin.relics().enabledEffects(true)) {
            w.reserve(3);
            w.add(Component.text("☠ " + curse.displayName(), CURSE).decorate(TextDecoration.BOLD), true);
            w.text(curse.description(), INK);
        }
        w.newPage();

        // ---------------- The Hunt ----------------
        sections.put("The Hunt", w.currentPage());
        w.heading("The Hunt", TITLE);
        if (f.compassEnabled) {
            w.entry("Hunter's Compass", TITLE, "points to the nearest boss." + (f.compassRecipe
                ? " Craft: Compass + Eye of Ender + Bone." : "") + " Click gear onto it to get a Soul.", INK);
            w.blank();
        }
        if (f.soulsEnabled) {
            w.entry("Boss Souls", TITLE, "offer one to a boss and it rises in rank.", INK);
            w.blank();
        }
        if (f.totemEnabled) {
            w.entry("Boss Totem", TITLE, "right-click it to call a boss to fight.", INK);
            w.blank();
        }
        if (f.escalationEnabled) {
            w.entry("Escalation", TITLE, "kill " + f.escalationKills + " bosses in a day and worse ones come.", INK);
            w.blank();
        }
        if (f.promotionEnabled) {
            w.entry("Ascendant", BossRank.ASCENDANT.bookColor(), "raise a boss past Legendary: the top rank.", INK);
        }
        if (f.waystonesEnabled) {
            w.blank();
            w.entry("Waystones", TITLE, "Ascendants drop them. Place one to join the free travel network.", INK);
        }
        w.newPage();

        // ---------------- Nemesis ----------------
        if (f.nemesisEnabled) {
            sections.put("Nemesis", w.currentPage());
            w.heading("Nemesis", CURSE);
            w.text("A boss that kills you (or that you flee from) may become your Nemesis. It leaves, grows"
                + " stronger, and returns after " + Text.num(f.nemesisReturnDays) + " days.", INK);
            w.blank();
            w.text("It learns how you fight. Mix it up.", INK);
            w.blank();
            w.text("When it kills you, you get its Challenge: use it to call it now, or meet its Terms of Surrender"
                + " to end the feud.", INK);
            w.blank();
            w.text("Kill it for great loot and a statue.", SOFT);
            w.newPage();
        }

        // ---------------- Commands ----------------
        sections.put("Commands", w.currentPage());
        w.heading("Commands", TITLE);
        command(w, "/bosses inspect", "Everything on your held item.");
        command(w, "/bosses stats", "Your record.");
        command(w, "/bosses nemesis", "Your Nemeses.");
        command(w, "/bosses guide", "A copy of these books.");
        w.newPage();

        // ---------------- Personal record ----------------
        sections.put("Your Record", w.currentPage());
        w.heading("Your Record", TITLE);
        for (BossRank rank : BossRank.values()) {
            w.add(Component.text(s.rank(rank).title() + ": ", rank.bookColor())
                .append(Component.text(String.valueOf(PlayerData.kills(reader, rank)), INK)), false);
        }
        w.blank();
        w.text("Total: " + PlayerData.totalKills(reader), INK);
        w.text("Relics bound: " + PlayerData.relicsBound(reader), INK);
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

        return book("Boss Compendium II", "Book II: Loot & Nemesis", new ArrayList<>(sections.entrySet()), w.finish());
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
        b.append(Component.text("Contents", TITLE).decorate(TextDecoration.BOLD))
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
            .append(Component.text(volume == null ? "" : volume, SOFT))
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

    private static void command(BookWriter w, String command, String description) {
        w.reserve(3);
        w.add(Component.text(command, NamedTextColor.DARK_BLUE), false);
        w.text(description, SOFT);
        w.blank();
    }


}
