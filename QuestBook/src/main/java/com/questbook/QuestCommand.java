package com.questbook;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** /quests: opens the Quest Book. /quests book: a new copy. Admin: flag, reset, reload. */
final class QuestCommand implements BasicCommand {

    private static final String ADMIN = "questbook.admin";

    private final QuestBook plugin;
    private final Map<UUID, Long> lastBook = new HashMap<>();

    QuestCommand(QuestBook plugin) {
        this.plugin = plugin;
    }

    @Override
    public @Nullable String permission() {
        return "questbook.use";
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "flag" -> {
                if (!admin(sender)) {
                    return;
                }
                Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[1]) : null;
                if (target == null) {
                    sender.sendMessage(Component.text("Usage: /quests flag <online player> <flag>", NamedTextColor.RED));
                    return;
                }
                try {
                    QuestFlags.mark(target, args[2]);
                } catch (IllegalArgumentException ex) {
                    sender.sendMessage(Component.text("Flag names are letters, numbers, _ and -.", NamedTextColor.RED));
                    return;
                }
                sender.sendMessage(Component.text("Marked " + args[2] + " for " + target.getName() + ".", NamedTextColor.GREEN));
            }
            case "reset" -> {
                if (!admin(sender)) {
                    return;
                }
                Player target = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : null;
                if (target == null) {
                    sender.sendMessage(Component.text("Usage: /quests reset <online player>", NamedTextColor.RED));
                    return;
                }
                PlayerStore.State s = plugin.store().get(target.getUniqueId());
                if (s.locked) {
                    sender.sendMessage(Component.text(target.getName() + "'s quest file couldn't be read: fix or delete "
                        + "plugins/QuestBook/players/" + target.getUniqueId() + ".yml first.", NamedTextColor.RED));
                    return;
                }
                s.claimed.clear();
                s.done.clear();
                s.milestones.clear();
                s.welcomeClaimed = false;
                s.dirty = true;
                plugin.store().save(target.getUniqueId());
                sender.sendMessage(Component.text("Reset " + target.getName() + "'s claimed rewards. (Flags set by other "
                    + "plugins stay, so done quests can be claimed again.)", NamedTextColor.GREEN));
            }
            case "reload" -> {
                if (!admin(sender)) {
                    return;
                }
                plugin.menus().closeAll();
                plugin.load();
                sender.sendMessage(Component.text("QuestBook reloaded.", NamedTextColor.GREEN));
            }
            case "book" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(Component.text("Only players carry a Quest Book.", NamedTextColor.RED));
                    return;
                }
                long now = System.currentTimeMillis();
                if (QuestListener.carriesBook(p)) {
                    Msg.error(p, "You already have your Quest Book.");
                } else if (now - lastBook.getOrDefault(p.getUniqueId(), 0L) < 60_000) {
                    Msg.error(p, "You just got one. Try again in a minute.");
                } else {
                    lastBook.put(p.getUniqueId(), now);
                    for (ItemStack left : p.getInventory().addItem(QuestListener.book(plugin)).values()) {
                        p.getWorld().dropItemNaturally(p.getLocation(), left);
                    }
                }
            }
            default -> {
                if (sender instanceof Player p) {
                    plugin.menus().openHub(p);
                } else {
                    sender.sendMessage(Component.text("/quests flag <player> <flag> | reset <player> | reload",
                        NamedTextColor.GRAY));
                }
            }
        }
    }

    private static boolean admin(CommandSender sender) {
        if (sender.hasPermission(ADMIN)) {
            return true;
        }
        sender.sendMessage(Component.text("You don't have permission for that.", NamedTextColor.RED));
        return false;
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        List<String> options = new ArrayList<>();
        if (args.length <= 1) {
            options.add("book");
            if (sender.hasPermission(ADMIN)) {
                options.addAll(List.of("flag", "reset", "reload"));
            }
        } else if (args.length == 2 && sender.hasPermission(ADMIN)
            && (args[0].equalsIgnoreCase("flag") || args[0].equalsIgnoreCase("reset"))) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                options.add(p.getName());
            }
        }
        String typed = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(typed));
        return options;
    }
}
