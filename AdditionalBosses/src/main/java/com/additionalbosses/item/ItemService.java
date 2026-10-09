package com.additionalbosses.item;

import com.additionalbosses.AdditionalBosses;
import com.additionalbosses.boss.BossRank;
import com.additionalbosses.config.EmpowermentStat;
import com.additionalbosses.config.Messages;
import com.additionalbosses.config.PluginSettings;
import com.additionalbosses.relic.RelicEffect;
import com.additionalbosses.reward.GearQuality;
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
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
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

    public enum Kind { GEAR, RUNE, RELIC, CATALYST, GUIDE, COMPASS, TOTEM, TROPHY, STATUE, WAYSTONE, SOUL }

    /** Lore lines are wrapped to about this many characters so tooltips stay on screen. */
    private static final int LORE_WIDTH = 38;

    /** What the game says when a curse takes hold. */
    private static final String[] CURSE_NOTICES = {"So be it.", "As you wish.", "It is done.", "You asked for this."};

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

    /** Sum of an effect stat (loot / fortune) on an item, e.g. 0.35 for a x1.35 multiplier. */
    public double effectBonus(@Nullable ItemStack item, String effect) {
        if (item == null || item.isEmpty() || !item.getPersistentDataContainer().has(Keys.EMPOWERMENTS)) {
            return 0;
        }
        double total = 0;
        for (Map.Entry<String, Double> e : empowerments(item).entrySet()) {
            EmpowermentStat stat = settings().stat(e.getKey());
            if (stat != null && effect.equals(stat.effect())) {
                total += e.getValue();
            }
        }
        return total;
    }

    // =====================================================================
    // Creating items
    // =====================================================================

    /** Gives an item its Boss Gear identity: rank-coloured name with stars, label and source. */
    public void markGear(ItemStack item, BossRank rank, String sourceName, GearQuality quality) {
        String rankName = settings().rank(rank).name();
        Component name = rank.styled(rank.starText() + " " + rankName + " " + Text.pretty(item.getType().name()));
        item.setData(DataComponentTypes.ITEM_NAME, name);
        item.editPersistentDataContainer(pdc -> {
            pdc.set(Keys.ITEM_KIND, PersistentDataType.STRING, Kind.GEAR.name());
            pdc.set(Keys.ITEM_RANK, PersistentDataType.STRING, rank.name());
            pdc.set(Keys.GEAR_SOURCE, PersistentDataType.STRING, sourceName);
            pdc.set(Keys.GEAR_QUALITY, PersistentDataType.STRING, quality.name());
        });
        refreshLore(item);
    }

    public @Nullable ItemStack createRandomRune(BossRank rank) {
        EmpowermentStat stat = Rng.weighted(settings().empowermentStats, EmpowermentStat::weight);
        return stat == null ? null : createRune(rank, stat, stat.roll(rank, settings().fixedRuneValues));
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
        describe(lore, "  ", effect.description(), NamedTextColor.GRAY);
        if (showCurse) {
            lore.add(Text.line("☠ Curse: " + curse.displayName(), CURSE));
            describe(lore, "  ", curse.description(), NamedTextColor.DARK_RED);
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

    /** A Boss Totem: use it to call a boss of the given rank (null = random rank). */
    public ItemStack createTotem(@Nullable BossRank rank) {
        ItemStack item = ItemStack.of(Material.PAPER);
        item.setData(DataComponentTypes.ITEM_MODEL, Key.key("totem_of_undying"));
        Component name = rank == null
            ? Component.text("Boss Totem", NamedTextColor.DARK_PURPLE)
            : Component.text(rank.starText() + " " + settings().rank(rank).name() + " Boss Totem", rank.color());
        item.setData(DataComponentTypes.ITEM_NAME, name.decorate(TextDecoration.BOLD));
        item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        item.setData(DataComponentTypes.MAX_STACK_SIZE, 16);
        List<Component> lore = new ArrayList<>();
        lore.add(Text.line(rank == null ? "Calls a boss of a random rank." : "Calls a " + settings().rank(rank).name()
            + " boss.", NamedTextColor.GRAY));
        lore.add(Text.line("Right-click to begin the ritual.", NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Text.line("The boss arrives a few blocks away", NamedTextColor.DARK_GRAY));
        lore.add(Text.line("after 3 seconds. Be ready.", NamedTextColor.DARK_GRAY));
        item.lore(lore);
        item.editPersistentDataContainer(pdc -> {
            pdc.set(Keys.ITEM_KIND, PersistentDataType.STRING, Kind.TOTEM.name());
            pdc.set(Keys.TOTEM_RANK, PersistentDataType.STRING, rank == null ? "" : rank.name());
        });
        return item;
    }

    public static final String[] COMPASS_TIER_NAMES = {"Worn", "Keen", "Masterwork", "Mythic", "Legendary"};

    /** The Hunter's Compass. Higher tiers track further and read a boss's rank from further away. */
    public ItemStack createCompass(int tier) {
        var tiers = settings().features.compassTiers;
        int t = Math.max(1, Math.min(tiers.size(), tier));
        var info = settings().features.compassTier(t);
        ItemStack item = ItemStack.of(Material.COMPASS);
        String label = COMPASS_TIER_NAMES[Math.min(COMPASS_TIER_NAMES.length - 1, t - 1)];
        item.setData(DataComponentTypes.ITEM_NAME, Component.text(label + " Hunter's Compass", NamedTextColor.GOLD));
        item.setData(DataComponentTypes.MAX_STACK_SIZE, 1);
        if (t > 1) {
            item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        List<Component> lore = new ArrayList<>();
        lore.add(Text.line("Tier " + Text.roman(t) + " / " + Text.roman(tiers.size()), NamedTextColor.YELLOW));
        lore.add(Text.line("Hold it to track the nearest boss", NamedTextColor.GRAY));
        lore.add(Text.line("within " + Math.round(info.range()) + " blocks.", NamedTextColor.GRAY));
        lore.add(Text.line("Reveals its rank within " + Math.round(info.revealDistance()) + " blocks.", NamedTextColor.GRAY));
        lore.add(Component.empty());
        if (t < tiers.size()) {
            lore.add(Text.line("Click an Empowerment Rune onto it", NamedTextColor.DARK_GRAY));
            lore.add(Text.line("to upgrade it" + (t + 1 == tiers.size() ? " (Red rune or better)." : "."), NamedTextColor.DARK_GRAY));
        } else {
            lore.add(Text.line("Fully upgraded.", NamedTextColor.DARK_GRAY));
        }
        lore.add(Text.line("Click Boss Gear onto it to break the", NamedTextColor.DARK_GRAY));
        lore.add(Text.line("gear down into a Boss Soul of its rank.", NamedTextColor.DARK_GRAY));
        item.lore(lore);
        item.editPersistentDataContainer(pdc -> {
            pdc.set(Keys.ITEM_KIND, PersistentDataType.STRING, Kind.COMPASS.name());
            pdc.set(Keys.COMPASS_TIER, PersistentDataType.INTEGER, t);
        });
        return item;
    }

    public int compassTier(ItemStack compass) {
        return compass.getPersistentDataContainer().getOrDefault(Keys.COMPASS_TIER, PersistentDataType.INTEGER, 1);
    }

    /** Runes upgrade a Hunter's Compass one tier; the final tier needs a Red rune or better. */
    private @Nullable Component validateCompass(ItemStack rune, ItemStack compass) {
        int tier = compassTier(compass);
        int max = settings().features.compassTiers.size();
        if (tier >= max) {
            return Text.mm("<red>This compass is already fully upgraded.</red>");
        }
        BossRank rank = BossRank.parse(rune.getPersistentDataContainer().get(Keys.ITEM_RANK, PersistentDataType.STRING));
        if (tier + 1 == max && (rank == null || !rank.atLeast(BossRank.RED))) {
            return Text.mm("<red>The last upgrade needs a Red, Purple or Gold rune.</red>");
        }
        return compass.getAmount() == 1 ? null : settings().messages.get("apply-not-equipment");
    }

    public boolean isCompassUpgrade(@Nullable ItemStack consumable, @Nullable ItemStack target) {
        return kind(consumable) == Kind.RUNE && kind(target) == Kind.COMPASS;
    }

    // =====================================================================
    // Applying runes / relics / catalysts
    // =====================================================================

    /** Returns an error message, or null if the consumable can be applied to the target. */
    public @Nullable Component validate(ItemStack consumable, ItemStack target) {
        Messages m = settings().messages;
        if (isCompassUpgrade(consumable, target)) {
            return validateCompass(consumable, target);
        }
        EquipmentType type = EquipmentType.of(target.getType());
        Kind targetKind = kind(target);
        if (targetKind == Kind.COMPASS || targetKind == Kind.TOTEM || targetKind == Kind.TROPHY || targetKind == Kind.SOUL
            || targetKind == Kind.STATUE || targetKind == Kind.GUIDE || targetKind == Kind.WAYSTONE) {
            return m.get("apply-not-equipment");
        }
        if (target.isEmpty() || !type.isEquipment() || kind(target) == Kind.RUNE || kind(target) == Kind.RELIC
            || kind(target) == Kind.CATALYST || target.getAmount() != 1) {
            return m.get("apply-not-equipment");
        }
        PersistentDataContainerView c = consumable.getPersistentDataContainer();
        Kind k = kind(consumable);
        if (k == Kind.RUNE) {
            EmpowermentStat stat = settings().stat(c.getOrDefault(Keys.RUNE_STAT, PersistentDataType.STRING, ""));
            if (stat == null) {
                return m.get("apply-not-equipment");
            }
            if (!stat.fits(type)) {
                return m.get("apply-wrong-type", Placeholder.unparsed("fits", stat.fitsText()));
            }
            if (list(target, Keys.EMPOWERMENTS).size() >= settings().empowermentMaxPerItem) {
                return m.get("apply-empowerment-full",
                    Placeholder.unparsed("count", String.valueOf(settings().empowermentMaxPerItem)));
            }
            return null;
        }
        if (k == Kind.RELIC) {
            RelicEffect effect = plugin.relics().get(c.get(Keys.RELIC_ID, PersistentDataType.STRING));
            if (effect == null) {
                return m.get("apply-not-equipment");
            }
            List<String> relics = list(target, Keys.RELICS);
            if (relics.size() >= relicSlots(target)) {
                return m.get("apply-no-relic-slot");
            }
            if (!settings().allowDuplicateRelics && relics.contains(effect.id())) {
                return m.get("apply-duplicate-relic", Placeholder.unparsed("relic", effect.displayName()));
            }
            return null;
        }
        if (k == Kind.CATALYST) {
            if (relicSlots(target) >= settings().catalystMaxSlots) {
                return m.get("apply-catalyst-max",
                    Placeholder.unparsed("count", String.valueOf(settings().catalystMaxSlots)));
            }
            return null;
        }
        return m.get("apply-not-equipment");
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
            return m.get("confirm-catalyst", Placeholder.component("item", target.effectiveName()));
        }
        RelicEffect effect = plugin.relics().get(consumable.getPersistentDataContainer().get(Keys.RELIC_ID, PersistentDataType.STRING));
        Component prompt = m.get("confirm-relic", Placeholder.unparsed("relic", effect == null ? "?" : effect.displayName()),
            Placeholder.component("item", target.effectiveName()));
        if (hasCatalyst(target) && catalystCorruptionChance() > settings().corruptionChance) {
            prompt = prompt.append(Component.space()).append(m.get("confirm-relic-catalyst",
                Placeholder.unparsed("count", Text.num(catalystCorruptionChance()))));
        }
        return prompt;
    }

    // =====================================================================
    // Hunter's Compass: Boss Gear -> Boss Soul
    // =====================================================================

    /** True for a piece of Boss Gear dropped on a Hunter's Compass. */
    public boolean isSalvage(@Nullable ItemStack gear, @Nullable ItemStack compass) {
        return kind(gear) == Kind.GEAR && kind(compass) == Kind.COMPASS && gear != null && gear.getAmount() == 1;
    }

    /** The rank a piece of Boss Gear came from (Gray if unknown). */
    public BossRank gearRank(ItemStack gear) {
        BossRank rank = BossRank.parse(gear.getPersistentDataContainer().get(Keys.ITEM_RANK, PersistentDataType.STRING));
        return rank == null ? BossRank.GRAY : rank;
    }

    /** Breaking gear down destroys it, so it asks for a second click within 10 seconds. Null once confirmed. */
    public @Nullable Component salvagePrompt(Player player, ItemStack gear, String where) {
        String token = "SALVAGE:" + gear.getType() + ":" + gearRank(gear) + ":" + where;
        int now = Bukkit.getCurrentTick();
        Pending p = pending.get(player.getUniqueId());
        if (p != null && p.token().equals(token) && now <= p.expiresAt()) {
            pending.remove(player.getUniqueId());
            return null;
        }
        pending.put(player.getUniqueId(), new Pending(token, now + 200));
        return settings().messages.get("confirm-salvage", Placeholder.component("item", gear.effectiveName()),
            Placeholder.component("soul", createSoul(gearRank(gear), 1).effectiveName()));
    }

    /** Applies the consumable to the target (call {@link #validate} first). Returns the message to show. */
    public Component apply(Player player, ItemStack consumable, ItemStack target) {
        Messages m = settings().messages;
        PersistentDataContainerView c = consumable.getPersistentDataContainer();
        Kind k = kind(consumable);
        Component itemName = target.effectiveName();

        if (isCompassUpgrade(consumable, target)) {
            ItemStack upgraded = createCompass(compassTier(target) + 1);
            target.copyDataFrom(upgraded, component -> true);
            target.editPersistentDataContainer(pdc ->
                pdc.set(Keys.COMPASS_TIER, PersistentDataType.INTEGER, compassTier(upgraded)));
            Fx.play(player.getLocation(), "block.lodestone.place", 1.0f, 1.2f);
            Fx.play(player.getLocation(), "block.enchantment_table.use", 1.0f, 1.4f);
            return m.get("compass-upgraded", Placeholder.unparsed("count", Text.roman(compassTier(upgraded))));
        }

        if (k == Kind.RUNE) {
            EmpowermentStat stat = settings().stat(c.getOrDefault(Keys.RUNE_STAT, PersistentDataType.STRING, ""));
            double amount = c.getOrDefault(Keys.RUNE_AMOUNT, PersistentDataType.DOUBLE, 0.0);
            if (stat == null) {
                return m.get("apply-not-equipment");
            }
            EquipmentType type = EquipmentType.of(target.getType());
            if (stat.effect() == null) {
                addModifier(target, stat, amount, type);
            }
            List<String> emps = list(target, Keys.EMPOWERMENTS);
            emps.add(stat.id() + ":" + String.format(Locale.ROOT, "%.4f", amount));
            target.editPersistentDataContainer(pdc -> pdc.set(Keys.EMPOWERMENTS, PersistentDataType.LIST.strings(), emps));
            refreshLore(target);
            Fx.play(player.getLocation(), "block.enchantment_table.use", 1.0f, 1.2f);
            return m.get("rune-applied", Placeholder.component("item", itemName),
                Placeholder.unparsed("stat", stat.format(amount)));
        }

        if (k == Kind.RELIC) {
            RelicEffect effect = plugin.relics().get(c.get(Keys.RELIC_ID, PersistentDataType.STRING));
            RelicEffect curse = plugin.relics().get(c.get(Keys.RELIC_CURSE, PersistentDataType.STRING));
            if (effect == null) {
                return m.get("apply-not-equipment");
            }
            if (curse == null && hasCatalyst(target) && Rng.chance(extraCatalystCurseChance())) {
                // A Catalyst draws corruption: relics bound to its item are far more likely to carry a curse.
                curse = plugin.relics().rollCurse();
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
            Component msg = m.get("relic-bound", Placeholder.component("item", itemName),
                Placeholder.unparsed("relic", effect.displayName()));
            if (curse != null) {
                boolean bound = bindCursedArmor(target);
                Fx.play(player.getLocation(), "entity.wither.ambient", 0.7f, 0.6f);
                msg = m.get("relic-corrupted", Placeholder.unparsed("curse", curse.displayName()));
                curseNotice(player, curse, bound);
            }
            return msg;
        }

        if (k == Kind.CATALYST) {
            int slots = relicSlots(target) + 1;
            target.editPersistentDataContainer(pdc -> pdc.set(Keys.RELIC_SLOTS, PersistentDataType.INTEGER, slots));
            target.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
            refreshLore(target);
            Fx.play(player.getLocation(), "block.beacon.activate", 1.0f, 1.3f);
            return m.get("catalyst-applied", Placeholder.component("item", itemName),
                Placeholder.unparsed("count", String.valueOf(slots)));
        }
        return m.get("apply-not-equipment");
    }

    /** True once a Relic Catalyst has given this item an extra slot. */
    public boolean hasCatalyst(ItemStack item) {
        return relicSlots(item) > settings().relicBaseSlots;
    }

    private double catalystCorruptionChance() {
        return settings().catalystCorruptionChance;
    }

    /** The extra roll that lifts the total curse chance from the normal one to the Catalyst one. */
    private double extraCatalystCurseChance() {
        double base = Math.max(0, Math.min(100, settings().corruptionChance));
        double total = Math.max(0, Math.min(100, catalystCorruptionChance()));
        return total <= base || base >= 100 ? 0 : (total - base) / (100 - base) * 100.0;
    }

    /**
     * Cursed armor can't be taken off: it gets Curse of Binding, so only death removes it. Returns true if the
     * item is armor that is (now) bound.
     */
    public boolean bindCursedArmor(@Nullable ItemStack item) {
        if (item == null || item.isEmpty() || !EquipmentType.of(item.getType()).isArmor() || list(item, Keys.CURSES).isEmpty()) {
            return false;
        }
        if (item.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.BINDING_CURSE) <= 0) {
            item.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.BINDING_CURSE, 1);
        }
        return true;
    }

    private static void curseNotice(Player player, RelicEffect curse, boolean bound) {
        String line = CURSE_NOTICES[Rng.between(0, CURSE_NOTICES.length - 1)];
        Component sub = Component.text(curse.displayName(), CURSE).append(Component.text(bound
            ? " binds to you. Only death will part you." : " takes hold.", NamedTextColor.GRAY));
        player.showTitle(net.kyori.adventure.title.Title.title(Component.text(line, NamedTextColor.DARK_RED), sub,
            net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(300), java.time.Duration.ofMillis(2500),
                java.time.Duration.ofMillis(900))));
        Fx.playTo(player, Fx.sound("entity.elder_guardian.curse", 0.6f, 0.7f));
    }

    /** A stackable Boss Soul of the given rank: offer it to a boss to make it rise. */
    public ItemStack createSoul(BossRank rank, int amount) {
        ItemStack item = ItemStack.of(Material.PAPER, Math.max(1, Math.min(64, amount)));
        item.setData(DataComponentTypes.ITEM_MODEL, Key.key("echo_shard"));
        item.setData(DataComponentTypes.ITEM_NAME, rank.styled(rank.starText() + " " + settings().rank(rank).name() + " Boss Soul"));
        if (rank.atLeast(BossRank.PURPLE)) {
            item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        List<Component> lore = new ArrayList<>();
        lore.add(Text.line("Right-click a boss with it, or throw", NamedTextColor.GRAY));
        lore.add(Text.line("(drop) it at one, and the boss rises.", NamedTextColor.GRAY));
        item.lore(lore);
        item.editPersistentDataContainer(pdc -> {
            pdc.set(Keys.ITEM_KIND, PersistentDataType.STRING, Kind.SOUL.name());
            pdc.set(Keys.ITEM_RANK, PersistentDataType.STRING, rank.name());
        });
        return item;
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
        for (org.bukkit.attribute.Attribute attribute : new org.bukkit.attribute.Attribute[]{stat.attribute(), stat.extraAttribute()}) {
            if (attribute == null) {
                continue;
            }
            NamespacedKey key = new NamespacedKey(plugin, "empower_" + UUID.randomUUID().toString().substring(0, 8));
            AttributeModifier modifier = new AttributeModifier(key, amount, stat.operation(), type.slotGroup());
            builder.addModifier(attribute, modifier, type.slotGroup(), AttributeModifierDisplay.hidden());
        }
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

    /** Adds a description as short wrapped lines, each indented the same way. */
    private static void describe(List<Component> lines, String indent, String text, TextColor color) {
        for (String part : Text.wrap(text, LORE_WIDTH)) {
            lines.add(Text.line(indent + part, color));
        }
    }

    public List<Component> buildLore(ItemStack item) {
        List<Component> lines = new ArrayList<>();
        PersistentDataContainerView view = item.getPersistentDataContainer();

        if (kind(item) == Kind.GEAR) {
            BossRank rank = BossRank.parse(view.get(Keys.ITEM_RANK, PersistentDataType.STRING));
            if (rank != null) {
                lines.add(Text.line("◆ Boss-Touched Gear", rank.color()));
                GearQuality quality = GearQuality.parse(view.getOrDefault(Keys.GEAR_QUALITY, PersistentDataType.STRING, ""));
                if (quality != null && quality != GearQuality.STANDARD) {
                    lines.add(Text.line("Quality: " + quality.displayName(), quality == GearQuality.CRUDE
                        ? NamedTextColor.GRAY : quality == GearQuality.FINE ? NamedTextColor.AQUA : NamedTextColor.GOLD));
                }
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
            }
            for (int i = relics.size(); i < slots; i++) {
                lines.add(Text.line("  ◇ Empty Relic slot", NamedTextColor.DARK_GRAY));
            }
            for (String id : curses) {
                RelicEffect curse = plugin.relics().get(id);
                lines.add(Text.line("  ☠ Curse: " + (curse == null ? Text.pretty(id) : curse.displayName()), CURSE));
            }
            if (!relics.isEmpty() || !curses.isEmpty()) {
                // What they do is in the Boss Hunter's Compendium, not on the item.
                lines.add(Text.line("  What they do: see the Compendium", NamedTextColor.DARK_GRAY));
            }
        }
        return lines;
    }
}
