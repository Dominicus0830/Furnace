package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/** CraftingInput.ofPositioned: 바닐라처럼 빈 가장자리를 잘라 내고 잘린 자리를 돌려준다. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsCraftingInputTest {

    FurnaceTestServer server;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        org.bukkit.Bukkit.setServer(org.bukkit.craftbukkit.CraftServer.INSTANCE);
    }

    @AfterAll
    void stop() { server.close(); }

    private static ItemStack s(net.minecraft.world.item.Item i) { return new ItemStack(i); }

    @Test
    void trimsEmptyEdges() {
        ItemStack e = ItemStack.EMPTY;
        // 3x3 에서 가운데 한 칸 아래(1,2)와 (2,2) 두 칸만 찬 경우
        List<ItemStack> grid = List.of(e, e, e, e, e, e, e, s(Items.STICK), s(Items.DIAMOND));
        CraftingInput.Positioned p = CraftingInput.ofPositioned(3, 3, grid);
        assertEquals(2, p.input().width());
        assertEquals(1, p.input().height());
        assertEquals(1, p.left());
        assertEquals(2, p.top());
        assertEquals(2, p.input().ingredientCount());
        assertEquals(Items.DIAMOND, p.input().getItem(1, 0).getItem());
        assertEquals(2, p.input().size());
    }

    @Test
    void fullGridIsKeptAsIs() {
        List<ItemStack> grid = List.of(s(Items.STICK), ItemStack.EMPTY, ItemStack.EMPTY, s(Items.STICK));
        CraftingInput in = CraftingInput.of(2, 2, grid);
        assertSame(grid, in.items());
        assertEquals(2, in.width());
    }

    @Test
    void emptyGrid() {
        assertSame(CraftingInput.Positioned.EMPTY, CraftingInput.ofPositioned(2, 2, List.of(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY)));
        assertTrue(CraftingInput.EMPTY.isEmpty());
        assertEquals(CraftingInput.of(0, 0, List.of()), CraftingInput.EMPTY);
    }
}
