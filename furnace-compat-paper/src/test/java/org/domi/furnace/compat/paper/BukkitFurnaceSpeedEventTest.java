package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.domi.furnace.api.inventory.InventorySnapshot;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.api.world.Location;
import org.domi.furnace.compat.paper.fixtures.Jars;
import org.domi.furnace.compat.paper.fixtures.RecorderPlugin;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/** (PR #381 독립 검토 시험을 옮김) CraftFurnace 배율 반올림·200 경계, 창 이벤트(열기·깔때기)의 위쪽 화로 인벤토리 배선. */
class BukkitFurnaceSpeedEventTest {

    @TempDir
    Path tmp;

    @BeforeEach
    void reset() { RecorderPlugin.reset(); }

    private Path recorderJar() throws Exception {
        return Jars.bukkit(tmp.resolve("recorder.jar"), """
                name: Recorder
                version: '1'
                main: org.domi.furnace.compat.paper.fixtures.RecorderPlugin
                api-version: '1.21'
                """, Map.of("config.yml", "a: 1\n"), RecorderPlugin.class);
    }

    /** Paper: ceil(200 / 3) = 67(내림이면 66), 배율 200 은 허용, 0 은 Integer.MAX_VALUE(→ short 로 -1 저장). */
    @Test
    void multiplierRoundsUpAndBoundaries() {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).start()) {
            Bukkit.setServer(org.bukkit.craftbukkit.CraftServer.INSTANCE);
            server.tick(3);
            BlockPos pos = new BlockPos(13, -20, 13);
            String dim = "minecraft:overworld";
            server.loadChunk(pos.chunkX(), pos.chunkZ());
            server.onRegion(pos, () -> {
                var w = server.api().world(dim);
                w.block(pos).set(new BlockData("minecraft:furnace", Map.of("facing", "north", "lit", "false")));
                try {
                    var f = (org.bukkit.block.Furnace) Compat.block(dim, pos).getState();
                    f.setCookSpeedMultiplier(3);
                    assertEquals(67, f.getCookTimeTotal(), "ceil");
                    f.setCookSpeedMultiplier(200);
                    assertEquals(1, f.getCookTimeTotal(), "200 은 허용(Paper multiplier <= 200)");
                    f.setCookSpeedMultiplier(0);
                    assertEquals(Integer.MAX_VALUE, f.getCookTimeTotal());
                    f.setCookSpeedMultiplier(3);
                    assertTrue(f.update());
                    assertEquals(67, w.blockEntityNbtNow(pos).orElseThrow().getShort("CookTimeTotal"));
                    assertEquals(3.0, w.blockEntityNbtNow(pos).orElseThrow().getDouble("Paper.CookSpeedMultiplier"));
                } finally {
                    w.block(pos).set(BlockData.AIR);
                }
                return null;
            });
        }
    }

    /**
     * T141E2e-D1 결정 B+: 서버는 바닐라 == 로 굽으므로, 플러그인이 진행 ≥ 총 시간을 적으면 update 가 진행을 총 시간 − 1 로 적는다
     * (다음 틱에 굽혀 Paper >= 와 같은 결과). 진행 < 총 시간이나 재료 칸이 빈 화로는 손대지 않는다.
     */
    @Test
    void overshootIsWrittenAsTotalMinusOne() {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).start()) {
            Bukkit.setServer(org.bukkit.craftbukkit.CraftServer.INSTANCE);
            server.tick(3);
            BlockPos pos = new BlockPos(13, -20, 29);
            String dim = "minecraft:overworld";
            server.loadChunk(pos.chunkX(), pos.chunkZ());
            server.onRegion(pos, () -> {
                var w = server.api().world(dim);
                w.block(pos).set(new BlockData("minecraft:furnace", Map.of("facing", "north", "lit", "false")));
                try {
                    java.util.function.IntUnaryOperator write = cook -> {
                        var f = (org.bukkit.block.Furnace) Compat.block(dim, pos).getState();
                        f.setCookTimeTotal(200);
                        f.setCookTime((short) cook);
                        assertTrue(f.update());
                        assertEquals(cook, f.getCookTime(), "이 상태 객체의 값은 플러그인이 적은 그대로");
                        return w.blockEntityNbtNow(pos).orElseThrow().getShort("CookTime");
                    };
                    assertEquals(250, write.applyAsInt(250), "재료가 없으면 굽을 것이 없어 그대로");
                    ((org.bukkit.block.Furnace) Compat.block(dim, pos).getState()).getInventory()
                            .setItem(0, new org.bukkit.inventory.ItemStack(Material.RAW_IRON));
                    assertEquals(199, write.applyAsInt(250), "넘치면 총 시간 − 1");
                    assertEquals(199, ((org.bukkit.block.Furnace) Compat.block(dim, pos).getState()).getCookTime(), "새로 읽으면 적힌 값");
                    assertEquals(199, write.applyAsInt(200), "같아도 총 시간 − 1(바닐라는 ++ 뒤에 비교)");
                    assertEquals(150, write.applyAsInt(150), "모자라면 그대로");
                } finally {
                    w.block(pos).set(BlockData.AIR);
                }
                return null;
            });
        }
    }

    /** InventoryOpenEvent 위쪽 인벤토리(typedView)와 InventoryMoveItemEvent 의 화로 쪽(container)이 FurnaceInventory 다(Paper CraftInventoryFurnace). */
    @Test
    void eventTopInventoryIsFurnaceInventory() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(recorderJar()).start()) {
            var tp = server.join("Dana");
            server.tick(2);
            var p = tp.player();
            List<String> log = new CopyOnWriteArrayList<>();
            RecorderPlugin.ACTIONS.put("InventoryOpenEvent", e -> {
                var top = ((org.bukkit.event.inventory.InventoryOpenEvent) e).getInventory();
                log.add("open:" + top.getType() + "," + (top instanceof org.bukkit.inventory.FurnaceInventory fi ? fi.getSmelting().getType() + "/" + fi.getFuel().getType() + "/" + fi.getResult().getType() : "plain"));
            });
            RecorderPlugin.ACTIONS.put("InventoryMoveItemEvent", e -> {
                var m = (org.bukkit.event.inventory.InventoryMoveItemEvent) e;
                var src = m.getSource();
                log.add("move:" + src.getType() + "," + (src instanceof org.bukkit.inventory.FurnaceInventory fi ? fi.getResult().getType() + "," + src.getLocation().getBlockX() : "plain"));
            });
            List<ItemStack> win = new ArrayList<>(Collections.nCopies(3 + 36, ItemStack.EMPTY));
            win.set(0, ItemStack.of("minecraft:raw_iron"));
            win.set(1, ItemStack.of("minecraft:coal"));
            win.set(2, ItemStack.of("minecraft:iron_ingot"));
            server.api().events().call(new org.domi.furnace.api.event.inventory.InventoryOpenEvent(p, new InventorySnapshot("minecraft:smoker", win)));
            String w = "minecraft:overworld";
            var f = new org.domi.furnace.api.event.inventory.InventoryMoveItemEvent(
                    new InventorySnapshot("minecraft:blast_furnace", List.of(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.of("minecraft:iron_ingot"))),
                    new Location(w, 4.5, 65.5, 2.5), new InventorySnapshot("minecraft:hopper", Collections.nCopies(5, ItemStack.EMPTY)),
                    new Location(w, 4.5, 64.5, 2.5), ItemStack.of("minecraft:iron_ingot"), false);
            server.api().events().call(f);
            assertEquals(List.of("open:SMOKER,RAW_IRON/COAL/IRON_INGOT", "move:BLAST_FURNACE,IRON_INGOT,4"), log);
        }
    }
}
