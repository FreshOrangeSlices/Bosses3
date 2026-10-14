package com.bloompets;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

/**
 * How happy each pet is. Kept as 0-100 inside, shown to players only as 1-10 hearts in a colour.
 *
 * <ul>
 *   <li>Feeding, petting (sneak + right-click with an empty hand) and fetch cheer a pet up.</li>
 *   <li>It slowly drops while the pet is out (a little faster at a post). After a long time out with you a pet gets
 *   tired and drops three times as fast until it rests in its bloom, where it also cheers up.</li>
 *   <li>Reaching full happiness through your care makes it love you: its mood drops much slower for a while (lost
 *   if it faints).</li>
 *   <li>A sad pet sulks: its bonus, its mcMMO skills and its storage are off until it feels better. Nothing is
 *   lost; it all comes back the moment it cheers up.</li>
 * </ul>
 */
public final class Happiness {

    private static final TextColor LOVE = TextColor.color(0xF06292);

    private final BloomPets plugin;
    private int tick;
    /** Cooldowns by pet id (kept here so they survive the owner relogging). */
    private final java.util.Map<java.util.UUID, long[]> cooldowns = new java.util.HashMap<>();
    private static final int FEED = 0;
    private static final int FETCH = 1;
    private static final int PAT = 2;

    public Happiness(BloomPets plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private Settings s() {
        return plugin.settings();
    }

    // =====================================================================
    //  Reading the mood
    // =====================================================================

    /** Happiness right now, counting the rest it has had in its bloom since it went in. */
    public double current(Pet pet, long now) {
        if (!s().happiness) {
            return 100;
        }
        double h = pet.happiness;
        if (pet.stashedAt > 0 && now > pet.stashedAt) { // (0 while it's out)
            h += s().restPerMinute * (now - pet.stashedAt) / 60_000.0;
        }
        return Math.max(0, Math.min(100, h));
    }

    /** A sad pet's bonus, skills and storage are off. */
    public boolean sad(Pet pet) {
        return s().happiness && current(pet, System.currentTimeMillis()) < s().sadBelow;
    }

    public boolean loved(Pet pet, long now) {
        return s().happiness && pet.lovedUntil > now;
    }

    /** Tired after a long time out with you (counting the rest it has had since). */
    public boolean tired(Pet pet, long now) {
        return s().happiness && tiredMillis(pet, now) >= s().tiredAfter;
    }

    private long tiredMillis(Pet pet, long now) {
        long t = pet.tiredMillis;
        if (pet.stashedAt > 0 && now > pet.stashedAt) {
            t -= (long) ((now - pet.stashedAt) * (s().tiredAfter / (s().minutesToRest * 60_000.0)));
        }
        return Math.max(0, t);
    }

    /** 1-10, rounded up (35 shows as 4). */
    public int hearts(Pet pet, long now) {
        return Math.max(1, Math.min(10, (int) Math.ceil(current(pet, now) / 10.0 - 1e-9)));
    }

    private TextColor color(Pet pet, long now) {
        if (loved(pet, now)) {
            return LOVE;
        }
        if (sad(pet)) {
            return NamedTextColor.RED;
        }
        int h = hearts(pet, now);
        return h >= 8 ? NamedTextColor.GREEN : h >= 6 ? NamedTextColor.YELLOW : NamedTextColor.GOLD;
    }

    private String word(Pet pet, long now) {
        if (loved(pet, now)) {
            return "Loves you!";
        }
        if (sad(pet)) {
            return "Sad";
        }
        int h = hearts(pet, now);
        return h >= 10 ? "Overjoyed" : h >= 8 ? "Happy" : h >= 6 ? "Content" : "Okay";
    }

    /** "Mood: ❤❤❤❤❤❤❤❤❤❤ Happy · Tired" for the bloom and the menu. */
    public Component line(Pet pet, long now) {
        int h = hearts(pet, now);
        TextColor c = color(pet, now);
        Component line = Component.text("Mood: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
            .append(Component.text("❤".repeat(h), c))
            .append(Component.text("❤".repeat(10 - h), NamedTextColor.DARK_GRAY))
            .append(Component.text(" " + word(pet, now), c));
        if (tired(pet, now) && plugin.pets().activeFor(pet) != null && !pet.stationed()) {
            line = line.append(Component.text(" · Tired", NamedTextColor.GRAY));
        }
        return line;
    }

    /** Short version for the action bar. */
    public Component bar(Pet pet, long now) {
        int h = hearts(pet, now);
        TextColor c = color(pet, now);
        return Component.text("❤".repeat(h), c).append(Component.text("❤".repeat(10 - h), NamedTextColor.DARK_GRAY));
    }

    // =====================================================================
    //  Changing it
    // =====================================================================

    /** The pet is out (summoned, or back at its post): the rest it had in its bloom counts now. */
    public void cameOut(Pet pet) {
        long now = System.currentTimeMillis();
        if (pet.stashedAt > 0 && now > pet.stashedAt) {
            boolean wasTired = s().happiness && pet.tiredMillis >= s().tiredAfter;
            pet.tiredMillis = tiredMillis(pet, now);
            if (wasTired && pet.tiredMillis == 0) {
                QuestFlags.mark(pet.owner, "pet_rested"); // a tired pet rested until it was fresh again
            }
            pet.happiness = Math.min(100, Math.max(pet.happiness, current(pet, now)));
        }
        pet.stashedAt = 0;
        pet.wasSad = s().happiness && pet.happiness < s().sadBelow;
    }

    /**
     * It went back into its bloom (put away, fainted, unloaded): it starts resting. Not a pet that keeps its post
     * (its chunk unloading, or a restart, isn't a rest); unless it fainted there.
     */
    public void wentIn(PetManager.Active a) {
        if (a.home != null && !plugin.pets().fainting(a)) {
            return;
        }
        if (a.pet.stashedAt == 0) {
            a.pet.stashedAt = System.currentTimeMillis();
        }
    }

    /** Fainting (or dying) breaks the "loves you" bonus. */
    public void fainted(Pet pet) {
        pet.lovedUntil = 0;
    }

    /** Feeding: true if it cheered up (at most once a minute). */
    private long[] cooldowns(Pet pet) {
        return cooldowns.computeIfAbsent(pet.id, k -> new long[3]);
    }

    public boolean fed(Player owner, PetManager.Active a) {
        long now = System.currentTimeMillis();
        if (!canFeedCheer(a.pet)) {
            return false;
        }
        cooldowns(a.pet)[FEED] = now;
        cheer(owner, a, s().happyFeed, false);
        return true;
    }

    public boolean canFeedCheer(Pet pet) {
        return s().happiness && System.currentTimeMillis() - cooldowns(pet)[FEED] >= s().happyFeedCooldown;
    }

    public void fetched(Player owner, PetManager.Active a) {
        long now = System.currentTimeMillis();
        if (!s().happiness || now - cooldowns(a.pet)[FETCH] < s().happyFetchCooldown) {
            return;
        }
        cooldowns(a.pet)[FETCH] = now;
        cheer(owner, a, s().happyFetch, false);
    }

    /** Sneak + right-click your pet with an empty hand: a pat on the head. */
    public void pet(Player owner, PetManager.Active a) {
        long now = System.currentTimeMillis();
        Mob m = a.entity;
        Location head = m.getLocation().add(0, m.getHeight() + 0.2, 0);
        m.getWorld().spawnParticle(Particle.HEART, head, 2, 0.25, 0.15, 0.25, 0);
        m.getWorld().playSound(m.getLocation(), Sound.ENTITY_CAT_PURR, 0.6f, 1.3f);
        QuestFlags.mark(owner, "pet_play");
        if (!s().happiness) {
            Msg.bar(owner, Component.text(a.pet.name + " loves the attention! ♥", Msg.PINK));
            return;
        }
        if (now - cooldowns(a.pet)[PAT] < s().happyPettingCooldown) {
            Msg.bar(owner, Component.text(a.pet.name + " enjoys the pat  ", Msg.PINK).append(bar(a.pet, now)));
            return;
        }
        cooldowns(a.pet)[PAT] = now;
        cheer(owner, a, s().happyPetting, true);
    }

    private void cheer(Player owner, PetManager.Active a, double amount, boolean announce) {
        Pet pet = a.pet;
        long now = System.currentTimeMillis();
        boolean wasSad = sad(pet);
        pet.happiness = Math.min(100, current(pet, now) + amount);
        if (pet.happiness >= 100 && s().lovedMillis > 0) {
            boolean already = loved(pet, now);
            pet.lovedUntil = now + s().lovedMillis;
            if (!already) {
                Mob m = a.entity;
                m.getWorld().spawnParticle(Particle.HEART, m.getLocation().add(0, m.getHeight() + 0.3, 0), 10,
                    0.5, 0.4, 0.5, 0);
                owner.playSound(owner.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.8f);
                Msg.chat(owner, Component.text(pet.name + " loves you! ♥ ", LOVE)
                    .append(Component.text("It stays happy much longer for a while.", NamedTextColor.GRAY)));
            }
        }
        if (announce) {
            Msg.bar(owner, Component.text(pet.name + "  ", pet.species.category().color()).append(bar(pet, now)));
        }
        if (wasSad && !sad(pet)) {
            feltBetter(owner, a);
        }
        pet.wasSad = sad(pet);
    }

    // =====================================================================
    //  Every second
    // =====================================================================

    private void tick() {
        if (!s().happiness) {
            return;
        }
        tick++;
        long now = System.currentTimeMillis();
        for (PetManager.Active a : plugin.pets().all()) {
            if (!plugin.pets().fainting(a)) {
                update(a, true, now);
            }
        }
        for (PetManager.Active a : plugin.pets().postedPets()) {
            if (!plugin.pets().fainting(a)) {
                update(a, false, now);
            }
        }
    }

    private void update(PetManager.Active a, boolean following, long now) {
        Pet pet = a.pet;
        Player owner = Bukkit.getPlayer(a.owner);
        if (following) {
            pet.tiredMillis = Math.min(s().tiredAfter, pet.tiredMillis + 1000); // (so 5 minutes' rest always does it)
        }
        boolean tired = following && pet.tiredMillis >= s().tiredAfter;
        if (following || owner != null || s().postDropsOffline) {
            double perMinute = 1.0 / (following ? s().minutesPerPointOut : s().minutesPerPointPost);
            if (tired) {
                perMinute *= s().tiredMultiplier;
            }
            if (loved(pet, now)) {
                perMinute *= s().lovedMultiplier;
            }
            pet.happiness = Math.max(0, pet.happiness - perMinute / 60.0);
        }
        boolean sad = pet.happiness < s().sadBelow;
        Mob m = a.entity;
        // a droopy rain cloud for a sad pet, hearts for one that loves you
        if (sad && tick % 2 == 0) {
            m.getWorld().spawnParticle(Particle.FALLING_WATER, m.getLocation().add(0, m.getHeight() + 0.5, 0), 3,
                m.getWidth() / 3, 0.05, m.getWidth() / 3, 0);
        } else if (loved(pet, now) && tick % 4 == 0) {
            m.getWorld().spawnParticle(Particle.HEART, m.getLocation().add(0, m.getHeight() + 0.3, 0), 1,
                0.3, 0.2, 0.3, 0);
        }
        if (pet.wasSad != null && pet.wasSad != sad && owner != null) {
            if (sad) {
                gotSad(owner, a);
            } else {
                feltBetter(owner, a);
            }
        }
        pet.wasSad = sad;
        if (following) {
            if (pet.wasTired != null && !pet.wasTired && tired && owner != null) {
                Msg.chat(owner, Component.text(pet.name + " is getting tired. ", NamedTextColor.GOLD)
                    .append(Component.text("Let it rest in its bloom for a few minutes, or it'll get grumpy fast.",
                        NamedTextColor.GRAY)));
            }
            pet.wasTired = tired;
        }
    }

    private void gotSad(Player owner, PetManager.Active a) {
        refresh(a);
        if (a.pet.species != Species.VEX) {
            plugin.menus().closeFor(a.pet); // a sad pet won't carry things (the Vex keeps its gear on)
        }
        Msg.chat(owner, Component.text(a.pet.name + " is feeling down" + (a.home != null ? " at its post" : "") + ". ",
                NamedTextColor.RED)
            .append(Component.text("It sulks until it cheers up: no bonus, skills or storage. Feed it, pet it "
                + "(sneak + right-click with an empty hand), play fetch, or let it rest in its bloom.",
                NamedTextColor.GRAY)));
        owner.playSound(owner.getLocation(), Sound.ENTITY_WOLF_WHINE, 0.6f, 1.2f);
    }

    private void feltBetter(Player owner, PetManager.Active a) {
        refresh(a);
        Msg.chat(owner, Component.text(a.pet.name + " is feeling better! Its bonus is back.", NamedTextColor.GREEN));
    }

    /** Owner bonuses follow the mood. */
    private void refresh(PetManager.Active a) {
        Player owner = Bukkit.getPlayer(a.owner);
        if (owner != null && a.home == null && plugin.pets().active(a.owner) == a) {
            plugin.bonuses().apply(owner, a);
        }
        if (owner != null) {
            plugin.menus().refresh(owner);
        }
    }
}
