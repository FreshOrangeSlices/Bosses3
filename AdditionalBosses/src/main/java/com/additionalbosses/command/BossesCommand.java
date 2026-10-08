package com.additionalbosses.command;

import com.additionalbosses.AdditionalBosses;
import com.additionalbosses.boss.Boss;
import com.additionalbosses.boss.BossRank;
import com.additionalbosses.config.EmpowermentStat;
import com.additionalbosses.config.MobCategory;
import com.additionalbosses.config.PluginSettings;
import com.additionalbosses.item.ItemService;
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
    private static final List<String> PLAYER_SUBS = List.of("help", "guide", "apply", "inspect", "stats");
    private static final List<String> ADMIN_SUBS = List.of("spawn", "give", "list", "killall", "reload");

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
            case "spawn" -> spawn(sender, args);
            case "give" -> give(sender, args);
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
        if (sender.hasPermission(ADMIN)) {
            line(sender, "/bosses spawn <mob> [rank] [trait,trait]", "spawn a boss where you look");
            line(sender, "/bosses give <player> gear [rank] [kind]", "give Boss Gear");
            line(sender, "/bosses give <player> rune [rank] [stat]", "give an Empowerment Rune");
            line(sender, "/bosses give <player> relic [relic|random] [curse|none|random]", "give a Relic");
            line(sender, "/bosses give <player> catalyst|guide", "give a Catalyst or guide");
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

    private void give(CommandSender sender, String[] args) {
        if (args.length < 3) {
            error(sender, "Usage: /bosses give <player> <gear|rune|relic|catalyst|guide> ...");
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
                    item = items.createRune(rank, stat, stat.roll(rank));
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
            case "guide" -> {
                plugin.guide().give(target);
                info(sender, "Gave the guide to " + target.getName() + ".");
                return;
            }
            default -> {
                error(sender, "Unknown item: " + args[2] + " (gear, rune, relic, catalyst, guide)");
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
                return filter(List.of("gear", "rune", "relic", "catalyst", "guide"), last);
            }
            String what = args[2].toLowerCase(Locale.ROOT);
            if (args.length == 4) {
                return switch (what) {
                    case "gear", "rune" -> filter(rankNames(false), last);
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
