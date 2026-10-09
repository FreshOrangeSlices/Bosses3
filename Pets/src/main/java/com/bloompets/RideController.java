package com.bloompets;

import org.bukkit.Bukkit;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Riding pets. The pet doesn't need a saddle and nobody else steers it: every tick it moves the way its rider is
 * pressing (WASD, jump, sprint), at its category's speed.
 *
 * <ul>
 *     <li>Small pets: the pet either grows big enough to carry you, or you shrink to its size (picked once per pet).</li>
 *     <li>Bees and Allays hover: hold jump to rise (a few blocks at most), let go to drift down.</li>
 * </ul>
 */
public final class RideController implements Listener {

    private static final class Ride {
        final PetManager.Active active;
        final UUID rider;
        @Nullable Location last;
        double travelled;

        Ride(PetManager.Active active, UUID rider) {
            this.active = active;
            this.rider = rider;
        }
    }

    private final BloomPets plugin;
    private final Map<UUID, Ride> rides = new HashMap<>();
    private boolean mounting;

    public RideController(BloomPets plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    /** True while we are seating the owner, so the mount isn't blocked as a stranger climbing on. */
    public boolean mounting() {
        return mounting;
    }

    public boolean isRidden(PetManager.Active a) {
        Ride r = rides.get(a.owner);
        return r != null && r.active == a;
    }

    public void tryMount(Player p, PetManager.Active a) {
        Pet pet = a.pet;
        Settings s = plugin.settings();
        if (!pet.species.rideable()) {
            Msg.error(p, pet.species.plural() + " can't be ridden.");
            return;
        }
        if (pet.level < s.rideUnlockLevel) {
            Msg.error(p, pet.name + " can be ridden from level " + s.rideUnlockLevel + " (now level " + pet.level + ").");
            return;
        }
        if (p.isInsideVehicle() || !a.entity.getPassengers().isEmpty() || plugin.pets().fainting(a)) {
            return;
        }
        Mob m = a.entity;
        double scale = pet.species.rideScale();
        if (pet.rideStyle == Pet.RideStyle.GROW && scale > 0) {
            setScale(m, scale);
        } else if (pet.rideStyle == Pet.RideStyle.SHRINK && scale > 0) {
            setScale(p, Math.max(s.minRiderScale, 1 / scale));
        }
        boolean seated;
        mounting = true;
        try {
            seated = m.addPassenger(p);
        } finally {
            mounting = false;
        }
        if (!seated) {
            setScale(m, 1);
            setScale(p, 1);
            return;
        }
        Ride ride = new Ride(a, p.getUniqueId());
        ride.last = m.getLocation();
        rides.put(p.getUniqueId(), ride);
        a.target = null;
        Msg.bar(p, Component.text("Riding " + pet.name + "  ", pet.species.category().color())
            .append(Component.text(pet.species.hovers() ? "hold jump to rise · sneak to get off" : "sneak to get off",
                NamedTextColor.GRAY)));
    }

    /** Takes the rider off this pet (it's being put away) and puts everyone back to normal size. */
    public void eject(PetManager.Active a) {
        Ride r = rides.get(a.owner);
        if (r != null && r.active == a) {
            end(r, true);
        }
    }

    private void end(Ride r, boolean dismount) {
        rides.remove(r.rider);
        Mob m = r.active.entity;
        Player p = Bukkit.getPlayer(r.rider);
        if (p != null) {
            setScale(p, 1);
            if (dismount && p.getVehicle() == m) {
                m.removePassenger(p);
            }
            p.setFallDistance(0);
        }
        if (m.isValid()) {
            setScale(m, 1);
            m.setVelocity(new Vector(0, m.getVelocity().getY(), 0));
        }
    }

    private void tick() {
        if (rides.isEmpty()) {
            return;
        }
        for (Ride r : List.copyOf(rides.values())) {
            Player p = Bukkit.getPlayer(r.rider);
            Mob m = r.active.entity;
            if (p == null || !m.isValid() || p.getVehicle() != m) {
                end(r, true);
                continue;
            }
            steer(p, r);
        }
    }

    private void steer(Player p, Ride r) {
        Mob m = r.active.entity;
        Pet pet = r.active.pet;
        Species sp = pet.species;
        Category cat = sp.category();
        Input in = p.getCurrentInput();

        double forward = (in.isForward() ? 1 : 0) - (in.isBackward() ? 0.5 : 0);
        double strafe = (in.isRight() ? 1 : 0) - (in.isLeft() ? 1 : 0);
        double yaw = Math.toRadians(p.getLocation().getYaw());
        // forward is (-sin, cos); right is (-cos, -sin)
        double mx = -Math.sin(yaw) * forward - Math.cos(yaw) * strafe;
        double mz = Math.cos(yaw) * forward - Math.sin(yaw) * strafe;
        double len = Math.sqrt(mx * mx + mz * mz);

        double speed = cat.rideSpeed() * (1 + 0.02 * (pet.level - 1));
        if (in.isSprint() && forward > 0) {
            speed *= cat == Category.SPEEDSTER ? 1.25 : 1.15;
        }
        boolean water = m.isInWater() && sp != Species.TURTLE && sp != Species.FROG;
        if (water) {
            speed *= 0.6;
        }

        Vector v = m.getVelocity();
        double vx;
        double vz;
        if (len > 0.01) {
            double scale = Math.min(1, len) / len * speed;
            vx = mx * scale;
            vz = mz * scale;
        } else {
            vx = v.getX() * 0.5;
            vz = v.getZ() * 0.5;
        }

        double vy = v.getY();
        if (sp.hovers()) {
            double hover = plugin.settings().hoverHeight;
            double height = m.getY() - groundBelow(m, hover + 3);
            if (in.isJump()) {
                vy = height < hover ? 0.2 : Math.max(-0.12, Math.min(0, hover - height)); // hold at the ceiling
            } else {
                vy = m.isOnGround() ? 0 : -0.12; // drift down gently
            }
        } else if (m.isInWater() || (sp == Species.STRIDER && m.isInLava())) {
            vy = in.isJump() ? 0.14 : (water ? 0.04 : vy);
        } else if (in.isJump() && m.isOnGround()) {
            vy = cat.jumpVelocity() * (1 + 0.01 * (pet.level - 1));
        }

        m.setVelocity(new Vector(vx, vy, vz));
        float facing = len > 0.01 ? PetManager.yaw(mx, mz) : p.getLocation().getYaw();
        PetManager.face(m, facing);

        // bond XP for distance travelled
        Location now = m.getLocation();
        if (r.last != null && r.last.getWorld() == now.getWorld()) {
            double dx = now.getX() - r.last.getX();
            double dz = now.getZ() - r.last.getZ();
            r.travelled += Math.sqrt(dx * dx + dz * dz);
            int per = plugin.settings().rideBlocksPerXp;
            if (r.travelled >= per) {
                int xp = (int) (r.travelled / per);
                r.travelled -= xp * per;
                plugin.pets().addXp(pet, xp);
            }
        }
        r.last = now;
    }

    /** The y of the ground under the pet, or far below if there's none within {@code range} blocks. */
    private static double groundBelow(LivingEntity e, double range) {
        Block b = e.getLocation().getBlock();
        for (int i = 0; i <= range; i++) {
            Block below = b.getRelative(0, -i, 0);
            if (!below.isPassable() || below.isLiquid()) {
                double top = below.getBoundingBox().getMaxY();
                return below.isLiquid() || top <= below.getY() ? below.getY() + 1 : top;
            }
        }
        return e.getY() - range - 10;
    }

    static void setScale(LivingEntity e, double factor) {
        PetManager.setModifier(e, Attribute.SCALE, Keys.MOD_RIDE_SCALE, factor - 1,
            AttributeModifier.Operation.MULTIPLY_SCALAR_1);
    }

    // =====================================================================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player p)) {
            return;
        }
        Ride r = rides.get(p.getUniqueId());
        if (r != null && event.getDismounted() == r.active.entity) {
            end(r, false);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onQuit(PlayerQuitEvent event) {
        Ride r = rides.get(event.getPlayer().getUniqueId());
        if (r != null) {
            end(r, true);
        }
    }

    /** Leftover size changes (a crash mid-ride) don't survive: transient modifiers are never saved. */
    public void shutdown() {
        for (Ride r : List.copyOf(rides.values())) {
            end(r, true);
        }
    }
}
