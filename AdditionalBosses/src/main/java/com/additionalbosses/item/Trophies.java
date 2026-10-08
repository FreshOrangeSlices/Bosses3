package com.additionalbosses.item;

import com.additionalbosses.AdditionalBosses;
import com.additionalbosses.boss.Boss;
import com.additionalbosses.boss.BossRank;
import com.additionalbosses.nemesis.NemesisRecord;
import com.additionalbosses.util.Clock;
import com.additionalbosses.util.Fx;
import com.additionalbosses.util.Keys;
import com.additionalbosses.util.Text;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.PiglinAbstract;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Collectible boss trophies (a "Blaze Core", "Ravager Horn"...) and Nemesis statues: a trophy that places the
 * slain Nemesis as a frozen, unkillable display mob with its title above its head.
 */
public final class Trophies {

    private record Look(String model, String name) {
    }

    private static final Map<EntityType, Look> LOOKS = new EnumMap<>(EntityType.class);

    static {
        LOOKS.put(EntityType.ZOMBIE, new Look("rotten_flesh", "Zombie Heart"));
        LOOKS.put(EntityType.HUSK, new Look("rotten_flesh", "Husk Heart"));
        LOOKS.put(EntityType.DROWNED, new Look("nautilus_shell", "Drowned Shell"));
        LOOKS.put(EntityType.ZOMBIE_VILLAGER, new Look("emerald", "Tarnished Emerald"));
        LOOKS.put(EntityType.SKELETON, new Look("bone", "Skeleton Fragment"));
        LOOKS.put(EntityType.STRAY, new Look("bone", "Frozen Bone"));
        LOOKS.put(EntityType.BOGGED, new Look("brown_mushroom", "Bogged Spore"));
        LOOKS.put(EntityType.PARCHED, new Look("bone", "Sunbleached Bone"));
        LOOKS.put(EntityType.WITHER_SKELETON, new Look("wither_skeleton_skull", "Withered Skull"));
        LOOKS.put(EntityType.CREEPER, new Look("gunpowder", "Volatile Core"));
        LOOKS.put(EntityType.SPIDER, new Look("spider_eye", "Broodmother Eye"));
        LOOKS.put(EntityType.CAVE_SPIDER, new Look("fermented_spider_eye", "Venom Gland"));
        LOOKS.put(EntityType.ENDERMAN, new Look("ender_pearl", "Void Shard"));
        LOOKS.put(EntityType.ENDERMITE, new Look("popped_chorus_fruit", "Endermite Husk"));
        LOOKS.put(EntityType.WITCH, new Look("glass_bottle", "Hex Bottle"));
        LOOKS.put(EntityType.PILLAGER, new Look("arrow", "Raider's Bolt"));
        LOOKS.put(EntityType.VINDICATOR, new Look("iron_axe", "Executioner's Blade"));
        LOOKS.put(EntityType.EVOKER, new Look("totem_of_undying", "Evoker's Idol"));
        LOOKS.put(EntityType.ILLUSIONER, new Look("ender_eye", "Illusion Eye"));
        LOOKS.put(EntityType.RAVAGER, new Look("goat_horn", "Ravager Horn"));
        LOOKS.put(EntityType.GUARDIAN, new Look("prismarine_shard", "Guardian Spine"));
        LOOKS.put(EntityType.ELDER_GUARDIAN, new Look("prismarine_crystals", "Elder Guardian Eye"));
        LOOKS.put(EntityType.BLAZE, new Look("blaze_rod", "Blaze Core"));
        LOOKS.put(EntityType.GHAST, new Look("ghast_tear", "Ghast Tear"));
        LOOKS.put(EntityType.MAGMA_CUBE, new Look("magma_cream", "Magma Heart"));
        LOOKS.put(EntityType.SLIME, new Look("slime_ball", "Slime Core"));
        LOOKS.put(EntityType.HOGLIN, new Look("leather", "Hoglin Hide"));
        LOOKS.put(EntityType.ZOGLIN, new Look("rotten_flesh", "Zoglin Tusk"));
        LOOKS.put(EntityType.PIGLIN, new Look("gold_nugget", "Piglin Signet"));
        LOOKS.put(EntityType.PIGLIN_BRUTE, new Look("golden_axe", "Brute's Axe"));
        LOOKS.put(EntityType.ZOMBIFIED_PIGLIN, new Look("gold_nugget", "Tarnished Signet"));
        LOOKS.put(EntityType.PHANTOM, new Look("phantom_membrane", "Phantom Wing"));
        LOOKS.put(EntityType.BREEZE, new Look("breeze_rod", "Breeze Core"));
        LOOKS.put(EntityType.SILVERFISH, new Look("iron_nugget", "Silverfish Scale"));
        LOOKS.put(EntityType.WARDEN, new Look("echo_shard", "Warden's Heart"));
        LOOKS.put(EntityType.WITHER, new Look("wither_rose", "Wither's Bloom"));
    }

    private final AdditionalBosses plugin;

    public Trophies(AdditionalBosses plugin) {
        this.plugin = plugin;
    }

    private static String day(LivingEntity e) {
        return String.valueOf(Clock.day());
    }

    /**
     * A trophy named after the mob type (a Blaze Core, a Ravager Horn...). Place it to get a tiny frozen copy of the
     * boss, or use it on a living boss to promote it.
     */
    public ItemStack createTrophy(Boss boss, @Nullable Player killer) {
        LivingEntity e = boss.entity();
        EntityType type = e.getType();
        Look look = LOOKS.getOrDefault(type, new Look("bone", Text.pretty(type.name()) + " Remnant"));
        ItemStack item = ItemStack.of(Material.PAPER);
        item.setData(DataComponentTypes.ITEM_MODEL, Key.key(look.model()));
        item.setData(DataComponentTypes.ITEM_NAME, boss.rank().styled(look.name()));
        item.setData(DataComponentTypes.MAX_STACK_SIZE, 1);
        if (boss.rank().atLeast(BossRank.PURPLE) || boss.isNemesis()) {
            item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        List<Component> lore = new ArrayList<>();
        lore.add(Text.line(boss.rank().starText() + " Boss Trophy", NamedTextColor.GOLD));
        lore.add(Text.line("From: " + boss.plainName(), NamedTextColor.GRAY));
        if (killer != null) {
            lore.add(Text.line("Slain by " + killer.getName() + " on day " + day(e), NamedTextColor.DARK_GRAY));
        }
        lore.add(Component.empty());
        lore.add(Text.line("Right-click a block: place a tiny copy.", NamedTextColor.DARK_GRAY));
        lore.add(Text.line("Right-click or throw it at a boss: promote it.", NamedTextColor.DARK_GRAY));
        item.lore(lore);
        String data = typeToken(e) + ";" + plugin.settings().features.trophyScale + ";" + gearString(e.getEquipment())
            + ";" + Text.MM.serialize(boss.name());
        item.editPersistentDataContainer(pdc -> {
            pdc.set(Keys.ITEM_KIND, PersistentDataType.STRING, ItemService.Kind.TROPHY.name());
            pdc.set(Keys.ITEM_RANK, PersistentDataType.STRING, boss.rank().name());
            pdc.set(Keys.STATUE, PersistentDataType.STRING, data);
        });
        return item;
    }

    /** The rank of the boss a trophy came from (older trophies: read from the name colour). */
    public static BossRank trophyRank(ItemStack trophy) {
        BossRank rank = BossRank.parse(trophy.getPersistentDataContainer().get(Keys.ITEM_RANK, PersistentDataType.STRING));
        if (rank != null) {
            return rank;
        }
        Component name = trophy.getData(DataComponentTypes.ITEM_NAME);
        if (name != null && name.color() != null) {
            for (BossRank r : BossRank.values()) {
                if (r.color().equals(name.color())) {
                    return r;
                }
            }
        }
        return BossRank.GRAY;
    }

    /** The Nemesis statue item (a spawn egg that places a frozen display of the slain Nemesis). */
    public ItemStack createStatue(Boss boss, NemesisRecord r, Component name) {
        LivingEntity e = boss.entity();
        double scale = e.getAttribute(Attribute.SCALE) == null ? 1.0 : e.getAttribute(Attribute.SCALE).getValue();
        String gear = gearString(e.getEquipment());
        List<Component> lore = new ArrayList<>();
        lore.add(Text.line("Nemesis Statue", NamedTextColor.WHITE));
        lore.add(Text.line("Level " + r.level + " · killed its prey " + r.kills + "× · fled " + r.escapes + "×",
            NamedTextColor.GRAY));
        lore.add(Text.line("Hunted " + r.ownerName + " · slain on day " + day(e), NamedTextColor.DARK_GRAY));
        lore.add(Component.empty());
        lore.add(Text.line("Right-click a block to place it.", NamedTextColor.DARK_GRAY));
        lore.add(Text.line("Sneak + right-click the statue to pick it up.", NamedTextColor.DARK_GRAY));
        return statueItem(typeToken(e), scale, gear, Text.MM.serialize(name), lore);
    }

    /** "ZOMBIE", or "ZOMBIE:baby" for a baby, so statues keep the look of the mob they came from. */
    private static String typeToken(LivingEntity e) {
        return e.getType().name() + (e instanceof org.bukkit.entity.Ageable a && !a.isAdult() ? ":baby" : "");
    }

    /** What the mob had on, slot by slot: head, chest, legs, feet, main hand, off hand. */
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
        EquipmentSlot.FEET, EquipmentSlot.HAND, EquipmentSlot.OFF_HAND};

    private static String gearString(@Nullable EntityEquipment eq) {
        List<String> parts = new ArrayList<>();
        for (EquipmentSlot slot : SLOTS) {
            String part = "AIR";
            if (eq != null) {
                try {
                    ItemStack item = eq.getItem(slot);
                    if (!item.isEmpty()) {
                        part = "b64:" + Base64.getEncoder().encodeToString(item.asOne().serializeAsBytes());
                    }
                } catch (IllegalArgumentException ignored) {
                    // slot not supported by this mob
                }
            }
            parts.add(part);
        }
        return String.join(",", parts);
    }

    private static @Nullable ItemStack gearItem(String part) {
        if (part.startsWith("b64:")) {
            try {
                return ItemStack.deserializeBytes(Base64.getDecoder().decode(part.substring(4)));
            } catch (RuntimeException ex) {
                return null;
            }
        }
        Material m = Material.matchMaterial(part); // older statues stored only the material
        return m == null || m.isAir() || !m.isItem() ? null : ItemStack.of(m);
    }

    private static void applyGear(@Nullable EntityEquipment eq, String gear) {
        if (eq == null) {
            return;
        }
        eq.clear();
        String[] parts = gear.split(",");
        if (parts.length == 1) {
            parts = new String[]{"AIR", "AIR", "AIR", "AIR", parts[0], "AIR"}; // older statues: main hand only
        }
        for (int i = 0; i < Math.min(parts.length, SLOTS.length); i++) {
            ItemStack item = gearItem(parts[i]);
            if (item == null) {
                continue;
            }
            try {
                eq.setItem(SLOTS[i], item);
                eq.setDropChance(SLOTS[i], 0f);
            } catch (IllegalArgumentException ignored) {
                // slot not supported by this mob
            }
        }
    }

    private ItemStack statueItem(String typeToken, double scale, String gear, String nameMini, List<Component> lore) {
        Material egg = Material.matchMaterial(typeToken.split(":")[0] + "_SPAWN_EGG");
        ItemStack item = ItemStack.of(egg == null ? Material.ZOMBIE_SPAWN_EGG : egg);
        item.setData(DataComponentTypes.ITEM_NAME, Text.MM.deserialize(nameMini));
        item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        item.setData(DataComponentTypes.MAX_STACK_SIZE, 1);
        item.lore(lore);
        String data = typeToken + ";" + scale + ";" + gear + ";" + nameMini;
        item.editPersistentDataContainer(pdc -> {
            pdc.set(Keys.ITEM_KIND, PersistentDataType.STRING, ItemService.Kind.STATUE.name());
            pdc.set(Keys.STATUE, PersistentDataType.STRING, data);
        });
        return item;
    }

    /** Places the statue mob. Returns false if the item is broken or the spot is blocked. */
    public boolean placeStatue(Player player, ItemStack item, Location at) {
        String data = item.getPersistentDataContainer().get(Keys.STATUE, PersistentDataType.STRING);
        if (data == null) {
            return false;
        }
        String[] parts = data.split(";", 4);
        if (parts.length < 4) {
            return false;
        }
        String[] token = parts[0].split(":");
        boolean baby = token.length > 1 && token[1].equals("baby");
        EntityType type;
        try {
            type = EntityType.valueOf(token[0]);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        if (type.getEntityClass() == null || !LivingEntity.class.isAssignableFrom(type.getEntityClass())) {
            return false;
        }
        boolean trophy = ItemService.Kind.TROPHY.name().equals(
            item.getPersistentDataContainer().get(Keys.ITEM_KIND, PersistentDataType.STRING));
        // Trophies always use the configured trophy size (older ones stored a bigger scale).
        double scale = trophy ? plugin.settings().features.trophyScale : parseDouble(parts[1], 1.0);
        String gear = parts[2];
        Component name = Text.MM.deserialize(parts[3]);
        List<Component> lore = item.lore() == null ? List.of() : item.lore();
        float yaw = player.getLocation().getYaw() + 180f;
        at.setYaw(yaw);
        at.setPitch(0f);
        List<String> loreMini = new ArrayList<>();
        for (Component line : lore) {
            loreMini.add(Text.MM.serialize(line));
        }
        if (at.getWorld().getDifficulty() == org.bukkit.Difficulty.PEACEFUL
            && org.bukkit.entity.Enemy.class.isAssignableFrom(type.getEntityClass())) {
            player.sendMessage(Text.mm("<red>Hostile statues can't stand in Peaceful difficulty.</red>"));
            return false;
        }
        boolean mini = trophy;
        String original = Base64.getEncoder().encodeToString(item.asOne().serializeAsBytes());
        Entity placed = at.getWorld().spawn(at, type.getEntityClass(), false, ent -> {
            if (!(ent instanceof LivingEntity statue)) {
                return;
            }
            statue.setAI(false);
            statue.setSilent(true);
            statue.setInvulnerable(true);
            statue.setGravity(false);
            statue.setPersistent(true);
            statue.setRemoveWhenFarAway(false);
            statue.setCollidable(false);
            statue.setCanPickupItems(false);
            statue.customName(name);
            statue.setCustomNameVisible(!mini); // trophies show their name only when you look at them
            AttributeInstance s = statue.getAttribute(Attribute.SCALE);
            if (s != null) {
                s.setBaseValue(scale);
            }
            applyGear(statue.getEquipment(), gear);
            if (statue instanceof org.bukkit.entity.Ageable ageable) {
                if (baby) {
                    ageable.setBaby();
                } else {
                    ageable.setAdult();
                }
            }
            if (statue instanceof Zombie z) {
                z.setShouldBurnInDay(false);
            }
            if (statue instanceof AbstractSkeleton sk) {
                sk.setShouldBurnInDay(false);
            }
            if (statue instanceof Phantom ph) {
                ph.setShouldBurnInDay(false);
            }
            if (statue instanceof PiglinAbstract pa) {
                pa.setImmuneToZombification(true);
            }
            if (statue instanceof org.bukkit.entity.Hoglin hoglin) {
                hoglin.setImmuneToZombification(true);
            }
            statue.getPersistentDataContainer().set(Keys.STATUE, PersistentDataType.STRING, data);
            statue.getPersistentDataContainer().set(Keys.STATUE_LORE, PersistentDataType.LIST.strings(), loreMini);
            statue.getPersistentDataContainer().set(Keys.STATUE_ITEM, PersistentDataType.STRING, original);
        });
        if (!placed.isValid()) {
            return false; // something (e.g. a protection plugin) stopped it
        }
        // Apply the size once more after the mob is fully in the world, in case anything reset it on spawn.
        Bukkit.getScheduler().runTask(plugin, () -> resize(placed));
        Fx.particle(at.clone().add(0, 1, 0), Particle.CLOUD, 15, 0.4, 0.02);
        Fx.play(at, "block.stone.place", 1.0f, 0.8f);
        return true;
    }

    /** Placed trophies always use the current trophy size, including ones placed before it changed. */
    public void resize(Entity e) {
        if (!(e instanceof LivingEntity living) || !e.isValid()) {
            return;
        }
        ItemStack original = storedItem(e);
        if (original == null || !ItemService.Kind.TROPHY.name().equals(
            original.getPersistentDataContainer().get(Keys.ITEM_KIND, PersistentDataType.STRING))) {
            return;
        }
        AttributeInstance scale = living.getAttribute(Attribute.SCALE);
        double wanted = plugin.settings().features.trophyScale;
        if (scale != null && Math.abs(scale.getBaseValue() - wanted) > 1.0E-4) {
            scale.setBaseValue(wanted);
        }
    }

    /** Mini trophies are too small to click reliably, so they can also be picked up via the block below them. */
    public @Nullable Entity trophyOn(org.bukkit.block.Block block) {
        Location top = block.getLocation().add(0.5, 1.0, 0.5);
        for (Entity e : block.getWorld().getNearbyEntities(top, 0.6, 0.6, 0.6)) {
            ItemStack original = storedItem(e);
            if (original != null && ItemService.Kind.TROPHY.name().equals(
                original.getPersistentDataContainer().get(Keys.ITEM_KIND, PersistentDataType.STRING))) {
                return e;
            }
        }
        return null;
    }

    private static @Nullable ItemStack storedItem(Entity e) {
        String raw = e.getPersistentDataContainer().get(Keys.STATUE_ITEM, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(raw));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    public static boolean isStatue(Entity entity) {
        return entity.getPersistentDataContainer().has(Keys.STATUE, PersistentDataType.STRING);
    }

    /** Turns a placed statue back into its item. */
    public @Nullable ItemStack pickUp(Entity statue) {
        String data = statue.getPersistentDataContainer().get(Keys.STATUE, PersistentDataType.STRING);
        if (data == null) {
            return null;
        }
        String original = statue.getPersistentDataContainer().get(Keys.STATUE_ITEM, PersistentDataType.STRING);
        if (original != null) {
            try {
                ItemStack item = ItemStack.deserializeBytes(Base64.getDecoder().decode(original));
                Fx.particle(Fx.center(statue), Particle.CLOUD, 15, 0.4, 0.02);
                statue.remove();
                return item;
            } catch (RuntimeException ignored) {
                // fall back to rebuilding it below
            }
        }
        String[] parts = data.split(";", 4);
        if (parts.length < 4) {
            return null;
        }
        List<String> loreMini = statue.getPersistentDataContainer().get(Keys.STATUE_LORE, PersistentDataType.LIST.strings());
        List<Component> lore = new ArrayList<>();
        if (loreMini != null) {
            for (String line : loreMini) {
                lore.add(Text.noItalic(Text.MM.deserialize(line)));
            }
        }
        try {
            EntityType.valueOf(parts[0].split(":")[0]);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        ItemStack item = statueItem(parts[0], parseDouble(parts[1], 1.0), parts[2], parts[3], lore);
        Fx.particle(Fx.center(statue), Particle.CLOUD, 15, 0.4, 0.02);
        statue.remove();
        return item;
    }

    private static double parseDouble(String s, double def) {
        try {
            return Double.parseDouble(s.trim().toLowerCase(Locale.ROOT));
        } catch (NumberFormatException ex) {
            return def;
        }
    }
}
