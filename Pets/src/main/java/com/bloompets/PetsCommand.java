package com.bloompets;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** /pets: the menu, plus typed versions of everything the blooms do. */
public final class PetsCommand implements BasicCommand {

    private static final String ADMIN = "bloompets.admin";
    private static final List<String> PLAYER_SUBS = List.of("summon", "dismiss", "list", "rename", "toy", "bloom",
        "guide", "release", "help");
    private static final List<String> ADMIN_SUBS = List.of("give", "reload");

    private record PendingRelease(List<UUID> petIds, long until) {
    }

    private final BloomPets plugin;
    private final Map<UUID, PendingRelease> releases = new HashMap<>();

    public PetsCommand(BloomPets plugin) {
        this.plugin = plugin;
    }

    @Override
    public @Nullable String permission() {
        return "bloompets.use";
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        String rest = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim() : "";
        if (sub.equals("give") || sub.equals("reload")) {
            if (!sender.hasPermission(ADMIN)) {
                sender.sendMessage(Component.text("You don't have permission for that.", NamedTextColor.RED));
                return;
            }
            if (sub.equals("give")) {
                give(sender, args);
            } else {
                plugin.reloadSettings();
                sender.sendMessage(Component.text("BloomPets config reloaded.", NamedTextColor.GREEN));
            }
            return;
        }
        if (!(sender instanceof Player p)) {
            help(sender);
            return;
        }
        switch (sub) {
            case "", "menu" -> plugin.menus().openPets(p);
            case "summon" -> {
                Pet pet = find(p, rest);
                if (pet != null) {
                    plugin.pets().summon(p, pet, true);
                }
            }
            case "dismiss" -> plugin.pets().dismiss(p, true);
            case "list" -> list(p);
            case "rename" -> rename(p, rest);
            case "bloom" -> {
                Pet pet = rest.isEmpty() ? out(p) : find(p, rest);
                if (pet != null) {
                    plugin.menus().giveBloom(p, pet);
                }
            }
            case "release" -> release(p, rest);
            case "toy" -> {
                if (Blooms.hasToy(p)) {
                    Msg.bar(p, "You already have a Pet Toy.", NamedTextColor.GRAY);
                } else if (Blooms.giveToyIfMissing(p, true)) {
                    Msg.bar(p, "Here's your Pet Toy!", Msg.PINK);
                }
            }
            case "guide" -> p.openBook(Guide.book());
            default -> help(p);
        }
    }

    private @Nullable Pet find(Player p, String name) {
        if (name.isEmpty()) {
            Msg.chat(p, Component.text("Which pet? /pets list shows their names.", NamedTextColor.RED));
            return null;
        }
        Pet pet = plugin.store().byName(p.getUniqueId(), name);
        if (pet == null) {
            Msg.chat(p, Component.text("You don't have a pet called " + name + ".", NamedTextColor.RED));
        }
        return pet;
    }

    private @Nullable Pet out(Player p) {
        PetManager.Active a = plugin.pets().active(p.getUniqueId());
        if (a == null) {
            Msg.chat(p, Component.text("None of your pets is out. Name one: /pets bloom <name>", NamedTextColor.RED));
            return null;
        }
        return a.pet;
    }

    private void list(Player p) {
        List<Pet> pets = plugin.store().pets(p.getUniqueId());
        if (pets.isEmpty()) {
            Msg.chat(p, Component.text("No pets yet. Sneak + feed an animal its favourite food a few times to bond "
                + "with it.", NamedTextColor.GRAY));
            return;
        }
        Msg.chat(p, Component.text("Your pets (" + pets.size() + "/" + plugin.settings().maxPets + "):", Msg.PINK));
        long now = System.currentTimeMillis();
        for (Pet pet : pets) {
            Component line = Component.text("  " + pet.name, pet.species.category().color())
                .append(Component.text(" · " + pet.species.displayName() + " · level " + pet.level, NamedTextColor.GRAY));
            if (plugin.pets().isOut(pet)) {
                line = line.append(Component.text(" · out", NamedTextColor.GREEN));
            } else if (pet.resting(now)) {
                line = line.append(Component.text(" · resting " + ((pet.restingUntil - now + 999) / 1000) + "s",
                    NamedTextColor.RED));
            }
            p.sendMessage(line);
        }
    }

    private void rename(Player p, String name) {
        Pet pet = out(p);
        if (pet == null) {
            return;
        }
        if (name.isEmpty()) {
            Msg.chat(p, Component.text("Usage: /pets rename <new name> (renames the pet that's out)",
                NamedTextColor.RED));
            return;
        }
        plugin.pets().rename(p, pet, name);
    }

    /** /pets release Mochi, Biscuit, Pebble: several at once, separated by commas. */
    private void release(Player p, String names) {
        if (names.isEmpty()) {
            Msg.chat(p, Component.text("Usage: /pets release <name>[, <name>...] (or use Release pets in /pets)",
                NamedTextColor.RED));
            return;
        }
        List<Pet> pets = new ArrayList<>();
        for (String name : names.split(",")) {
            if (name.isBlank()) {
                continue;
            }
            Pet pet = find(p, name.trim());
            if (pet == null) {
                return;
            }
            if (!pets.contains(pet)) {
                pets.add(pet);
            }
        }
        if (pets.isEmpty()) {
            return;
        }
        List<UUID> ids = new ArrayList<>();
        List<String> shown = new ArrayList<>();
        for (Pet pet : pets) {
            ids.add(pet.id);
            shown.add(pet.name);
        }
        long now = System.currentTimeMillis();
        PendingRelease pending = releases.get(p.getUniqueId());
        if (pending == null || !pending.petIds().equals(ids) || pending.until() < now) {
            releases.put(p.getUniqueId(), new PendingRelease(ids, now + 15_000));
            Msg.chat(p, Component.text("Release " + String.join(", ", shown) + " for good? Anything they carry "
                + "comes back to you. Type the same command again within 15 seconds to confirm.", NamedTextColor.GOLD));
            return;
        }
        releases.remove(p.getUniqueId());
        for (Pet pet : pets) {
            plugin.pets().release(p, pet);
        }
        Msg.chat(p, Component.text(String.join(", ", shown) + " said goodbye. Take care!", NamedTextColor.GRAY));
    }

    /** /pets give <player> <species> [level] */
    private void give(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /pets give <player> <species> [level]", NamedTextColor.RED));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("No player called " + args[1] + " is online.", NamedTextColor.RED));
            return;
        }
        Species species = Species.parse(args[2]);
        if (species == null) {
            sender.sendMessage(Component.text("Unknown species " + args[2] + ".", NamedTextColor.RED));
            return;
        }
        int level = 1;
        if (args.length >= 4) {
            try {
                level = Math.max(1, Math.min(Pet.MAX_LEVEL, Integer.parseInt(args[3])));
            } catch (NumberFormatException ex) {
                sender.sendMessage(Component.text("The level must be a number from 1 to 10.", NamedTextColor.RED));
                return;
            }
        }
        PetStore store = plugin.store();
        if (store.pets(target.getUniqueId()).size() >= plugin.settings().maxPets) {
            sender.sendMessage(Component.text(target.getName() + " already has the most pets allowed.",
                NamedTextColor.RED));
            return;
        }
        Pet.RideStyle style = !species.small() ? Pet.RideStyle.NORMAL
            : java.util.concurrent.ThreadLocalRandom.current().nextBoolean() ? Pet.RideStyle.GROW : Pet.RideStyle.SHRINK;
        Pet pet = new Pet(UUID.randomUUID(), target.getUniqueId(), species, Blooms.freshName(store, target.getUniqueId()),
            style);
        pet.level = level;
        store.add(pet);
        Blooms.giveToyIfMissing(target);
        plugin.menus().refresh(target);
        Msg.chat(target, Component.text("You got a new pet: " + pet.name + " the " + species.displayName()
            + " (level " + level + "). Punch with your Pet Toy to see it.", Msg.PINK));
        if (sender != target) {
            sender.sendMessage(Component.text("Gave " + target.getName() + " " + pet.name + " the "
                + species.displayName() + " (level " + level + ").", NamedTextColor.GREEN));
        }
    }

    private void help(CommandSender sender) {
        List<String> lines = new ArrayList<>(List.of(
            "/pets  open your pets",
            "/pets summon <name>  /pets dismiss",
            "/pets list  see all your pets",
            "/pets rename <new name>  rename the pet that's out",
            "/pets toy  a new Pet Toy, if you lost yours",
            "/pets bloom [name]  get a pet's bloom back",
            "/pets guide  read the Pet Guide",
            "/pets release <name>[, <name>...]  say goodbye for good"));
        if (sender.hasPermission(ADMIN)) {
            lines.add("/pets give <player> <species> [level]  /pets reload");
        }
        sender.sendMessage(Component.text("BloomPets", Msg.PINK));
        for (String line : lines) {
            sender.sendMessage(Component.text("  " + line, NamedTextColor.GRAY));
        }
    }

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
        if ((sub.equals("summon") || sub.equals("release") || sub.equals("bloom")) && sender instanceof Player p
            && args.length == 2) {
            List<String> names = new ArrayList<>();
            for (Pet pet : plugin.store().pets(p.getUniqueId())) {
                names.add(pet.name);
            }
            return filter(names, last);
        }
        if (sub.equals("give") && admin) {
            if (args.length == 2) {
                List<String> names = new ArrayList<>();
                for (Player online : Bukkit.getOnlinePlayers()) {
                    names.add(online.getName());
                }
                return filter(names, last);
            }
            if (args.length == 3) {
                List<String> ids = new ArrayList<>();
                for (Species s : Species.values()) {
                    ids.add(s.id());
                }
                return filter(ids, last);
            }
            if (args.length == 4) {
                return filter(List.of("1", "3", "5", "10"), last);
            }
        }
        return List.of();
    }

    private static List<String> filter(Collection<String> options, String typed) {
        String t = typed.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(t)) {
                out.add(o);
            }
        }
        return out;
    }
}
