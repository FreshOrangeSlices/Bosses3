package com.additionalbosses.command;

import com.additionalbosses.AdditionalBosses;
import com.additionalbosses.boss.Boss;
import com.additionalbosses.boss.BossRank;
import com.additionalbosses.config.EmpowermentStat;
import com.additionalbosses.config.MobCategory;
import com.additionalbosses.config.PluginSettings;
import com.additionalbosses.item.ItemService;
import com.additionalbosses.nemesis.NemesisManager;
import com.additionalbosses.nemesis.NemesisRecord;
import com.additionalbosses.relic.RelicEffect;
import com.additionalbosses.reward.GearKind;
import com.additionalbosses.trait.BossTrait;
import com.additionalbosses.util.Keys;
import com.additionalbosses.util.PlayerData;
import com.additionalbosses.util.Rng;
import com.additionalbosses.util.Text;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * /bosses (aliases /ab, /boss). Player subcommands need additionalbosses.use; admin ones need additionalbosses.admin.
 */
public final class BossesCommand implements BasicCommand {

    private static final String USE = "additionalbosses.use";
    private static final String ADMIN = "additionalbosses.admin";
    private static final List<String> PLAYER_SUBS = List.of("help", "guide", "apply", "inspect", "stats", "nemesis");
    private static final List<String> ADMIN_SUBS = List.of("spawn", "give", "list", "killall", "reload", "escalate", "promote", "curse");

    private final AdditionalBosses plugin;

    public BossesCommand(AdditionalBosses plugin) {
        this.plugin = plugin;
    }

    @Override
    public @Nullable String permission() {
        return USE;
    }

    // =====================================================================
    // Execution
    // =====================================================================

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        if (ADMIN_SUBS.contains(sub) && !sender.hasPermission(ADMIN)) {
            error(sender, "You don't have permission to do that.");
            return;
        }
        switch (sub) {
            case "guide" -> guide(sender, args);
            case "apply" -> apply(sender);
            case "inspect" -> inspect(sender);
            case "stats" -> stats(sender);
            case "nemesis" -> nemesis(sender, args);
            case "escalate" -> escalate(sender, args);
            case "promote" -> promote(sender, args);
            case "spawn" -> spawn(sender, args);
            case "give" -> give(sender, args);
            case "curse" -> curse(sender, args);
            case "list" -> list(sender);
            case "killall" -> info(sender, "Removed " + plugin.bosses().killAll() + " boss(es).");
            case "reload" -> {
                plugin.reloadSettings();
                info(sender, "Config reloaded.");
            }
            default -> help(sender);
        }
    }

    private void help(CommandSender sender) {
        sender.sendMessage(Component.text("Additional Bosses", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        line(sender, "/bosses guide", "get the Boss Hunter's Compendium");
        line(sender, "/bosses apply", "use the rune/relic in your off hand on your main-hand item");
        line(sender, "/bosses inspect", "look at a boss, or inspect your held item");
        line(sender, "/bosses stats", "your boss-hunting record");
        line(sender, "/bosses nemesis", "your Nemeses and when they return");
        if (sender.hasPermission(ADMIN)) {
            line(sender, "/bosses spawn <mob> [rank] [trait,trait]", "spawn a boss where you look");
            line(sender, "/bosses give <player> gear [rank] [kind]", "give Boss Gear");
            line(sender, "/bosses give <player> rune [rank] [stat]", "give an Empowerment Rune");
            line(sender, "/bosses give <player> relic [relic|random] [curse|none|random]", "give a Relic");
            line(sender, "/bosses give <player> catalyst|guide", "give a Catalyst or guide");
            line(sender, "/bosses give <player> compass [tier] | totem [rank]", "give a Hunter's Compass or Boss Totem");
            line(sender, "/bosses nemesis list|summon|clear <player>", "manage Nemeses");
            line(sender, "/bosses escalate <player>", "trigger an Escalation on a player");
            line(sender, "/bosses promote [ranks]", "promote the boss you are looking at");
            line(sender, "/bosses give <player> waystone [amount]", "give Waystones");
            line(sender, "/bosses curse <player> <curse>", "make a curse's effect happen now (testing)");
            line(sender, "/bosses list | killall | reload", "admin tools");
        }
    }

    private void guide(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 2) {
            if (!sender.hasPermission(ADMIN)) {
                error(sender, "Only admins can give the guide to other players.");
                return;
            }
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                error(sender, "Player not found: " + args[1]);
                return;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            error(sender, "Usage: /bosses guide <player>");
            return;
        }
        plugin.guide().give(target);
        if (target != sender) {
            info(sender, "Gave the guide to " + target.getName() + ".");
        }
    }

    private void apply(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            error(sender, "Only players can do that.");
            return;
        }
        ItemService items = plugin.items();
        PlayerInventory inv = player.getInventory();
        ItemStack main = inv.getItemInMainHand();
        ItemStack off = inv.getItemInOffHand();
        boolean consumableInOff = items.isConsumable(off);
        boolean consumableInMain = items.isConsumable(main);
        if (!consumableInOff && !consumableInMain) {
            player.sendMessage(plugin.settings().messages.prefixed("apply-how"));
            return;
        }
        ItemStack consumable = consumableInOff ? off : main;
        ItemStack target = consumableInOff ? main : off;
        Component error = items.validate(consumable, target);
        if (error != null) {
            player.sendMessage(error);
            return;
        }
        Component confirm = items.confirmationPrompt(player, consumable, target, consumableInOff ? "cmd-main" : "cmd-off");
        if (confirm != null) {
            player.sendMessage(confirm.append(Component.text(" (/bosses apply)", NamedTextColor.DARK_GRAY)));
            return;
        }
        ItemStack updated = target.clone();
        Component message = items.apply(player, consumable, updated);
        ItemStack remaining = consumable.getAmount() > 1 ? consumable.asQuantity(consumable.getAmount() - 1) : null;
        if (consumableInOff) {
            inv.setItemInMainHand(updated);
            inv.setItemInOffHand(remaining);
        } else {
            inv.setItemInOffHand(updated);
            inv.setItemInMainHand(remaining);
        }
        player.sendMessage(message);
        plugin.relics().refresh(player);
    }

    private void inspect(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            error(sender, "Only players can do that.");
            return;
        }
        Entity looked = player.getTargetEntity(24);
        Boss boss = plugin.bosses().get(looked);
        if (boss != null) {
            player.sendMessage(Component.empty().append(boss.name()));
            info(player, "Health: " + (int) Math.ceil(boss.health()) + "/" + Math.round(boss.maxHealth())
                + "   Damage: x" + Text.num(boss.damageMultiplier()));
            if (boss.traits().isEmpty()) {
                info(player, "No traits.");
            }
            for (BossTrait t : boss.traits()) {
                player.sendMessage(Component.text(" • " + t.displayName() + " ", NamedTextColor.YELLOW)
                    .append(Component.text("(" + t.category().displayName() + ") ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(t.description(), NamedTextColor.GRAY)));
            }
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.isEmpty()) {
            error(player, "Hold an item or look at a boss.");
            return;
        }
        ItemService items = plugin.items();
        player.sendMessage(Component.empty().append(held.effectiveName()));
        ItemService.Kind kind = items.kind(held);
        if (kind == ItemService.Kind.RUNE || kind == ItemService.Kind.RELIC || kind == ItemService.Kind.CATALYST) {
            info(player, "This is a " + Text.pretty(kind.name()) + ". Use /bosses apply or click it onto equipment.");
            return;
        }
        if (kind == ItemService.Kind.SOUL || kind == ItemService.Kind.TROPHY) {
            info(player, "A Boss Soul. Right-click a boss with it, or throw it at one, to make the boss rise.");
            return;
        }
        List<Component> lore = items.buildLore(held);
        if (lore.isEmpty()) {
            info(player, "No Empowerments or Relics yet. Relic slots: 0/" + items.relicSlots(held)
                + "   Empowerments: 0/" + plugin.settings().empowermentMaxPerItem);
            return;
        }
        for (Component line : lore) {
            player.sendMessage(line);
        }
        info(player, "Empowerments: " + ItemService.list(held, Keys.EMPOWERMENTS).size() + "/"
            + plugin.settings().empowermentMaxPerItem);
    }

    private void stats(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            error(sender, "Only players can do that.");
            return;
        }
        player.sendMessage(Component.text("Your boss-hunting record", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        for (BossRank rank : BossRank.values()) {
            player.sendMessage(Component.text(" " + plugin.settings().rank(rank).title() + ": ", rank.color())
                .append(Component.text(PlayerData.kills(player, rank), NamedTextColor.WHITE)));
        }
        info(player, "Total: " + PlayerData.totalKills(player) + "   Relics bound: " + PlayerData.relicsBound(player));
    }

    private void nemesis(CommandSender sender, String[] args) {
        NemesisManager nm = plugin.nemesis();
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "mine";
        if (!action.equals("mine") && sender.hasPermission(ADMIN)) {
            switch (action) {
                case "list" -> {
                    List<NemesisRecord> all = args.length >= 3 ? nemesesOf(sender, args[2]) : nm.all();
                    if (all == null) {
                        return;
                    }
                    info(sender, all.size() + " Nemes" + (all.size() == 1 ? "is" : "es") + ":");
                    for (NemesisRecord r : all) {
                        sender.sendMessage(Component.text(" • ", NamedTextColor.DARK_GRAY).append(nm.displayName(r))
                            .append(Component.text("  hunts " + r.ownerName + ", " + nm.describeReturn(r),
                                NamedTextColor.GRAY)));
                    }
                }
                case "summon" -> {
                    Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : null;
                    if (target == null) {
                        error(sender, "Usage: /bosses nemesis summon <online player>");
                        return;
                    }
                    info(sender, "Called " + nm.summonNow(target.getUniqueId()) + " Nemes(es) to " + target.getName() + ".");
                }
                case "clear" -> {
                    if (args.length < 3) {
                        error(sender, "Usage: /bosses nemesis clear <player>");
                        return;
                    }
                    java.util.UUID owner = ownerId(args[2]);
                    if (owner == null) {
                        error(sender, "Unknown player: " + args[2]);
                        return;
                    }
                    info(sender, "Removed " + nm.clear(owner) + " Nemes(es) of " + args[2] + ".");
                }
                default -> error(sender, "Usage: /bosses nemesis [list|summon|clear] [player]");
            }
            return;
        }
        if (!(sender instanceof Player player)) {
            error(sender, "Usage: /bosses nemesis list|summon|clear <player>");
            return;
        }
        List<NemesisRecord> mine = nm.forOwner(player.getUniqueId());
        if (mine.isEmpty()) {
            info(player, "Nothing is hunting you... yet.");
            return;
        }
        player.sendMessage(Component.text("Your Nemeses", NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD));
        for (NemesisRecord r : mine) {
            player.sendMessage(Component.text(" ", NamedTextColor.GRAY).append(nm.displayName(r)));
            info(player, "   killed you " + r.kills + "x, you fled " + r.escapes + "x, " + r.traits.size()
                + " traits - " + nm.describeReturn(r));
        }
    }

    private @Nullable List<NemesisRecord> nemesesOf(CommandSender sender, String name) {
        java.util.UUID owner = ownerId(name);
        if (owner == null) {
            error(sender, "Unknown player: " + name);
            return null;
        }
        return plugin.nemesis().forOwner(owner);
    }

    /** Online player, cached offline player, or the name stored on a Nemesis record. */
    private java.util.@Nullable UUID ownerId(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        for (NemesisRecord r : plugin.nemesis().all()) {
            if (r.ownerName.equalsIgnoreCase(name)) {
                return r.owner;
            }
        }
        org.bukkit.OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        return cached == null ? null : cached.getUniqueId();
    }

    private void promote(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            error(sender, "Only players can do that.");
            return;
        }
        Boss boss = plugin.bosses().get(player.getTargetEntity(24));
        if (boss == null) {
            error(sender, "Look at a boss first.");
            return;
        }
        int steps = 1;
        if (args.length >= 2) {
            try {
                steps = Math.max(1, Integer.parseInt(args[1]));
            } catch (NumberFormatException ex) {
                error(sender, "Not a number: " + args[1]);
                return;
            }
        }
        Boss promoted = plugin.bosses().promote(boss, steps, player);
        if (promoted == null) {
            error(sender, "That boss can't be promoted (Nemesis, or already Ascendant).");
            return;
        }
        sender.sendMessage(Component.text("Promoted to ", NamedTextColor.GRAY).append(promoted.name()));
    }

    private void escalate(CommandSender sender, String[] args) {
        Player target = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player p ? p : null;
        if (target == null) {
            error(sender, "Usage: /bosses escalate <player>");
            return;
        }
        plugin.escalation().trigger(target);
        info(sender, "Escalation triggered on " + target.getName() + ".");
    }

    private void list(CommandSender sender) {
        Collection<Boss> active = plugin.bosses().active();
        info(sender, active.size() + " active boss(es) (max " + plugin.settings().maxActive + "):");
        for (Boss boss : active) {
            Location l = boss.entity().getLocation();
            sender.sendMessage(Component.text(" • ", NamedTextColor.DARK_GRAY).append(boss.name())
                .append(Component.text("  " + l.getWorld().getName() + " " + l.getBlockX() + " " + l.getBlockY() + " "
                    + l.getBlockZ() + "  ❤" + (int) Math.ceil(boss.health()), NamedTextColor.GRAY)));
        }
    }

    private void spawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            error(sender, "Only players can do that.");
            return;
        }
        if (args.length < 2) {
            error(sender, "Usage: /bosses spawn <mob> [rank] [trait,trait|none]");
            return;
        }
        EntityType type = PluginSettings.parseMob(args[1]);
        if (type == null || type.getEntityClass() == null || !Mob.class.isAssignableFrom(type.getEntityClass())) {
            error(sender, "Not a mob: " + args[1]);
            return;
        }
        if (PluginSettings.isMount(type)) {
            error(sender, "Rideable mobs can't be bosses.");
            return;
        }
        MobCategory category = plugin.settings().categoryFor(type);
        BossRank rank;
        if (args.length >= 3 && !args[2].equalsIgnoreCase("random")) {
            rank = BossRank.parse(args[2]);
            if (rank == null) {
                error(sender, "Unknown rank: " + args[2] + " (gray, green, red, purple, gold)");
                return;
            }
        } else {
            rank = category != null ? category.rollRank() : BossRank.values()[Rng.between(0, 4)];
        }
        List<BossTrait> forced = null;
        if (args.length >= 4) {
            forced = new ArrayList<>();
            String joined = String.join(",", Arrays.copyOfRange(args, 3, args.length));
            if (!joined.equalsIgnoreCase("none")) {
                for (String id : joined.split(",")) {
                    if (id.isBlank()) {
                        continue;
                    }
                    BossTrait t = plugin.traits().get(id.trim());
                    if (t == null) {
                        error(sender, "Unknown trait: " + id);
                        return;
                    }
                    forced.add(t);
                }
            }
        }
        Location at = player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(3));
        String categoryId = category != null ? category.id() : "custom";
        List<BossTrait> traits = forced;
        BossRank finalRank = rank;
        Entity spawned = player.getWorld().spawn(at, type.getEntityClass(), CreatureSpawnEvent.SpawnReason.COMMAND, e -> {
            if (e instanceof LivingEntity living) {
                plugin.bosses().createBoss(living, finalRank, categoryId, traits, true);
            }
        });
        Boss boss = plugin.bosses().get(spawned);
        if (boss != null) {
            sender.sendMessage(Component.text("Spawned ", NamedTextColor.GRAY).append(boss.name()));
        }
    }

    /** Testing: makes a curse's random event (the jump scare, the angel, a hiccup...) happen right now. */
    private void curse(CommandSender sender, String[] args) {
        if (args.length < 3) {
            error(sender, "Usage: /bosses curse <player> <curse>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            error(sender, "Player not found: " + args[1]);
            return;
        }
        RelicEffect curse = plugin.relics().get(args[2]);
        if (curse == null || !curse.curse()) {
            error(sender, "Unknown curse: " + args[2]);
            return;
        }
        if (curse.trigger(target, plugin.relics())) {
            info(sender, curse.displayName() + " triggered on " + target.getName() + ".");
        } else {
            info(sender, curse.displayName() + " has no single moment to trigger (it's always on, or needs something"
                + " to happen first). To try it, wear it: /bosses give " + target.getName() + " relic random " + curse.id());
        }
    }

    private void give(CommandSender sender, String[] args) {
        if (args.length < 3) {
            error(sender, "Usage: /bosses give <player> <gear|rune|relic|catalyst|compass|totem|soul|waystone|guide> ...");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            error(sender, "Player not found: " + args[1]);
            return;
        }
        ItemService items = plugin.items();
        String what = args[2].toLowerCase(Locale.ROOT);
        ItemStack item;
        switch (what) {
            case "gear" -> {
                BossRank rank = args.length >= 4 ? BossRank.parse(args[3]) : BossRank.GOLD;
                if (rank == null) {
                    error(sender, "Unknown rank: " + args[3]);
                    return;
                }
                GearKind kind = args.length >= 5 ? GearKind.parse(args[4]) : null;
                if (args.length >= 5 && kind == null) {
                    error(sender, "Unknown gear kind: " + args[4]);
                    return;
                }
                item = plugin.rewards().gear().create(rank, null, plugin.settings().rank(rank).name() + " Boss", kind);
            }
            case "rune" -> {
                BossRank rank = args.length >= 4 ? BossRank.parse(args[3]) : BossRank.GOLD;
                if (rank == null) {
                    error(sender, "Unknown rank: " + args[3]);
                    return;
                }
                if (args.length >= 5) {
                    EmpowermentStat stat = plugin.settings().stat(args[4]);
                    if (stat == null) {
                        error(sender, "Unknown stat: " + args[4]);
                        return;
                    }
                    item = items.createRune(rank, stat, stat.roll(rank, plugin.settings().fixedRuneValues));
                } else {
                    item = items.createRandomRune(rank);
                }
            }
            case "relic" -> {
                RelicEffect effect = args.length >= 4 && !args[3].equalsIgnoreCase("random")
                    ? plugin.relics().get(args[3]) : plugin.relics().rollEffect();
                if (effect == null || effect.curse()) {
                    error(sender, "Unknown relic: " + (args.length >= 4 ? args[3] : "(none enabled)"));
                    return;
                }
                RelicEffect curse = null;
                if (args.length >= 5) {
                    if (args[4].equalsIgnoreCase("random")) {
                        curse = plugin.relics().rollCurse();
                    } else if (!args[4].equalsIgnoreCase("none")) {
                        curse = plugin.relics().get(args[4]);
                        if (curse == null || !curse.curse()) {
                            error(sender, "Unknown curse: " + args[4]);
                            return;
                        }
                    }
                } else if (Rng.chance(plugin.settings().corruptionChance)) {
                    curse = plugin.relics().rollCurse();
                }
                item = items.createRelic(effect, curse);
            }
            case "catalyst" -> {
                int amount = 1;
                if (args.length >= 4) {
                    try {
                        amount = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
                    } catch (NumberFormatException ex) {
                        error(sender, "Not a number: " + args[3]);
                        return;
                    }
                }
                item = items.createCatalyst().asQuantity(amount);
            }
            case "compass" -> {
                int tier = 1;
                if (args.length >= 4) {
                    try {
                        tier = Integer.parseInt(args[3]);
                    } catch (NumberFormatException ex) {
                        error(sender, "Not a number: " + args[3]);
                        return;
                    }
                }
                item = items.createCompass(tier);
            }
            case "soul" -> {
                BossRank rank = args.length >= 4 ? BossRank.parse(args[3]) : BossRank.GOLD;
                if (rank == null) {
                    error(sender, "Unknown rank: " + args[3]);
                    return;
                }
                int amount = 1;
                if (args.length >= 5) {
                    try {
                        amount = Math.max(1, Math.min(64, Integer.parseInt(args[4])));
                    } catch (NumberFormatException ex) {
                        error(sender, "Not a number: " + args[4]);
                        return;
                    }
                }
                item = items.createSoul(rank, amount);
            }
            case "totem" -> {
                BossRank rank = null;
                if (args.length >= 4 && !args[3].equalsIgnoreCase("random")) {
                    rank = BossRank.parse(args[3]);
                    if (rank == null) {
                        error(sender, "Unknown rank: " + args[3]);
                        return;
                    }
                }
                item = items.createTotem(rank);
            }
            case "waystone" -> {
                int amount = 1;
                if (args.length >= 4) {
                    try {
                        amount = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
                    } catch (NumberFormatException ex) {
                        error(sender, "Not a number: " + args[3]);
                        return;
                    }
                }
                item = plugin.waystones().createItem(null).asQuantity(amount);
            }
            case "guide" -> {
                plugin.guide().give(target);
                info(sender, "Gave the guide to " + target.getName() + ".");
                return;
            }
            default -> {
                error(sender, "Unknown item: " + args[2] + " (gear, rune, relic, catalyst, compass, totem, waystone, guide)");
                return;
            }
        }
        if (item == null) {
            error(sender, "Nothing to give (is that category disabled in config.yml?)");
            return;
        }
        for (ItemStack left : target.getInventory().addItem(item).values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), left);
        }
        sender.sendMessage(Component.text("Gave ", NamedTextColor.GRAY).append(item.displayName())
            .append(Component.text(" to " + target.getName(), NamedTextColor.GRAY)));
    }

    // =====================================================================
    // Tab completion
    // =====================================================================

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        boolean admin = sender.hasPermission(ADMIN);
        if (args.length <= 1) {
            List<String> subs = new ArrayList<>(PLAYER_SUBS);
            if (admin) {
                subs.addAll(ADMIN_SUBS);
            }
            return filter(subs, args.length == 0 ? "" : args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String last = args[args.length - 1];
        if (sub.equals("guide") && args.length == 2 && admin) {
            return filter(onlineNames(), last);
        }
        if (!admin) {
            return List.of();
        }
        if (sub.equals("nemesis")) {
            if (args.length == 2) {
                return filter(List.of("list", "summon", "clear"), last);
            }
            if (args.length == 3) {
                List<String> names = new ArrayList<>(onlineNames());
                for (NemesisRecord r : plugin.nemesis().all()) {
                    if (!names.contains(r.ownerName)) {
                        names.add(r.ownerName);
                    }
                }
                return filter(names, last);
            }
            return List.of();
        }
        if (sub.equals("promote") && args.length == 2) {
            return filter(List.of("1", "2", "3", "4", "5"), last);
        }
        if (sub.equals("escalate") && args.length == 2) {
            return filter(onlineNames(), last);
        }
        if (sub.equals("curse")) {
            if (args.length == 2) {
                return filter(onlineNames(), last);
            }
            if (args.length == 3) {
                List<String> ids = new ArrayList<>();
                for (RelicEffect r : plugin.relics().all()) {
                    if (r.curse()) {
                        ids.add(r.id());
                    }
                }
                return filter(ids, last);
            }
            return List.of();
        }
        if (sub.equals("spawn")) {
            return switch (args.length) {
                case 2 -> {
                    List<String> mobs = new ArrayList<>();
                    for (EntityType t : plugin.settings().eligibleMobs()) {
                        mobs.add(t.name().toLowerCase(Locale.ROOT));
                    }
                    yield filter(mobs, last);
                }
                case 3 -> filter(rankNames(true), last);
                default -> {
                    List<String> ids = new ArrayList<>();
                    for (BossTrait t : plugin.traits().all()) {
                        ids.add(t.id());
                    }
                    ids.add("none");
                    yield filter(ids, last.contains(",") ? last.substring(last.lastIndexOf(',') + 1) : last)
                        .stream().map(s -> last.contains(",") ? last.substring(0, last.lastIndexOf(',') + 1) + s : s)
                        .toList();
                }
            };
        }
        if (sub.equals("give")) {
            if (args.length == 2) {
                return filter(onlineNames(), last);
            }
            if (args.length == 3) {
                return filter(List.of("gear", "rune", "relic", "catalyst", "compass", "totem", "soul", "waystone", "guide"), last);
            }
            String what = args[2].toLowerCase(Locale.ROOT);
            if (args.length == 4) {
                return switch (what) {
                    case "gear", "rune" -> filter(rankNames(false), last);
                    case "totem" -> filter(rankNames(true), last);
                    case "compass" -> {
                        List<String> tiers = new ArrayList<>();
                        for (int i = 1; i <= plugin.settings().features.compassTiers.size(); i++) {
                            tiers.add(String.valueOf(i));
                        }
                        yield filter(tiers, last);
                    }
                    case "relic" -> {
                        List<String> ids = new ArrayList<>();
                        ids.add("random");
                        for (RelicEffect r : plugin.relics().enabledEffects(false)) {
                            ids.add(r.id());
                        }
                        yield filter(ids, last);
                    }
                    default -> List.of();
                };
            }
            if (args.length == 5) {
                return switch (what) {
                    case "gear" -> {
                        List<String> kinds = new ArrayList<>();
                        for (GearKind k : GearKind.values()) {
                            kinds.add(k.name().toLowerCase(Locale.ROOT));
                        }
                        yield filter(kinds, last);
                    }
                    case "rune" -> {
                        List<String> stats = new ArrayList<>();
                        for (EmpowermentStat s : plugin.settings().empowermentStats) {
                            stats.add(s.id());
                        }
                        yield filter(stats, last);
                    }
                    case "relic" -> {
                        List<String> ids = new ArrayList<>(List.of("none", "random"));
                        for (RelicEffect r : plugin.relics().enabledEffects(true)) {
                            ids.add(r.id());
                        }
                        yield filter(ids, last);
                    }
                    default -> List.of();
                };
            }
        }
        return List.of();
    }

    private static List<String> rankNames(boolean withRandom) {
        List<String> out = new ArrayList<>();
        for (BossRank r : BossRank.values()) {
            out.add(r.name().toLowerCase(Locale.ROOT));
        }
        if (withRandom) {
            out.add("random");
        }
        return out;
    }

    private static List<String> onlineNames() {
        List<String> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            out.add(p.getName());
        }
        return out;
    }

    private static List<String> filter(Collection<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(o);
            }
        }
        out.sort(String::compareTo);
        return out;
    }

    // =====================================================================

    private static void info(CommandSender sender, String text) {
        sender.sendMessage(Component.text(text, NamedTextColor.GRAY));
    }

    private static void error(CommandSender sender, String text) {
        sender.sendMessage(Component.text(text, NamedTextColor.RED));
    }

    private static void line(CommandSender sender, String command, String description) {
        sender.sendMessage(Component.text(command, NamedTextColor.YELLOW)
            .append(Component.text(" - " + description, NamedTextColor.GRAY)));
    }
}
