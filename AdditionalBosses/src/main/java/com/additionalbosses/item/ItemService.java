package com.additionalbosses.item;

import com.additionalbosses.AdditionalBosses;
import com.additionalbosses.boss.BossRank;
import com.additionalbosses.config.EmpowermentStat;
import com.additionalbosses.config.Messages;
import com.additionalbosses.config.PluginSettings;
import com.additionalbosses.relic.RelicEffect;
import com.additionalbosses.util.Fx;
import com.additionalbosses.util.Keys;
import com.additionalbosses.util.PlayerData;
import com.additionalbosses.util.Rng;
import com.additionalbosses.util.Text;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import io.papermc.paper.datacomponent.item.attribute.AttributeModifierDisplay;
import io.papermc.paper.persistence.PersistentDataContainerView;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Creates the plugin's special items (Boss Gear identity, Empowerment Runes, Relics, Relic Catalysts), applies
 * them to equipment, and renders the layered item lore (Boss Gear + Empowered stats + Relics + Curses).
 *
 * <p>All layer data lives in the item's PersistentDataContainer; the lore is always rebuilt from that data.</p>
 */
public final class ItemService {

    public enum Kind { GEAR, RUNE, RELIC, CATALYST, GUIDE }

    private record Pending(String token, int expiresAt) {
    }

    private static final TextColor EMPOWER = TextColor.color(0x55D7FF);
    private static final TextColor RELIC = TextColor.color(0xE08CFF);
    private static final TextColor CURSE = TextColor.color(0xFF4040);

    private final AdditionalBosses plugin;
    private final Map<UUID, Pending> pending = new HashMap<>();

    public ItemService(AdditionalBosses plugin) {
        this.plugin = plugin;
    }

    private PluginSettings settings() {
        return plugin.settings();
    }

    // =====================================================================
    // Identification + data access
    // =====================================================================

    public @Nullable Kind kind(@Nullable ItemStack item) {
        if (item == null || item.isEmpty()) {
            return null;
        }
        String raw = item.getPersistentDataContainer().get(Keys.ITEM_KIND, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return Kind.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public boolean isConsumable(@Nullable ItemStack item) {
        Kind k = kind(item);
        return k == Kind.RUNE || k == Kind.RELIC || k == Kind.CATALYST;
    }

    public static List<String> list(ItemStack item, NamespacedKey key) {
        List<String> l = item.getPersistentDataContainer().get(key, PersistentDataType.LIST.strings());
        return l == null ? new ArrayList<>() : new ArrayList<>(l);
    }

    public int relicSlots(ItemStack item) {
        return item.getPersistentDataContainer().getOrDefault(Keys.RELIC_SLOTS, PersistentDataType.INTEGER,
            settings().relicBaseSlots);
    }

    /** Empowerments summed per stat, in the order they were first applied. */
    public Map<String, Double> empowerments(ItemStack item) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (String entry : list(item, Keys.EMPOWERMENTS)) {
            int colon = entry.lastIndexOf(':');
            if (colon <= 0) {
                continue;
            }
            try {
                out.merge(entry.substring(0, colon), Double.parseDouble(entry.substring(colon + 1)), Double::sum);
            } catch (NumberFormatException ignored) {
                // corrupt entry, skip
            }
        }
        return out;
    }

    // =====================================================================
    // Creating items
    // =====================================================================

    /** Gives an item its Boss Gear identity: rank-coloured name with stars, label and source. */
    public void markGear(ItemStack item, BossRank rank, String sourceName) {
        String rankName = settings().rank(rank).name();
        Component name = Component.text(rank.starText() + " " + rankName + " " + Text.pretty(item.getType().name()),
            rank.color());
        if (rank == BossRank.GOLD) {
            name = name.decorate(TextDecoration.BOLD);
        }
        item.setData(DataComponentTypes.ITEM_NAME, name);
        item.editPersistentDataContainer(pdc -> {
            pdc.set(Keys.ITEM_KIND, PersistentDataType.STRING, Kind.GEAR.name());
            pdc.set(Keys.ITEM_RANK, PersistentDataType.STRING, rank.name());
            pdc.set(Keys.GEAR_SOURCE, PersistentDataType.STRING, sourceName);
        });
        refreshLore(item);
    }

    public @Nullable ItemStack createRandomRune(BossRank rank) {
        EmpowermentStat stat = Rng.weighted(settings().empowermentStats, EmpowermentStat::weight);
        return stat == null ? null : createRune(rank, stat, stat.roll(rank));
    }

    public ItemStack createRune(BossRank rank, EmpowermentStat stat, double amount) {
        ItemStack item = ItemStack.of(settings().runeMaterial);
        Component name = Component.text(rank.starText() + " Empowerment Rune", rank.color());
        item.setData(DataComponentTypes.ITEM_NAME, name);
        item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        List<Component> lore = new ArrayList<>();
        lore.add(Text.line(stat.format(amount), EMPOWER));
        lore.add(Text.noItalic(Component.text("Fits: ", NamedTextColor.GRAY)
            .append(Component.text(stat.fitsText(), NamedTextColor.WHITE))));
        lore.add(Component.empty());
        lore.add(Text.line("Adds a permanent stat bonus.", NamedTextColor.DARK_GRAY));
        lore.add(Text.line("Pick it up and click it onto an item,", NamedTextColor.DARK_GRAY));
        lore.add(Text.line("or use /bosses apply.", NamedTextColor.DARK_GRAY));
        item.lore(lore);
        item.editPersistentDataContainer(pdc -> {
            pdc.set(Keys.ITEM_KIND, PersistentDataType.STRING, Kind.RUNE.name());
            pdc.set(Keys.ITEM_RANK, PersistentDataType.STRING, rank.name());
            pdc.set(Keys.RUNE_STAT, PersistentDataType.STRING, stat.id());
            pdc.set(Keys.RUNE_AMOUNT, PersistentDataType.DOUBLE, amount);
        });
        return item;
    }

    /** Rolls a relic: always one good effect, plus the corruption chance for one curse on top. */
    public @Nullable ItemStack createRandomRelic() {
        RelicEffect effect = plugin.relics().rollEffect();
        if (effect == null) {
            return null;
        }
        RelicEffect curse = Rng.chance(settings().corruptionChance) ? plugin.relics().rollCurse() : null;
        return createRelic(effect, curse);
    }

    public ItemStack createRelic(RelicEffect effect, @Nullable RelicEffect curse) {
        boolean showCurse = curse != null && settings().revealCorruption;
        ItemStack item = ItemStack.of(settings().relicMaterial);
        Component name = showCurse
            ? Component.text("Corrupted Relic: " + effect.displayName(), CURSE)
            : Component.text("Relic: " + effect.displayName(), RELIC);
        item.setData(DataComponentTypes.ITEM_NAME, name);
        item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        item.setData(DataComponentTypes.MAX_STACK_SIZE, 1);
        List<Component> lore = new ArrayList<>();
        lore.add(Text.line("✦ " + effect.displayName(), RELIC));
        lore.add(Text.line("  " + effect.description(), NamedTextColor.GRAY));
        if (showCurse) {
            lore.add(Text.line("☠ Curse: " + curse.displayName(), CURSE));
            lore.add(Text.line("  " + curse.description(), NamedTextColor.DARK_RED));
        } else if (!settings().revealCorruption) {
            lore.add(Text.line("Its aura can't be read until it is bound...", NamedTextColor.DARK_GRAY));
        }
        lore.add(Component.empty());
        lore.add(Text.line("Permanent once bound to equipment.", NamedTextColor.YELLOW));
        lore.add(Text.line("Pick it up and click it onto any equipment,", NamedTextColor.DARK_GRAY));
        lore.add(Text.line("or use /bosses apply.", NamedTextColor.DARK_GRAY));
        item.lore(lore);
        item.editPersistentDataContainer(pdc -> {
            pdc.set(Keys.ITEM_KIND, PersistentDataType.STRING, Kind.RELIC.name());
            pdc.set(Keys.RELIC_ID, PersistentDataType.STRING, effect.id());
            if (curse != null) {
                pdc.set(Keys.RELIC_CURSE, PersistentDataType.STRING, curse.id());
            }
        });
        return item;
    }

    public ItemStack createCatalyst() {
        ItemStack item = ItemStack.of(settings().catalystMaterial);
        item.setData(DataComponentTypes.ITEM_NAME, Component.text("Relic Catalyst", NamedTextColor.GOLD)
            .decorate(TextDecoration.BOLD));
        item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        List<Component> lore = new ArrayList<>();
        lore.add(Text.line("Gives a piece of equipment +1 Relic slot", NamedTextColor.GRAY));
        lore.add(Text.line("(up to " + settings().catalystMaxSlots + " slots per item).", NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Text.line("Permanent. Extremely rare.", NamedTextColor.YELLOW));
        lore.add(Text.line("Pick it up and click it onto equipment,", NamedTextColor.DARK_GRAY));
        lore.add(Text.line("or use /bosses apply.", NamedTextColor.DARK_GRAY));
        item.lore(lore);
        item.editPersistentDataContainer(pdc -> pdc.set(Keys.ITEM_KIND, PersistentDataType.STRING, Kind.CATALYST.name()));
        return item;
    }

    // =====================================================================
    // Applying runes / relics / catalysts
    // =====================================================================

    /** Returns an error message, or null if the consumable can be applied to the target. */
    public @Nullable Component validate(ItemStack consumable, ItemStack target) {
        Messages m = settings().messages;
        EquipmentType type = EquipmentType.of(target.getType());
        if (target.isEmpty() || !type.isEquipment() || kind(target) == Kind.RUNE || kind(target) == Kind.RELIC
            || kind(target) == Kind.CATALYST || target.getAmount() != 1) {
            return m.prefixed("apply-not-equipment");
        }
        PersistentDataContainerView c = consumable.getPersistentDataContainer();
        Kind k = kind(consumable);
        if (k == Kind.RUNE) {
            EmpowermentStat stat = settings().stat(c.getOrDefault(Keys.RUNE_STAT, PersistentDataType.STRING, ""));
            if (stat == null) {
                return m.prefixed("apply-not-equipment");
            }
            if (!stat.fits(type)) {
                return m.prefixed("apply-wrong-type", Placeholder.unparsed("fits", stat.fitsText()));
            }
            if (list(target, Keys.EMPOWERMENTS).size() >= settings().empowermentMaxPerItem) {
                return m.prefixed("apply-empowerment-full",
                    Placeholder.unparsed("count", String.valueOf(settings().empowermentMaxPerItem)));
            }
            return null;
        }
        if (k == Kind.RELIC) {
            RelicEffect effect = plugin.relics().get(c.get(Keys.RELIC_ID, PersistentDataType.STRING));
            if (effect == null) {
                return m.prefixed("apply-not-equipment");
            }
            List<String> relics = list(target, Keys.RELICS);
            if (relics.size() >= relicSlots(target)) {
                return m.prefixed("apply-no-relic-slot");
            }
            if (!settings().allowDuplicateRelics && relics.contains(effect.id())) {
                return m.prefixed("apply-duplicate-relic", Placeholder.unparsed("relic", effect.displayName()));
            }
            return null;
        }
        if (k == Kind.CATALYST) {
            if (relicSlots(target) >= settings().catalystMaxSlots) {
                return m.prefixed("apply-catalyst-max",
                    Placeholder.unparsed("count", String.valueOf(settings().catalystMaxSlots)));
            }
            return null;
        }
        return m.prefixed("apply-not-equipment");
    }

    /** Relics and catalysts are permanent, so (if enabled) they need a second click/command within 10 seconds. */
    public @Nullable Component confirmationPrompt(Player player, ItemStack consumable, ItemStack target, String where) {
        Kind k = kind(consumable);
        if (!settings().confirmBinding || (k != Kind.RELIC && k != Kind.CATALYST)) {
            return null;
        }
        String token = k + ":" + consumable.getPersistentDataContainer().getOrDefault(Keys.RELIC_ID, PersistentDataType.STRING, "")
            + ":" + target.getType() + ":" + where;
        int now = Bukkit.getCurrentTick();
        Pending p = pending.get(player.getUniqueId());
        if (p != null && p.token().equals(token) && now <= p.expiresAt()) {
            pending.remove(player.getUniqueId());
            return null;
        }
        pending.put(player.getUniqueId(), new Pending(token, now + 200));
        Messages m = settings().messages;
        if (k == Kind.CATALYST) {
            return m.prefixed("confirm-catalyst", Placeholder.component("item", target.effectiveName()));
        }
        RelicEffect effect = plugin.relics().get(consumable.getPersistentDataContainer().get(Keys.RELIC_ID, PersistentDataType.STRING));
        return m.prefixed("confirm-relic", Placeholder.unparsed("relic", effect == null ? "?" : effect.displayName()),
            Placeholder.component("item", target.effectiveName()));
    }

    /** Applies the consumable to the target (call {@link #validate} first). Returns the message to show. */
    public Component apply(Player player, ItemStack consumable, ItemStack target) {
        Messages m = settings().messages;
        PersistentDataContainerView c = consumable.getPersistentDataContainer();
        Kind k = kind(consumable);
        Component itemName = target.effectiveName();

        if (k == Kind.RUNE) {
            EmpowermentStat stat = settings().stat(c.getOrDefault(Keys.RUNE_STAT, PersistentDataType.STRING, ""));
            double amount = c.getOrDefault(Keys.RUNE_AMOUNT, PersistentDataType.DOUBLE, 0.0);
            if (stat == null) {
                return m.prefixed("apply-not-equipment");
            }
            EquipmentType type = EquipmentType.of(target.getType());
            addModifier(target, stat, amount, type);
            List<String> emps = list(target, Keys.EMPOWERMENTS);
            emps.add(stat.id() + ":" + String.format(Locale.ROOT, "%.4f", amount));
            target.editPersistentDataContainer(pdc -> pdc.set(Keys.EMPOWERMENTS, PersistentDataType.LIST.strings(), emps));
            refreshLore(target);
            Fx.play(player.getLocation(), "block.enchantment_table.use", 1.0f, 1.2f);
            return m.prefixed("rune-applied", Placeholder.component("item", itemName),
                Placeholder.unparsed("stat", stat.format(amount)));
        }

        if (k == Kind.RELIC) {
            RelicEffect effect = plugin.relics().get(c.get(Keys.RELIC_ID, PersistentDataType.STRING));
            RelicEffect curse = plugin.relics().get(c.get(Keys.RELIC_CURSE, PersistentDataType.STRING));
            if (effect == null) {
                return m.prefixed("apply-not-equipment");
            }
            List<String> relics = list(target, Keys.RELICS);
            relics.add(effect.id());
            List<String> curses = list(target, Keys.CURSES);
            if (curse != null) {
                curses.add(curse.id());
            }
            target.editPersistentDataContainer(pdc -> {
                pdc.set(Keys.RELICS, PersistentDataType.LIST.strings(), relics);
                pdc.set(Keys.CURSES, PersistentDataType.LIST.strings(), curses);
                if (!pdc.has(Keys.RELIC_SLOTS, PersistentDataType.INTEGER)) {
                    pdc.set(Keys.RELIC_SLOTS, PersistentDataType.INTEGER, settings().relicBaseSlots);
                }
            });
            target.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
            refreshLore(target);
            PlayerData.addRelicBound(player);
            Fx.play(player.getLocation(), "block.end_portal_frame.fill", 1.0f, 0.8f);
            Component msg = m.prefixed("relic-bound", Placeholder.component("item", itemName),
                Placeholder.unparsed("relic", effect.displayName()));
            if (curse != null) {
                Fx.play(player.getLocation(), "entity.wither.ambient", 0.7f, 0.6f);
                msg = msg.appendNewline().append(m.prefixed("relic-corrupted",
                    Placeholder.unparsed("curse", curse.displayName() + " - " + curse.description())));
            }
            return msg;
        }

        if (k == Kind.CATALYST) {
            int slots = relicSlots(target) + 1;
            target.editPersistentDataContainer(pdc -> pdc.set(Keys.RELIC_SLOTS, PersistentDataType.INTEGER, slots));
            target.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
            refreshLore(target);
            Fx.play(player.getLocation(), "block.beacon.activate", 1.0f, 1.3f);
            return m.prefixed("catalyst-applied", Placeholder.component("item", itemName),
                Placeholder.unparsed("count", String.valueOf(slots)));
        }
        return m.prefixed("apply-not-equipment");
    }

    /**
     * Adds the empowerment as an attribute modifier while keeping the item's normal stats (a sword keeps its base
     * damage). The bonus is hidden from the vanilla tooltip because the "Empowered" lore section shows it.
     */
    private void addModifier(ItemStack target, EmpowermentStat stat, double amount, EquipmentType type) {
        ItemAttributeModifiers current = target.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.itemAttributes();
        if (current != null) {
            for (ItemAttributeModifiers.Entry entry : current.modifiers()) {
                builder.addModifier(entry.attribute(), entry.modifier(), entry.getGroup(), entry.display());
            }
        }
        NamespacedKey key = new NamespacedKey(plugin, "empower_" + UUID.randomUUID().toString().substring(0, 8));
        AttributeModifier modifier = new AttributeModifier(key, amount, stat.operation(), type.slotGroup());
        builder.addModifier(stat.attribute(), modifier, type.slotGroup(), AttributeModifierDisplay.hidden());
        target.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, builder.build());
    }

    // =====================================================================
    // Lore
    // =====================================================================

    /**
     * Rebuilds this plugin's lore block at the top of the item's lore. Lines added by anything else (below ours)
     * are kept.
     */
    public void refreshLore(ItemStack item) {
        List<Component> ours = buildLore(item);
        item.editMeta(meta -> {
            List<Component> existing = meta.lore();
            int previous = meta.getPersistentDataContainer().getOrDefault(Keys.LORE_LINES, PersistentDataType.INTEGER, 0);
            List<Component> rest = new ArrayList<>();
            if (existing != null && existing.size() > previous) {
                rest.addAll(existing.subList(previous, existing.size()));
            }
            List<Component> all = new ArrayList<>(ours);
            all.addAll(rest);
            meta.lore(all.isEmpty() ? null : all);
            meta.getPersistentDataContainer().set(Keys.LORE_LINES, PersistentDataType.INTEGER, ours.size());
        });
    }

    public List<Component> buildLore(ItemStack item) {
        List<Component> lines = new ArrayList<>();
        PersistentDataContainerView view = item.getPersistentDataContainer();

        if (kind(item) == Kind.GEAR) {
            BossRank rank = BossRank.parse(view.get(Keys.ITEM_RANK, PersistentDataType.STRING));
            if (rank != null) {
                lines.add(Text.line("◆ Boss-Touched Gear", rank.color()));
                String source = view.get(Keys.GEAR_SOURCE, PersistentDataType.STRING);
                if (source != null) {
                    lines.add(Text.line("Dropped by: " + source, NamedTextColor.DARK_GRAY));
                }
            }
        }

        Map<String, Double> emps = empowerments(item);
        if (!emps.isEmpty()) {
            if (!lines.isEmpty()) {
                lines.add(Component.empty());
            }
            lines.add(Text.line("Empowered:", EMPOWER));
            for (Map.Entry<String, Double> e : emps.entrySet()) {
                EmpowermentStat stat = settings().stat(e.getKey());
                String text = stat == null ? "+" + Text.num(e.getValue()) + " " + Text.pretty(e.getKey())
                    : stat.format(e.getValue());
                lines.add(Text.line("  " + text, NamedTextColor.BLUE));
            }
        }

        List<String> relics = list(item, Keys.RELICS);
        List<String> curses = list(item, Keys.CURSES);
        int slots = relicSlots(item);
        if (!relics.isEmpty() || !curses.isEmpty() || slots > settings().relicBaseSlots) {
            if (!lines.isEmpty()) {
                lines.add(Component.empty());
            }
            lines.add(Text.line("Relics (" + relics.size() + "/" + slots + "):", RELIC));
            for (String id : relics) {
                RelicEffect effect = plugin.relics().get(id);
                lines.add(Text.line("  ✦ " + (effect == null ? Text.pretty(id) : effect.displayName()), RELIC));
                if (effect != null) {
                    lines.add(Text.line("     " + effect.description(), NamedTextColor.GRAY));
                }
            }
            for (int i = relics.size(); i < slots; i++) {
                lines.add(Text.line("  ◇ Empty Relic slot", NamedTextColor.DARK_GRAY));
            }
            for (String id : curses) {
                RelicEffect curse = plugin.relics().get(id);
                lines.add(Text.line("  ☠ Curse: " + (curse == null ? Text.pretty(id) : curse.displayName()), CURSE));
                if (curse != null) {
                    lines.add(Text.line("     " + curse.description(), NamedTextColor.DARK_RED));
                }
            }
        }
        return lines;
    }
}
