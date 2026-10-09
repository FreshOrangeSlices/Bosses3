package com.gmail.nossr50.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MaterialMapStoreTest {

    @Nested
    class TreeFellerRegisters {
        /**
         * A shelf mushroom grows on a log and vanilla pops it when that log goes, so Tree Feller
         * has to both break it and hand over its drop instead of putting it on the leaf roll.
         */
        @Test
        void shelfMushroomShouldBeDestructibleAndGuaranteedDrop() {
            // Given - a fully populated material map store
            final MaterialMapStore store = new MaterialMapStore();

            // When - shelf mushroom is looked up in both Tree Feller registers
            // Then - it is destructible and its drop is guaranteed
            assertThat(store.isTreeFellerDestructible("shelf_mushroom")).isTrue();
            assertThat(store.isTreeFellerGuaranteedDrop("shelf_mushroom")).isTrue();
        }

        /**
         * The guaranteed drop register is a narrow carve out; leaves must keep rolling for their
         * drop or Tree Feller would bury the player in leaf blocks.
         */
        @Test
        void leavesShouldNotBeGuaranteedDrop() {
            // Given - a fully populated material map store
            final MaterialMapStore store = new MaterialMapStore();

            // When - oak leaves are looked up in both Tree Feller registers
            // Then - they are destructible but stay on the drop roll
            assertThat(store.isTreeFellerGuaranteedDrop("oak_leaves")).isFalse();
            assertThat(store.isTreeFellerDestructible("oak_leaves")).isTrue();
        }

        /**
         * Tree Feller only consults the guaranteed drop register inside its non-wood branch,
         * which a block reaches only after passing the destructible check. An entry that is
         * guaranteed but not destructible would never be looked at, so the registers have to
         * stay in step.
         */
        @Test
        void guaranteedDropEntriesShouldAllBeDestructible()
                throws NoSuchFieldException, IllegalAccessException {
            // Given - a fully populated material map store
            final MaterialMapStore store = new MaterialMapStore();

            // When - the guaranteed drop register is read directly
            final Field guaranteedDropField = MaterialMapStore.class
                    .getDeclaredField("treeFellerGuaranteedDropWhiteList");
            guaranteedDropField.setAccessible(true);
            @SuppressWarnings("unchecked")
            final Set<String> guaranteedDrops = (Set<String>) guaranteedDropField.get(store);

            // Then - the register carries entries and each one is destructible as well
            assertThat(guaranteedDrops).isNotEmpty();
            assertThat(guaranteedDrops).allSatisfy(entry ->
                    assertThat(store.isTreeFellerDestructible(entry))
                            .as("%s must also be Tree Feller destructible", entry).isTrue());
        }
    }
}
