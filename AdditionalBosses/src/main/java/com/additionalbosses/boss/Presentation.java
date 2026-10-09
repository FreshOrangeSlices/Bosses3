package com.additionalbosses.boss;

import com.additionalbosses.AdditionalBosses;
import com.additionalbosses.config.Messages;
import com.additionalbosses.config.RankSettings;
import com.additionalbosses.util.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

/**
 * Encounter presentation: spawn announcements, first-engagement cues, ambient particles and death effects.
 * Higher ranks get stronger cues, but nothing runs every tick and nothing is spammed.
 */
public final class Presentation {

    private static final ItemStack BONE = ItemStack.of(Material.BONE);
    private static final ItemStack FLESH = ItemStack.of(Material.ROTTEN_FLESH);
    private static final BlockData SAND = Material.SAND.createBlockData();
    private static final BlockData STONE = Material.STONE.createBlockData();

    private final AdditionalBosses plugin;

    public Presentation(AdditionalBosses plugin) {
        this.plugin = plugin;
    }

    private RankSettings.Presentation of(Boss boss) {
        return plugin.settings().rank(boss.rank()).presentation();
    }

    public Component rankTitle(BossRank rank) {
        return rank.styled(plugin.settings().rank(rank).title());
    }

    /** Shown to players near a boss right after it appears naturally. */
    public void announceSpawn(Boss boss) {
        RankSettings.Presentation p = of(boss);
        if (p.announce() == RankSettings.Announce.NONE) {
            return;
        }
        Messages m = plugin.settings().messages;
        double radius = plugin.settings().announceRadius;
        LivingEntity e = boss.entity();
        for (Player player : e.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(e.getLocation()) > radius * radius) {
                continue;
            }
            switch (p.announce()) {
                case ACTIONBAR -> Fx.actionBar(player, m.get("spawn-actionbar", Placeholder.component("boss", boss.name())));
                case CHAT -> {
                    if (plugin.settings().bossChat) {
                        player.sendMessage(m.prefixed("spawn-chat", Placeholder.component("boss", boss.name())));
                    } else {
                        Fx.actionBar(player, m.get("spawn-actionbar", Placeholder.component("boss", boss.name())));
                    }
                }
                case TITLE -> {
                    player.showTitle(Title.title(
                        m.get("spawn-title", Placeholder.component("rank", rankTitle(boss.rank())), Placeholder.component("boss", boss.name())),
                        m.get("spawn-subtitle", Placeholder.component("rank", rankTitle(boss.rank())), Placeholder.component("boss", boss.name())),
                        Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2800), Duration.ofMillis(900))));
                    if (plugin.settings().bossChat) {
                        player.sendMessage(m.prefixed("spawn-chat", Placeholder.component("boss", boss.name())));
                    }
                }
                default -> {
                }
            }
            Fx.playTo(player, p.sound());
        }
    }

    /** The first time a player engages a boss: its rank cue plays for them, plus a one-time burst. */
    public void reveal(Boss boss, Player player) {
        if (boss.revealTo(player)) {
            Fx.playTo(player, of(boss).sound());
        }
        if (boss.revealOnce()) {
            LivingEntity e = boss.entity();
            int burst = 6 + boss.rank().stars() * 6;
            Fx.dust(Fx.center(e), boss.rank().bukkitColor(), 1.6f, burst, 0.7);
            if (boss.rank().top()) {
                Fx.particle(Fx.center(e), Particle.END_ROD, boss.rank() == BossRank.ASCENDANT ? 50 : 20, 0.6, 0.05);
            }
        }
    }

    /** Called about once per second for every loaded boss. Subtle by design. */
    public void ambient(Boss boss) {
        LivingEntity e = boss.entity();
        Location at = Fx.center(e);
        int count = of(boss).particles();
        if (count > 0) {
            Fx.dust(at, boss.rank().bukkitColor(), 1.1f, count, Math.max(0.3, e.getWidth() * 0.6));
            if (boss.rank() == BossRank.GOLD) {
                Fx.particle(at, Particle.WAX_ON, 1, 0.5, 0);
            } else if (boss.rank() == BossRank.ASCENDANT) {
                Fx.particle(at, Particle.END_ROD, 2, 0.6, 0.01);
                Fx.particle(at.clone().add(0, e.getHeight() * 0.6, 0), Particle.ELECTRIC_SPARK, 2, 0.5, 0.05);
            }
        }
        if (plugin.settings().environmentalParticles) {
            environment(e, at);
        }
        // Battle scars: a Nemesis trails smoke that thickens with every fight it has survived.
        int scars = boss.isNemesis() ? plugin.nemesis().scarLevel(boss) : 0;
        if (scars > 0) {
            Fx.particle(at, Particle.LARGE_SMOKE, scars, 0.3, 0.01);
            e.getWorld().spawnParticle(Particle.DUST, at.clone().add(0, e.getHeight() * 0.4, 0), 2 + scars, 0.25, 0.3, 0.25, 0,
                new Particle.DustOptions(Color.WHITE, 1.0f));
            if (scars >= 4) {
                Fx.particle(at, Particle.SOUL, 1, 0.4, 0.01);
            }
        }
        if (boss.lastStand()) {
            Fx.particle(at, Particle.SOUL_FIRE_FLAME, 3, 0.4, 0.02);
        }
    }

    /** Flavour that fits the mob: Blazes smoulder, Drowned drip, skeletons shed bone dust... */
    private static void environment(LivingEntity e, Location at) {
        World w = e.getWorld();
        double spread = Math.max(0.25, e.getWidth() * 0.5);
        switch (e.getType()) {
            case BLAZE -> w.spawnParticle(Particle.FLAME, at, 3, spread, 0.4, spread, 0.01);
            case MAGMA_CUBE -> w.spawnParticle(Particle.DRIPPING_LAVA, at, 2, spread, 0.2, spread, 0);
            case DROWNED, GUARDIAN, ELDER_GUARDIAN -> {
                w.spawnParticle(Particle.BUBBLE_POP, at, 3, spread, 0.4, spread, 0.02);
                w.spawnParticle(Particle.DRIPPING_WATER, at, 2, spread, 0.3, spread, 0);
            }
            case SKELETON, PARCHED -> w.spawnParticle(Particle.ITEM, at, 2, spread, 0.4, spread, 0.02, BONE);
            case STRAY -> w.spawnParticle(Particle.SNOWFLAKE, at, 2, spread, 0.4, spread, 0.01);
            case BOGGED -> w.spawnParticle(Particle.FALLING_SPORE_BLOSSOM, at, 1, spread, 0.4, spread, 0);
            case WITHER_SKELETON -> {
                w.spawnParticle(Particle.SMOKE, at, 2, spread, 0.5, spread, 0.01);
                w.spawnParticle(Particle.SOUL, at, 1, spread, 0.5, spread, 0.01);
            }
            case ZOMBIE, ZOMBIE_VILLAGER -> w.spawnParticle(Particle.ITEM, at, 1, spread, 0.4, spread, 0.02, FLESH);
            case HUSK -> w.spawnParticle(Particle.FALLING_DUST, at, 2, spread, 0.4, spread, 0, SAND);
            case SPIDER, CAVE_SPIDER -> w.spawnParticle(Particle.ITEM_COBWEB, at, 1, spread, 0.2, spread, 0);
            case CREEPER -> w.spawnParticle(Particle.SMOKE, at, 1, spread, 0.3, spread, 0.01);
            case ENDERMAN, ENDERMITE -> w.spawnParticle(Particle.PORTAL, at, 4, spread, 0.8, spread, 0.2);
            case WITCH, EVOKER, ILLUSIONER -> w.spawnParticle(Particle.WITCH, at, 2, spread, 0.4, spread, 0);
            case PIGLIN, PIGLIN_BRUTE, ZOMBIFIED_PIGLIN, HOGLIN, ZOGLIN ->
                w.spawnParticle(Particle.ASH, at, 3, spread, 0.4, spread, 0);
            case GHAST -> w.spawnParticle(Particle.WHITE_ASH, at, 4, spread, 0.6, spread, 0);
            case PHANTOM -> w.spawnParticle(Particle.MYCELIUM, at, 3, spread, 0.3, spread, 0);
            case BREEZE -> w.spawnParticle(Particle.SMALL_GUST, at, 1, spread, 0.3, spread, 0);
            case SLIME -> w.spawnParticle(Particle.ITEM_SLIME, at, 2, spread, 0.3, spread, 0);
            case SILVERFISH -> w.spawnParticle(Particle.FALLING_DUST, at, 1, spread, 0.2, spread, 0, STONE);
            case VINDICATOR, PILLAGER, RAVAGER -> w.spawnParticle(Particle.CRIT, at, 1, spread, 0.4, spread, 0.05);
            default -> {
            }
        }
    }

    public void death(Boss boss, @Nullable Player killer) {
        RankSettings.Presentation p = of(boss);
        LivingEntity e = boss.entity();
        Location at = Fx.center(e);
        Fx.dust(at, boss.rank().bukkitColor(), 1.8f, 10 + boss.rank().stars() * 8, 0.8);
        if (boss.rank() == BossRank.ASCENDANT) {
            Fx.particle(at, Particle.END_ROD, 120, 1.0, 0.3);
            Fx.particle(at, Particle.TOTEM_OF_UNDYING, 120, 1.0, 0.5);
            e.getWorld().strikeLightningEffect(e.getLocation());
        } else if (boss.rank() == BossRank.GOLD) {
            Fx.particle(at, Particle.TOTEM_OF_UNDYING, 80, 0.8, 0.4);
        } else if (boss.rank() == BossRank.PURPLE) {
            Fx.particle(at, Particle.WITCH, 40, 0.8, 0.1);
        }
        Fx.play(e.getLocation(), p.deathSound());

        if (p.deathBroadcast() == RankSettings.Broadcast.NONE || !plugin.settings().bossChat) {
            return;
        }
        Messages m = plugin.settings().messages;
        Component msg = killer == null
            ? m.prefixed("slain-unknown", Placeholder.component("boss", boss.name()))
            : m.prefixed("slain", Placeholder.component("boss", boss.name()), Placeholder.component("player", killer.displayName()));
        if (p.deathBroadcast() == RankSettings.Broadcast.SERVER) {
            Bukkit.getServer().sendMessage(msg);
        } else {
            double radius = 64;
            for (Player player : e.getWorld().getPlayers()) {
                if (player.getLocation().distanceSquared(e.getLocation()) <= radius * radius) {
                    player.sendMessage(msg);
                }
            }
        }
    }

    /** Everyone nearby sees a promoted boss's new rank; Ascendant promotions are told to the whole server. */
    public void announcePromotion(Boss boss, @Nullable Player by) {
        Messages m = plugin.settings().messages;
        LivingEntity e = boss.entity();
        Component title = rankTitle(boss.rank());
        for (Player player : e.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(e.getLocation()) > 64 * 64) {
                continue;
            }
            player.showTitle(Title.title(title, m.get("promoted-subtitle", Placeholder.component("boss", boss.name())),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2200), Duration.ofMillis(700))));
            Fx.playTo(player, of(boss).sound());
        }
        if (boss.rank() == BossRank.ASCENDANT) {
            broadcast(m.prefixed("ascended", Placeholder.component("boss", boss.name()),
                Placeholder.component("player", by == null ? Component.text("Someone") : by.displayName())));
        }
    }

    /**
     * A boss message for one player: in chat if boss chat is on (presentation.boss-chat), otherwise above their
     * hotbar, so chat stays clear.
     */
    public void tell(Player player, String key, TagResolver... resolvers) {
        Messages m = plugin.settings().messages;
        if (plugin.settings().bossChat) {
            player.sendMessage(m.prefixed(key, resolvers));
        } else {
            Fx.actionBar(player, m.get(key, resolvers));
        }
    }

    /** Server-wide boss news. Only sent when boss chat is on. */
    public void broadcast(Component message) {
        if (plugin.settings().bossChat) {
            Bukkit.getServer().sendMessage(message);
        }
    }

    public void messageNearby(Boss boss, String key, double radius) {
        LivingEntity e = boss.entity();
        Component msg = plugin.settings().messages.get(key, Placeholder.component("boss", boss.name()));
        for (Player player : e.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(e.getLocation()) <= radius * radius) {
                Fx.actionBar(player, msg);
            }
        }
    }
}
