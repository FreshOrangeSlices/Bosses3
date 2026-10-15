package com.questbook;

import com.questbook.QuestConfig.Board;
import com.questbook.QuestConfig.Guide;
import com.questbook.QuestConfig.Quest;
import com.questbook.QuestConfig.Reward;
import com.questbook.QuestConfig.Skill;
import com.questbook.QuestService.Status;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Quest Book's screens: the hub (boards, guide books, the welcome gift), a quest board (each quest's icon with its
 * state below it: gray dye, emerald to claim, lime dye when done), and a skill milestone board.
 */
public final class Menus implements Listener {

    private static final int BACK_SLOT = 49;
    private static final int HEADER_SLOT = 4;

    /** Which screen is open, and what each slot does. */
    static final class Screen implements InventoryHolder {
        final @Nullable Board board;
        final Map<Integer, Runnable> actions = new HashMap<>();
        Inventory inventory;

        Screen(@Nullable Board board) {
            this.board = board;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    private final QuestBook plugin;

    Menus(QuestBook plugin) {
        this.plugin = plugin;
    }

    private QuestService service() {
        return plugin.service();
    }

    // =====================================================================
    //  Hub
    // =====================================================================

    public void openHub(Player p) {
        Screen screen = new Screen(null);
        screen.inventory = Bukkit.createInventory(screen, 45, Component.text(plugin.quests().bookTitle));
        service().refresh(p);
        fill(screen.inventory);
        QuestConfig cfg = plugin.quests();
        PlayerStore.State state = plugin.store().get(p.getUniqueId());
        if (!state.welcomeClaimed && !cfg.welcome().isEmpty()) {
            List<Component> lore = new ArrayList<>();
            lore.add(line("A little something to start you off.", NamedTextColor.GRAY));
            lore.add(Component.empty());
            rewardLines(lore, cfg.welcome());
            lore.add(Component.empty());
            lore.add(line("Click to claim!", NamedTextColor.GREEN));
            put(screen, cfg.welcomeSlot, icon(Material.CHEST, Component.text(cfg.welcomeName, Msg.GOLD), lore, true),
                () -> {
                    if (service().claimWelcome(p)) {
                        openHub(p);
                    }
                });
        } else {
            put(screen, cfg.welcomeSlot, icon(Material.WRITTEN_BOOK, Component.text(cfg.bookTitle, Msg.GOLD), List.of(
                line("Everything this server adds, a", NamedTextColor.GRAY),
                line("little at a time. Do a quest, then", NamedTextColor.GRAY),
                line("click its emerald for the reward.", NamedTextColor.GRAY)), false), null);
        }
        for (Board b : cfg.boards()) {
            if (b.hubSlot() < 0 || b.hubSlot() >= 45 || !service().visible(b)) {
                continue;
            }
            int[] pr = service().progress(p, b);
            List<Component> lore = new ArrayList<>();
            for (String d : b.description()) {
                lore.add(Msg.mini(d).colorIfAbsent(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.empty());
            lore.add(line((b.skillBoard() ? "Milestones: " : "Done: ") + pr[0] + "/" + pr[1],
                pr[0] >= pr[1] && pr[1] > 0 ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
            if (pr[2] > 0) {
                lore.add(line(pr[2] + (pr[2] == 1 ? " reward" : " rewards") + " to claim!", NamedTextColor.GREEN));
            }
            put(screen, b.hubSlot(), icon(b.icon(), Msg.mini(b.title()).colorIfAbsent(Msg.GOLD), lore, pr[2] > 0),
                () -> openBoard(p, b));
        }
        // the guide books, side by side along the bottom
        List<Guide> books = new ArrayList<>();
        for (Guide g : cfg.guides()) {
            if (service().available(g.requires())) {
                books.add(g);
            }
        }
        List<Integer> bottom = row(36, books.size(), true);
        for (int i = 0; i < books.size() && i < bottom.size(); i++) {
            Guide g = books.get(i);
            List<Component> lore = new ArrayList<>();
            for (String l : g.lore()) {
                lore.add(Msg.mini(l).colorIfAbsent(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.empty());
            lore.add(line("Click to read", NamedTextColor.DARK_GRAY));
            put(screen, bottom.get(i), icon(Material.WRITTEN_BOOK, Msg.mini(g.name()).colorIfAbsent(NamedTextColor.AQUA), lore,
                false), () -> Bukkit.getScheduler().runTask(plugin, () -> {
                    p.closeInventory();
                    p.performCommand(g.command()); // opens the book on screen; nothing goes in the bag
                }));
        }
        show(p, screen);
    }

    // =====================================================================
    //  Quest board
    // =====================================================================

    public void openBoard(Player p, Board b) {
        if (!service().visible(b)) {
            openHub(p);
            return;
        }
        if (b.skillBoard()) {
            openSkills(p, b);
            return;
        }
        Screen screen = new Screen(b);
        screen.inventory = Bukkit.createInventory(screen, 54, Msg.mini(b.title()));
        fill(screen.inventory);
        int[] pr = service().progress(p, b);
        put(screen, HEADER_SLOT, icon(b.icon(), Msg.mini(b.title()).colorIfAbsent(Msg.GOLD), List.of(
            line("Done: " + pr[0] + "/" + pr[1], NamedTextColor.YELLOW)), false), null);
        List<Quest> shown = new ArrayList<>();
        List<Status> states = new ArrayList<>();
        for (Quest q : b.quests()) {
            Status st = service().status(p, q);
            if (st != Status.HIDDEN) {
                shown.add(q);
                states.add(st);
            }
        }
        List<Integer> slots = grid(shown.size());
        for (int i = 0; i < shown.size() && i < slots.size(); i++) {
            Quest q = shown.get(i);
            Status st = states.get(i);
            Runnable claim = st == Status.READY ? () -> {
                if (service().claim(p, q)) {
                    openBoard(p, b);
                }
            } : null;
            put(screen, slots.get(i), questIcon(q, st), claim);
        }
        put(screen, BACK_SLOT, icon(Material.ARROW, Component.text("Back", NamedTextColor.GRAY), List.of(), false),
            () -> openHub(p));
        show(p, screen);
    }

    /**
     * One item per quest: what to do and the reward; "click to claim" once done; and once claimed it shines, with
     * its Insight.
     */
    private ItemStack questIcon(Quest q, Status st) {
        List<Component> lore = new ArrayList<>();
        lore.add(Msg.mini(q.objective()).colorIfAbsent(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        if (st == Status.CLAIMED) {
            if (!q.insight().isBlank()) {
                lore.add(Component.empty());
                for (String part : wrap(q.insight(), 32)) {
                    lore.add(line(part, NamedTextColor.AQUA));
                }
            }
            return icon(q.icon(), Component.text("✔ ", NamedTextColor.GREEN)
                .append(Msg.mini(q.name()).colorIfAbsent(NamedTextColor.GREEN)), lore, true);
        }
        if (!q.rewards().isEmpty()) {
            lore.add(Component.empty());
            rewardLines(lore, q.rewards());
        }
        if (st == Status.READY) {
            lore.add(Component.empty());
            lore.add(line("Done! Click to claim.", NamedTextColor.GREEN));
            return icon(q.icon(), Msg.mini(q.name()).colorIfAbsent(Msg.GOLD), lore, false);
        }
        return icon(q.icon(), Msg.mini(q.name()).colorIfAbsent(NamedTextColor.WHITE), lore, false);
    }

    /** Slots for {@code n} quests: tidy centred rows of up to 7 between the header and the Back button. */
    private static List<Integer> grid(int n) {
        List<Integer> out = new ArrayList<>();
        int rows = Math.max(1, (n + 6) / 7);
        int[] rowSlots = rows == 1 ? new int[]{2} : rows == 2 ? new int[]{1, 3} : rows == 3 ? new int[]{1, 2, 3}
            : new int[]{1, 2, 3, 4};
        int left = n;
        for (int r = 0; r < rowSlots.length && left > 0; r++) {
            int count = (int) Math.ceil(left / (double) (rowSlots.length - r));
            out.addAll(row(rowSlots[r] * 9, Math.min(7, count), false));
            left -= Math.min(7, count);
        }
        return out;
    }

    /**
     * {@code count} slots centred in the row starting at {@code start}: odd counts side by side, even counts with
     * the middle slot left free so the row stays symmetrical. {@code spaced}: one slot apart.
     */
    private static List<Integer> row(int start, int count, boolean spaced) {
        List<Integer> out = new ArrayList<>();
        if (count <= 0) {
            return out;
        }
        if (spaced && count <= 5) {
            int first = 4 - (count - 1);
            for (int i = 0; i < count; i++) {
                out.add(start + first + i * 2);
            }
            return out;
        }
        if (count % 2 == 1) {
            int first = 4 - count / 2;
            for (int i = 0; i < count; i++) {
                out.add(start + first + i);
            }
        } else {
            int half = count / 2;
            for (int i = 0; i < half; i++) {
                out.add(start + 4 - half + i);
            }
            for (int i = 0; i < half; i++) {
                out.add(start + 5 + i);
            }
        }
        return out;
    }

    private void rewardLines(List<Component> lore, List<Reward> rewards) {
        lore.add(line("Reward:", Msg.GOLD));
        for (Reward r : rewards) {
            if (!service().available(r.requires())) {
                continue;
            }
            if (r.item() != null) {
                lore.add(Component.text(" • " + r.item().getAmount() + " × ", NamedTextColor.WHITE)
                    .append(r.item().effectiveName().colorIfAbsent(NamedTextColor.WHITE))
                    .decoration(TextDecoration.ITALIC, false));
            } else if (!r.label().isBlank()) {
                lore.add(Component.text(" • ", NamedTextColor.WHITE).append(Msg.mini(r.label()).colorIfAbsent(
                    NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false));
            }
        }
    }

    // =====================================================================
    //  Skill milestones
    // =====================================================================

    private void openSkills(Player p, Board b) {
        Screen screen = new Screen(b);
        screen.inventory = Bukkit.createInventory(screen, 54, Msg.mini(b.title()));
        fill(screen.inventory);
        List<Integer> levels = b.levels();
        int row = 0;
        for (Skill skill : b.skills()) {
            if (row >= 5) {
                break;
            }
            int base = row * 9;
            int level = plugin.mcmmo().level(p, skill.id());
            put(screen, base + 1, icon(skill.icon(), Msg.mini(skill.name()).colorIfAbsent(Msg.GOLD), List.of(
                line("Your level: " + (level < 0 ? "?" : level), NamedTextColor.YELLOW)), false), null);
            boolean prevReached = true;
            for (int i = 0; i < levels.size() && i < 3; i++) {
                int need = levels.get(i);
                boolean reached = service().milestoneReached(p, skill, need);
                int shown = plugin.mcmmo().scaled(need);
                List<Component> lore = new ArrayList<>();
                String text = i < skill.milestones().size() ? skill.milestones().get(i) : "";
                for (String part : wrap(text, 32)) {
                    lore.add(line(part, reached ? NamedTextColor.WHITE : NamedTextColor.GRAY));
                }
                lore.add(Component.empty());
                if (reached && i == levels.size() - 1) {
                    lore.add(line("Complete!", NamedTextColor.GREEN));
                } else if (reached) {
                    lore.add(line("Next milestone: level " + plugin.mcmmo().scaled(levels.get(i + 1)), NamedTextColor.YELLOW));
                } else {
                    lore.add(line("Reach level " + shown + " in " + plainText(skill.name()) + ".", NamedTextColor.DARK_GRAY));
                }
                Material dye = reached ? Material.LIME_DYE : Material.GRAY_DYE;
                put(screen, base + 3 + i * 2, icon(dye, Component.text("Level " + shown,
                    reached ? NamedTextColor.GREEN : NamedTextColor.GRAY), lore, false), null);
                // the path between milestones fills in as you go
                if (i > 0) {
                    put(screen, base + 2 + i * 2, pane(prevReached && reached ? Material.LIME_STAINED_GLASS_PANE
                        : Material.GRAY_STAINED_GLASS_PANE), null);
                }
                prevReached = reached;
            }
            row++;
        }
        put(screen, BACK_SLOT, icon(Material.ARROW, Component.text("Back", NamedTextColor.GRAY), List.of(), false),
            () -> openHub(p));
        show(p, screen);
    }

    private static String plainText(String mini) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(Msg.mini(mini));
    }

    // =====================================================================
    //  Helpers
    // =====================================================================

    private static void put(Screen screen, int slot, ItemStack item, @Nullable Runnable action) {
        if (slot < 0 || slot >= screen.inventory.getSize()) {
            return;
        }
        screen.inventory.setItem(slot, item);
        if (action != null) {
            screen.actions.put(slot, action);
        } else {
            screen.actions.remove(slot);
        }
    }

    private void show(Player p, Screen screen) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) {
                p.openInventory(screen.inventory);
            }
        });
    }

    private static void fill(Inventory inv) {
        ItemStack pane = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, pane);
        }
    }

    private static ItemStack pane(Material m) {
        ItemStack item = ItemStack.of(m);
        item.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().hideTooltip(true).build());
        return item;
    }

    private static ItemStack icon(Material m, Component name, List<Component> lore, boolean glint) {
        ItemStack item = ItemStack.of(m);
        item.setData(DataComponentTypes.ITEM_NAME, name.decoration(TextDecoration.ITALIC, false));
        item.lore(lore);
        if (glint) {
            item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        // a sword's damage, a potion's effects...: only the name and our lines show
        item.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay()
            .addHiddenComponents(DataComponentTypes.ATTRIBUTE_MODIFIERS, DataComponentTypes.POTION_CONTENTS,
                DataComponentTypes.WRITTEN_BOOK_CONTENT, DataComponentTypes.ENCHANTMENTS, DataComponentTypes.TRIM,
                DataComponentTypes.JUKEBOX_PLAYABLE).build());
        return item;
    }

    private static Component line(String text, TextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    /** Splits text into lines of at most {@code width} characters (for lore). */
    static List<String> wrap(String text, int width) {
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
            .serialize(Msg.mini(text));
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : plain.split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (line.length() > 0 && line.length() + 1 + word.length() > width) {
                out.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0) {
            out.add(line.toString());
        }
        return out;
    }

    /** On shutdown and reload: our screens close (their contents are pictures, not items). */
    public void closeAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder(false) instanceof Screen) {
                p.closeInventory();
            }
        }
    }

    // =====================================================================

    private final Map<java.util.UUID, Long> lastClick = new HashMap<>();

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Screen screen)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p) || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastClick.getOrDefault(p.getUniqueId(), 0L) < 250) {
            return; // a double click is one click
        }
        Runnable action = screen.actions.get(event.getRawSlot());
        if (action != null) {
            lastClick.put(p.getUniqueId(), now);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.4f);
            action.run();
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Screen) {
            event.setCancelled(true);
        }
    }

    void forget(java.util.UUID player) {
        lastClick.remove(player);
    }
}
