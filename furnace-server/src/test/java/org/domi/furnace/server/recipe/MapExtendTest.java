// 지도 확장: 종이 8 + 채워진 지도 가운데 — 지도 데이터가 있고 배율이 4 미만이어야 하며, 결과는 map_post_processing=scale 인 지도 한 장.
package org.domi.furnace.server.recipe;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.map.MapStore;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MapExtendTest {

    @TempDir Path dir;

    @AfterEach
    void tearDown() { MapStore.uninstall(); }

    static void writeMap(Path world, int id, int scale, boolean locked) throws Exception {
        NbtCompound data = new NbtCompound();
        data.putByte("scale", (byte) scale);
        data.putByte("locked", (byte) (locked ? 1 : 0));
        NbtCompound root = new NbtCompound();
        root.putCompound("data", data);
        Files.createDirectories(world.resolve("data"));
        try (var os = Files.newOutputStream(world.resolve("data/map_" + id + ".dat"))) { NbtIo.writeGzip(os, root, ""); }
    }

    static CraftingInput grid(ItemStack map) {
        List<ItemStack> l = new ArrayList<>();
        for (int i = 0; i < 9; i++) l.add(i == 4 ? map : TestRecipes.item("paper", 1));
        return CraftingInput.of(3, 3, l);
    }

    @Test
    void extendsOnlyKnownMapsBelowScaleFour() throws Exception {
        RecipeManager rm = TestRecipes.manager();
        assumeTrue(rm != null, "로컬 바닐라 데이터 없음");
        MapStore.install(dir);
        writeMap(dir, 7, 1, false);
        writeMap(dir, 8, 4, false);
        ItemStack m7 = ItemTools.with(TestRecipes.item("filled_map", 1), "minecraft:map_id", 7);
        ItemStack m8 = ItemTools.with(TestRecipes.item("filled_map", 1), "minecraft:map_id", 8);
        ItemStack m9 = ItemTools.with(TestRecipes.item("filled_map", 1), "minecraft:map_id", 9);   // 파일 없음
        var e = rm.findCrafting(grid(m7), null);
        assertNotNull(e);
        ItemStack out = e.recipe().assemble(grid(m7), rm.context());
        assertEquals("minecraft:filled_map", ItemTools.itemName(out));
        assertEquals("scale", ItemTools.component(out, "minecraft:map_post_processing"));
        assertEquals(7, ((Number) ItemTools.component(out, "minecraft:map_id")).intValue());
        assertNull(rm.findCrafting(grid(m8), null), "배율 4 는 더 늘릴 수 없다");
        assertNull(rm.findCrafting(grid(m9), null), "지도 데이터가 없으면 안 된다");
        MapStore.install(dir);
        var d7 = MapStore.of(m7);
        assertEquals(1, d7.scale());
        assertFalse(d7.locked());
    }

    @Test
    void explorationMapsCannotBeExtended() throws Exception {
        RecipeManager rm = TestRecipes.manager();
        assumeTrue(rm != null, "로컬 바닐라 데이터 없음");
        MapStore.install(dir);
        var plain = org.domi.furnace.server.map.MapData.createFresh(0, 0, (byte) 1, true, false, "minecraft:overworld");
        var explorer = org.domi.furnace.server.map.MapData.createFresh(0, 0, (byte) 1, true, true, "minecraft:overworld");
        explorer.addDecoration(org.domi.furnace.server.map.MapDecorationType.MONUMENT, null, "+", 10, 10, 180, null);
        MapStore.put(20, plain);
        MapStore.put(21, explorer);
        ItemStack m20 = ItemTools.with(TestRecipes.item("filled_map", 1), "minecraft:map_id", 20);
        ItemStack m21 = ItemTools.with(TestRecipes.item("filled_map", 1), "minecraft:map_id", 21);
        assertNotNull(rm.findCrafting(grid(m20), null));
        assertNull(rm.findCrafting(grid(m21), null), "탐험 지도(표식이 있는 지도 데이터)는 늘릴 수 없다");
    }
}
