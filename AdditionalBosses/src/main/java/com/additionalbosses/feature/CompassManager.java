package com.additionalbosses.feature;

import com.additionalbosses.AdditionalBosses;
import com.additionalbosses.boss.Boss;
import com.additionalbosses.boss.BossRank;
import com.additionalbosses.config.FeatureSettings;
import com.additionalbosses.item.ItemService;
import com.additionalbosses.nemesis.NemesisRecord;
import com.additionalbosses.util.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Hunter's Compass: while a player holds one, the needle points at the nearest boss in range and the action bar
 * shows how far away it is and which way to turn. The boss's name and rank are only revealed up close (a Nemesis
 * is always recognised), and the most dangerous bosses make the compass pulse like a heartbeat.
 */
public final class CompassManager {

    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    private final AdditionalBosses plugin;
    private final Set<UUID> pointing = new HashSet<>();
    private @Nullable BukkitTask task;

    public CompassManager(AdditionalBosses plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (task == null) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 20L);
        }
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (UUID id : pointing) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.setCompassTarget(p.getWorld().getSpawnLocation());
            }
        }
        pointing.clear();
    }

    private @Nullable ItemStack heldCompass(Player player) {
        ItemService items = plugin.items();
        PlayerInventory inv = player.getInventory();
        if (items.kind(inv.getItemInMainHand()) == ItemService.Kind.COMPASS) {
            return inv.getItemInMainHand();
        }
        if (items.kind(inv.getItemInOffHand()) == ItemService.Kind.COMPASS) {
            return inv.getItemInOffHand();
        }
        return null;
    }

    private void tick() {
        FeatureSettings f = plugin.settings().features;
        for (Player player : Bukkit.getOnlinePlayers()) {
            ItemStack compass = f.compassEnabled ? heldCompass(player) : null;
            if (compass == null) {
                if (pointing.remove(player.getUniqueId())) {
                    player.setCompassTarget(player.getWorld().getSpawnLocation());
                }
                continue;
            }
            track(player, f.compassTier(plugin.items().compassTier(compass)));
        }
    }

    private void track(Player player, FeatureSettings.CompassTier tier) {
        Location here = player.getLocation();
        Boss best = null;
        double bestDist = Double.MAX_VALUE;
        Boss ownNemesis = null;
        double ownDist = Double.MAX_VALUE;
        for (Boss boss : plugin.bosses().active()) {
            Location at = boss.entity().getLocation();
            if (!at.getWorld().equals(here.getWorld())) {
                continue;
            }
            double d = at.distance(here);
            if (d > tier.range()) {
                continue;
            }
            if (d < bestDist) {
                bestDist = d;
                best = boss;
            }
            NemesisRecord r = plugin.nemesis().get(boss.nemesisId());
            if (r != null && r.owner.equals(player.getUniqueId()) && d < ownDist) {
                ownDist = d;
                ownNemesis = boss;
            }
        }
        if (ownNemesis != null) {
            best = ownNemesis; // your own Nemesis always takes priority
            bestDist = ownDist;
        }
        if (best == null) {
            player.sendActionBar(Component.text("No boss within " + Math.round(tier.range()) + " blocks",
                NamedTextColor.DARK_GRAY));
            if (pointing.remove(player.getUniqueId())) {
                player.setCompassTarget(player.getWorld().getSpawnLocation());
            }
            return;
        }
        Location at = best.entity().getLocation();
        player.setCompassTarget(at);
        pointing.add(player.getUniqueId());

        boolean revealed = best.isNemesis() || bestDist <= tier.revealDistance();
        Component name = revealed ? best.name() : Component.text("☠ Unknown Boss", NamedTextColor.GRAY);
        double dy = at.getY() - here.getY();
        String vertical = dy > 6 ? " ▲" : dy < -6 ? " ▼" : "";
        player.sendActionBar(Component.empty().append(name)
            .append(Component.text("   " + Math.round(bestDist) + "m ", NamedTextColor.WHITE))
            .append(Component.text(arrow(here, at) + vertical, NamedTextColor.YELLOW)));

        boolean dangerous = best.isNemesis() || best.rank().atLeast(BossRank.PURPLE);
        if (dangerous && bestDist <= 48) {
            float closeness = (float) (1.0 - bestDist / 48.0);
            Fx.playTo(player, Fx.sound("entity.warden.heartbeat", 0.4f + closeness * 0.8f, 0.8f + closeness * 0.5f));
        }
    }

    /** Arrow relative to where the player is looking: ↑ ahead, → to the right, ↓ behind... */
    private static String arrow(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double diff = targetYaw - from.getYaw();
        diff = ((diff % 360) + 540) % 360 - 180; // wrap into [-180, 180)
        int index = (int) Math.round(diff / 45.0);
        return ARROWS[((index % 8) + 8) % 8];
    }
}
