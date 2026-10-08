package com.additionalbosses.guide;

import com.additionalbosses.AdditionalBosses;
import com.additionalbosses.boss.BossRank;
import com.additionalbosses.config.EmpowermentStat;
import com.additionalbosses.config.MobCategory;
import com.additionalbosses.config.PluginSettings;
import com.additionalbosses.config.RankSettings;
import com.additionalbosses.item.ItemService;
import com.additionalbosses.relic.RelicEffect;
import com.additionalbosses.trait.BossTrait;
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

    public void give(Player player) {
        ItemStack book = create(player);
        for (ItemStack left : player.getInventory().addItem(book).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
        player.sendMessage(plugin.settings().messages.prefixed("guide-received"));
    }

    public ItemStack create(Player reader) {
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
        w.text("Bosses are bigger, wear armor dyed in their rank colour and shimmer faintly. "
            + "Fight one and its health bar appears at the top of your screen.", INK);
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
        w.text("The armor a boss wears is just for show and never drops.", SOFT);
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

        // ---------------- Commands ----------------
        sections.put("Commands", w.currentPage());
        w.heading("Commands", TITLE);
        command(w, "/bosses guide", "Get a fresh copy of this book.");
        command(w, "/bosses apply", "Use the rune or relic in your off hand on your main-hand item.");
        command(w, "/bosses inspect", "See every layer of your held item.");
        command(w, "/bosses stats", "Your boss-hunting record.");
        if (reader.hasPermission("additionalbosses.admin")) {
            w.newPage();
            w.heading("Admin", TITLE);
            command(w, "/bosses spawn <mob> [rank] [traits]", "Spawn a boss.");
            command(w, "/bosses give <player> <gear|rune|relic|catalyst|guide>", "Create items.");
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

        List<Component> body = w.finish();

        // ---------------- Front matter (title + clickable contents) ----------------
        int offset = 2; // title page + contents page come first
        List<Component> pages = new ArrayList<>();
        pages.add(titlePage());
        pages.add(contentsPage(sections, offset));
        pages.addAll(body);

        ItemStack book = ItemStack.of(Material.WRITTEN_BOOK);
        book.setData(DataComponentTypes.WRITTEN_BOOK_CONTENT,
            WrittenBookContent.writtenBookContent("Boss Hunter's Compendium", "Additional Bosses")
                .addPages(pages)
                .resolved(true)
                .build());
        book.editPersistentDataContainer(pdc ->
            pdc.set(Keys.ITEM_KIND, PersistentDataType.STRING, ItemService.Kind.GUIDE.name()));
        return book;
    }

    private static Component titlePage() {
        return Component.text()
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
