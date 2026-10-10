package com.bloompets;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.WrittenBookContent;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.object.ObjectContents;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The pet guide: a written book with little pictures (item sprites drawn right in the text). How to make a friend is
 * spelled out plainly; what each pet does is only hinted at, so there's something to discover.
 */
public final class Guide {

    // dark, warm colours that read well on the book's paper
    private static final TextColor PINK = TextColor.color(0xC2185B);
    private static final TextColor INK = TextColor.color(0x3E2723);
    private static final TextColor SOFT = TextColor.color(0x795548);

    private Guide() {
    }

    public static ItemStack book() {
        WrittenBookContent.Builder content = WrittenBookContent.writtenBookContent("Pet Guide", "BloomPets");
        for (Component page : pages()) {
            content.addPage(page);
        }
        ItemStack book = ItemStack.of(Material.WRITTEN_BOOK);
        book.setData(DataComponentTypes.WRITTEN_BOOK_CONTENT, content.build());
        return book;
    }

    private static List<Component> pages() {
        List<Component> pages = new ArrayList<>();
        pages.add(page()
            .append(nl())
            .append(Component.text("  ✿ BloomPets ✿", PINK).decorate(TextDecoration.BOLD)).append(nl()).append(nl())
            .append(ink("A little guide to\nmaking animal\nfriends!")).append(nl()).append(nl())
            .append(Component.text("  ")).append(egg(Species.RABBIT)).append(Component.text(" "))
            .append(egg(Species.FOX)).append(Component.text(" ")).append(egg(Species.CAT))
            .append(Component.text(" ")).append(egg(Species.ALLAY)).append(nl()).append(nl())
            .append(ink("Every animal has a\nfavourite snack.\nShare it, and they\njust might stay. "))
            .append(Component.text("♥", PINK))
            .build());
        // a page shows 14 short lines; every page below stays within that
        pages.add(page()
            .append(title("Make a Friend ♥")).append(nl()).append(nl())
            .append(ink("1. Find an animal\nin this book.\n"))
            .append(ink("2. Sneak and feed\nit its favourite\nsnack.\n"))
            .append(ink("3. Repeat until the\nhearts fill up!")).append(nl()).append(nl())
            .append(soft("Then it's yours,\nforever and ever."))
            .build());
        pages.add(page()
            .append(item("popped_chorus_fruit")).append(Component.text(" ")).append(title("Your Pet Toy"))
            .append(nl()).append(nl())
            .append(ink("Right-click: call\nyour pet, or send\nit home for a nap.")).append(nl()).append(nl())
            .append(ink("Punch: see all\nyour pets and pick\none.")).append(nl()).append(nl())
            .append(ink("Use it on your pet:\nhop on for a ride!"))
            .build());
        pages.add(page()
            .append(title("More Tricks ✦")).append(nl()).append(nl())
            .append(ink("Click your pet to\nswap to another.")).append(nl()).append(nl())
            .append(ink("Sneak + click it to\npeek in its bag.")).append(nl()).append(nl())
            .append(ink("Feed it its snack\nto heal it up.")).append(nl()).append(nl())
            .append(ink("Each has its own\n")).append(block("poppy")).append(ink(" Bloom, too!"))
            .build());
        pages.add(page()
            .append(title("Growing Up ✦")).append(nl()).append(nl())
            .append(ink("Walks, snacks and\nadventures help\nyour pet grow up to\nlevel 10.")).append(nl()).append(nl())
            .append(ink("Bigger bags, more\nheart, and rides!")).append(nl()).append(nl())
            .append(soft("Pets never die. A\ntired pet just goes\nhome for a nap."))
            .build());

        BloomPets plugin = BloomPets.get();
        for (Category category : Category.values()) {
            List<Species> roster = new ArrayList<>();
            for (Species s : Species.values()) {
                if (s.category() == category && (plugin == null || plugin.settings().enabled.contains(s))) {
                    roster.add(s);
                }
            }
            for (int i = 0; i < roster.size(); i += 2) { // two pets a page, so nothing runs off the bottom
                TextComponent.Builder page = page()
                    .append(Component.text(heading(category), color(category)).decorate(TextDecoration.BOLD))
                    .append(nl());
                for (Species s : roster.subList(i, Math.min(i + 2, roster.size()))) {
                    page.append(nl())
                        .append(egg(s)).append(Component.text(" " + s.displayName(), color(s.category()))
                            .decorate(TextDecoration.BOLD)).append(nl())
                        .append(Component.text("♥ ", PINK)).append(food(s)).append(ink(" " + foodName(s))).append(nl())
                        .append(soft(hint(s))).append(nl());
                }
                pages.add(page.build());
            }
        }
        return pages;
    }

    // =====================================================================

    private static String heading(Category c) {
        return switch (c) {
            case COMBAT -> "⚔ Brave Buddies";
            case PACK -> "✉ Pack Pals";
            case SPEEDSTER -> "➹ Speedy Pals";
            case UTILITY -> "✿ Helpful Pals";
        };
    }

    private static TextColor color(Category c) {
        return switch (c) {
            case COMBAT -> TextColor.color(0xB71C1C);
            case PACK -> TextColor.color(0xBF5B00);
            case SPEEDSTER -> TextColor.color(0x00695C);
            case UTILITY -> TextColor.color(0x2E7D32);
        };
    }

    /** What it eats, in plain words. */
    private static String foodName(Species s) {
        return switch (s) {
            case WOLF -> "Any meat";
            case POLAR_BEAR -> "Salmon";
            case IRON_GOLEM -> "Iron Ingots";
            case GOAT, COW -> "Wheat";
            case PANDA -> "Bamboo";
            case VEX -> "Emeralds";
            case DONKEY -> "Golden Carrots";
            case MULE -> "Apples";
            case LLAMA -> "Hay Bales";
            case CAMEL -> "Cactus";
            case SNIFFER -> "Torchflower Seeds";
            case HORSE -> "Sugar";
            case FOX -> "Sweet Berries";
            case RABBIT -> "Carrots";
            case OCELOT -> "Tropical Fish";
            case STRIDER -> "Warped Fungus";
            case CAT -> "Cod";
            case BEE -> "Any flower";
            case ALLAY -> "Amethyst Shards";
            case CHICKEN -> "Seeds";
            case TURTLE -> "Seagrass";
            case ARMADILLO -> "Spider Eyes";
            case FROG -> "Slime Balls";
        };
    }

    private static Component food(Species s) {
        return switch (s) {
            case WOLF -> item("beef");
            case POLAR_BEAR -> item("salmon");
            case IRON_GOLEM -> item("iron_ingot");
            case GOAT, COW -> item("wheat");
            case PANDA -> item("bamboo");
            case VEX -> item("emerald");
            case DONKEY -> item("golden_carrot");
            case MULE -> item("apple");
            case LLAMA -> block("hay_block_side");
            case CAMEL -> block("cactus_side");
            case SNIFFER -> item("torchflower_seeds");
            case HORSE -> item("sugar");
            case FOX -> item("sweet_berries");
            case RABBIT -> item("carrot");
            case OCELOT -> item("tropical_fish");
            case STRIDER -> block("warped_fungus");
            case CAT -> item("cod");
            case BEE -> block("poppy");
            case ALLAY -> item("amethyst_shard");
            case CHICKEN -> item("wheat_seeds");
            case TURTLE -> block("seagrass");
            case ARMADILLO -> item("spider_eye");
            case FROG -> item("slime_ball");
        };
    }

    /** A hint at what the pet does, never the numbers. */
    private static String hint(Species s) {
        return switch (s) {
            case WOLF -> "Brave, loyal, and\nready to ride!";
            case POLAR_BEAR -> "Never minds the cold.";
            case IRON_GOLEM -> "Sturdy as a castle.";
            case GOAT -> "Always lands on its\nfeet.";
            case PANDA -> "Loves a lazy break.";
            case VEX -> "Likes shiny things.\nDresses for battle!";
            case DONKEY -> "A big, big heart.";
            case MULE -> "Hard to push around.";
            case LLAMA -> "Has opinions about\nbullies.";
            case CAMEL -> "Loves a sandy\nstroll.";
            case SNIFFER -> "Has a nose for\ntreasure!";
            case HORSE -> "Puts a spring in\nyour step.";
            case FOX -> "Sneaky, sneaky...";
            case RABBIT -> "Boing! Hop on!";
            case OCELOT -> "Scary things keep\ntheir distance.";
            case STRIDER -> "Toasty toes in the\nNether.";
            case CAT -> "Keeps watch, brings\ngifts, rides along!";
            case BEE -> "Gardens bloom\naround it.";
            case ALLAY -> "A tidy little\nhelper.";
            case CHICKEN -> "Makes falling feel\nlike floating.";
            case COW -> "Fresh milk for bad\ndays.";
            case TURTLE -> "Breathe easy\nunderwater.";
            case ARMADILLO -> "Arrows? What\narrows?";
            case FROG -> "Swims like a dream.";
        };
    }

    // =====================================================================

    private static TextComponent.Builder page() {
        return Component.text().color(INK);
    }

    private static Component title(String text) {
        return Component.text(text, PINK).decorate(TextDecoration.BOLD);
    }

    private static Component ink(String text) {
        return Component.text(text, INK);
    }

    private static Component soft(String text) {
        return Component.text(text, SOFT).decorate(TextDecoration.ITALIC);
    }

    private static Component nl() {
        return Component.newline();
    }

    /** A picture of the pet: its spawn egg. */
    private static Component egg(Species s) {
        return item(s.type().getKey().getKey().toLowerCase(Locale.ROOT) + "_spawn_egg");
    }

    private static Component item(String texture) {
        return sprite("items", "item/" + texture);
    }

    private static Component block(String texture) {
        return sprite("blocks", "block/" + texture);
    }

    /** A picture. White, because text colour tints pictures (the book's brown ink turned them into silhouettes). */
    private static Component sprite(String atlas, String texture) {
        return Component.object(ObjectContents.sprite(Key.key("minecraft", atlas), Key.key("minecraft", texture)))
            .color(NamedTextColor.WHITE);
    }
}
