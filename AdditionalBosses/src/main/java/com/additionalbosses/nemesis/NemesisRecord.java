package com.additionalbosses.nemesis;

import com.additionalbosses.boss.BossRank;
import com.additionalbosses.config.PluginSettings;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Everything remembered about one Nemesis. Saved in plugins/AdditionalBosses/nemesis.yml, so a Nemesis survives
 * restarts even though its mob is never saved in the world.
 */
public final class NemesisRecord {

    public final String id;
    public final UUID owner;
    public String ownerName;
    public EntityType type;
    public BossRank baseRank;
    public BossRank rank;
    public int baseTraitCount;
    public List<String> traits = new ArrayList<>();
    public List<String> titles = new ArrayList<>();
    public int level = 1;
    public int kills;
    public int escapes;
    public int returns;
    public int meleeHits;
    public int rangedHits;
    public long createdAt;
    public long returnAt;
    /** Its own name ("Scrawl"), unique on the server. */
    public String name = "";
    /** What it was known for as a boss ("Bulwark" -> "Scrawl the Bulwark"). */
    public String epithet = "";
    /** Body kept the same between returns: baby or adult, slime size, what it held. */
    public boolean baby;
    public int size = -1;
    public String hand = "";
    /** Gear tier it already wore as a boss, so its Nemesis gear never looks worse (0 chain .. 3 netherite). */
    public int gearFloor;
    /** Levels gained from killing other mobs during the current outing (capped per outing). */
    public int outingKills;

    /** Runtime only: server tick until which it prowls (0 = not prowling). */
    public int prowlUntil;
    /** Runtime only: someone other than its prey who attacked it while prowling (it fights back). */
    public @Nullable UUID provoker;
    /** Runtime only: the living mob while the Nemesis is in the world. */
    public @Nullable UUID entity;
    /** Runtime only: ticks the owner has been out of reach. */
    public int lostTicks;

    public NemesisRecord(String id, UUID owner, String ownerName, EntityType type, BossRank rank) {
        this.id = id;
        this.owner = owner;
        this.ownerName = ownerName;
        this.type = type;
        this.baseRank = rank;
        this.rank = rank;
    }

    public String title() {
        return titles.isEmpty() ? "Vengeful" : titles.get(titles.size() - 1);
    }

    public void save(ConfigurationSection s) {
        s.set("owner", owner.toString());
        s.set("owner-name", ownerName);
        s.set("type", type.name());
        s.set("base-rank", baseRank.name());
        s.set("rank", rank.name());
        s.set("base-trait-count", baseTraitCount);
        s.set("traits", traits);
        s.set("titles", titles);
        s.set("level", level);
        s.set("kills", kills);
        s.set("escapes", escapes);
        s.set("returns", returns);
        s.set("melee-hits", meleeHits);
        s.set("ranged-hits", rangedHits);
        s.set("created-at", createdAt);
        s.set("return-at", returnAt);
        s.set("name", name);
        s.set("epithet", epithet);
        s.set("baby", baby);
        s.set("size", size);
        s.set("hand", hand);
        s.set("gear-floor", gearFloor);
    }

    public static @Nullable NemesisRecord load(String id, ConfigurationSection s) {
        try {
            EntityType type = PluginSettings.parseMob(s.getString("type", ""));
            BossRank base = BossRank.parse(s.getString("base-rank"));
            BossRank rank = BossRank.parse(s.getString("rank"));
            if (type == null || base == null || rank == null) {
                return null;
            }
            NemesisRecord r = new NemesisRecord(id, UUID.fromString(s.getString("owner", "")),
                s.getString("owner-name", "?"), type, base);
            r.rank = rank;
            r.baseTraitCount = s.getInt("base-trait-count");
            r.traits = new ArrayList<>(s.getStringList("traits"));
            r.titles = new ArrayList<>(s.getStringList("titles"));
            r.level = s.getInt("level", 1);
            r.kills = s.getInt("kills");
            r.escapes = s.getInt("escapes");
            r.returns = s.getInt("returns");
            r.meleeHits = s.getInt("melee-hits");
            r.rangedHits = s.getInt("ranged-hits");
            r.createdAt = s.getLong("created-at");
            r.returnAt = s.getLong("return-at");
            r.name = s.getString("name", "");
            r.epithet = s.getString("epithet", "");
            r.baby = s.getBoolean("baby", false);
            r.size = s.getInt("size", -1);
            r.hand = s.getString("hand", "");
            r.gearFloor = s.getInt("gear-floor", 0);
            return r;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
