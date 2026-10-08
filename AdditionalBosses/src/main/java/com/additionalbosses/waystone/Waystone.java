package com.additionalbosses.waystone;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** One placed waystone. Saved in plugins/AdditionalBosses/waystones.yml. */
public final class Waystone {

    public final String id;
    public String name;
    public final UUID owner;
    public String ownerName;
    public final String world;
    public final int x;
    public final int y;
    public final int z;
    public Material icon = Material.LODESTONE;

    public Waystone(String id, String name, UUID owner, String ownerName, String world, int x, int y, int z) {
        this.id = id;
        this.name = name;
        this.owner = owner;
        this.ownerName = ownerName;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static String key(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }

    public static String key(Block block) {
        return key(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    public String key() {
        return key(world, x, y, z);
    }

    public @Nullable World bukkitWorld() {
        return Bukkit.getWorld(world);
    }

    /** Centre of the block above the waystone: where travellers arrive. */
    public @Nullable Location arrival() {
        World w = bukkitWorld();
        return w == null ? null : new Location(w, x + 0.5, y + 1, z + 0.5);
    }

    public void save(ConfigurationSection s) {
        s.set("name", name);
        s.set("owner", owner.toString());
        s.set("owner-name", ownerName);
        s.set("world", world);
        s.set("x", x);
        s.set("y", y);
        s.set("z", z);
        s.set("icon", icon.name());
    }

    public static @Nullable Waystone load(String id, ConfigurationSection s) {
        try {
            Waystone w = new Waystone(id, s.getString("name", "Waystone"), UUID.fromString(s.getString("owner", "")),
                s.getString("owner-name", "?"), s.getString("world", "world"), s.getInt("x"), s.getInt("y"), s.getInt("z"));
            Material icon = Material.matchMaterial(s.getString("icon", "LODESTONE"));
            if (icon != null && icon.isItem() && !icon.isAir()) {
                w.icon = icon;
            }
            return w;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
