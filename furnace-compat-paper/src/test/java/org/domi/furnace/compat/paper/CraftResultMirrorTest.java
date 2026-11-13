// PR #238 검토 R2: Paper 의 CraftingInventory#getResult() 는 결과 칸을 비추는 거울이라, 처리기가 setResult 없이 그 스택을 고쳐도(개수·이름) 결과가 바뀐다.
// 이전에는 getResult() 가 사본(bukkitAt)이라 고친 것이 사라졌다.
package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.Map;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.compat.paper.fixtures.Jars;
import org.domi.furnace.compat.paper.fixtures.RecorderPlugin;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CraftResultMirrorTest {

    @TempDir
    Path tmp;

    @BeforeEach
    void reset() { RecorderPlugin.reset(); }

    @Test
    void inPlaceEditOfPrepareCraftResultIsKept() throws Exception {
        Path jar = Jars.bukkit(tmp.resolve("recorder.jar"), """
                name: Recorder
                version: '1'
                main: org.domi.furnace.compat.paper.fixtures.RecorderPlugin
                api-version: '1.21'
                """, Map.of("config.yml", "a: 1\n"), RecorderPlugin.class);
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(jar).start()) {
            var p = server.join("Dana").player();
            server.tick(2);
            RecorderPlugin.ACTIONS.put("PrepareItemCraftEvent", e -> {
                var c = (org.bukkit.event.inventory.PrepareItemCraftEvent) e;
                c.getInventory().getResult().setAmount(7);   // 흔한 쓰임: 결과 스택을 그 자리에서 고친다(setResult 없이)
            });
            var snap = InventoryResultEventsTest.window("minecraft:player", 5);
            var f = new org.domi.furnace.api.event.inventory.PrepareItemCraftEvent(p, snap, "minecraft:oak_planks", ItemStack.of("minecraft:oak_planks").withAmount(4));
            server.api().events().call(f);
            assertEquals(7, f.result().amount(), "Paper: 거울을 고치면 결과가 바뀐다");
        }
    }
}
