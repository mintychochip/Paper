package dev.mintychochip.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;

@Normal
class BehaviorSnapshotIntegrationTest {

    @Test
    void itemStackViewCopiesRealPaperStackInputAndOutput() {
        final ItemStack source = new ItemStack(Material.STONE, 2);
        final ItemStackView view = ItemStackView.from(source);

        source.setAmount(1);
        assertEquals(2, view.amount());

        final ItemStack copy = view.copy();
        copy.setAmount(7);
        assertEquals(2, view.amount());
    }
}
