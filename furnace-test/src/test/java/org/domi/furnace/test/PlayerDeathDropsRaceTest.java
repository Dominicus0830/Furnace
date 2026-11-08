// 사망 드롭 목록은 region 스레드에서 죽는 순간 뜨고 인벤토리는 나중에 연결 이벤트 루프에서 비운다 — 그 사이 이벤트 루프가 처리한
// 인벤토리 조작(Q·Ctrl+Q 버리기)이 이미 꺼낸 아이템이 목록에서 또 떨어지면 안 된다(PR #164 검토). 관전자 keepLevel(false) 리스폰은 Paper 처럼 newLevel.
package org.domi.furnace.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.event.EventPriority;
import org.domi.furnace.api.event.player.PlayerDeathEvent;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.server.entity.AABB;
import org.domi.furnace.server.entity.ItemEntity;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.test.sample.CounterPlugin;
import org.junit.jupiter.api.Test;

class PlayerDeathDropsRaceTest {

    private static Map<String, Integer> floor(FurnaceTestServer server) {
        var p = server.internals().playerByName("Dana");
        return server.await(server.internals().world().submitAtChunk(p.chunkPos(), r -> {
            Map<String, Integer> out = new TreeMap<>();
            var box = new AABB(p.x() - 8, p.y() - 8, p.z() - 8, p.x() + 8, p.y() + 8, p.z() + 8);
            for (var e : r.level().getEntities(null, box, x -> x instanceof ItemEntity)) {
                var st = ((ItemEntity) e).getItem();
                out.merge(server.internals().world().itemRegistry().nameOf(st.itemId()), st.count(), Integer::sum);
            }
            return out;
        }));
    }

    /** 클라이언트가 죽음을 알기 전에 보낸 Q(player_action 4 = DROP_ITEM)가 사망 이벤트 도중 서버에 닿은 상황. */
    @Test
    void dropKeyProcessedDuringDeathWindowDuplicatesTheItem() throws Exception { run(4, 4); }

    /** Ctrl+Q(player_action 3 = DROP_ALL_ITEMS): 한 칸 통째로 복제된다. */
    @Test
    void dropAllKeyProcessedDuringDeathWindowDuplicatesTheWholeStack() throws Exception { run(3, 4); }

    private void run(int status, int expectedDirt) throws Exception {
        List<String> notes = new CopyOnWriteArrayList<>();
        try (var server = FurnaceTestServer.builder().plugin(CounterPlugin.class).start()) {
            TestPlayer tp = server.join("Dana");
            server.tick(2);
            tp.sync();
            var write = tp.player().inventory().set(0, new ItemStack("minecraft:dirt", 4));   // 든 칸(핫바 0)
            for (int i = 0; i < 40 && !write.isDone(); i++) { server.tick(1); tp.sync(); }
            assertTrue(write.isDone());
            var p = server.internals().playerByName("Dana");

            // 아무 일도 안 하는 리스너가 이벤트 루프가 Q 를 처리할 시간을 준다(실제로는 느린 리스너·패킷 경합)
            server.api().events().subscribe(server.plugin(CounterPlugin.class), PlayerDeathEvent.class, EventPriority.MONITOR, false, e -> {
                tp.sendPacket(0x24, b -> {
                    Types.writeVarInt(b, status);       // 4 DROP_ITEM · 3 DROP_ALL_ITEMS
                    Types.writeBlockPos(b, 0, 0, 0);
                    b.writeByte(0);
                    Types.writeVarInt(b, 0);
                });
                long dl = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (p.inventory().get(36).count() == 4 && System.nanoTime() < dl) java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L);
                notes.add("held after Q=" + p.inventory().get(36).count());
            });

            tp.player().setHealth(0f);
            server.tick(3);
            tp.sync();
            server.tick(5);
            int dirt = floor(server).getOrDefault("minecraft:dirt", 0);
            notes.add("dirt on floor=" + dirt);
            // 바닐라·Paper: Q 가 죽음 전에 처리되면 1 + 3, 뒤면 0 + 4 — 어느 쪽이든 흙은 4 개다
            assertEquals(expectedDirt, dirt, "흙이 늘었거나 줄었다: " + notes);
        }
    }

    /** Paper ServerPlayer.reset 은 keepLevel 만 본다(SPIGOT-6687) — 관전자라도 플러그인이 keepLevel(false) 로 바꾸면 newLevel 로 돌아간다. */
    @Test
    void spectatorWithKeepLevelFalseResetsLikePaper() {
        try (var server = FurnaceTestServer.builder().plugin(CounterPlugin.class).start()) {
            server.api().events().subscribe(server.plugin(CounterPlugin.class), PlayerDeathEvent.class, e -> {
                e.keepLevel(false);
                e.newLevel(1);
            });
            TestPlayer tp = server.join("Dana");
            server.tick(2);
            tp.sync();
            server.execute("gamemode spectator Dana");
            server.execute("xp set Dana 10 levels");
            server.tick(2);
            tp.player().setHealth(0f);
            server.tick(3);
            tp.sync();
            server.tick(3);
            var p = server.internals().playerByName("Dana");
            p.vitals().respawn();
            assertEquals(1, p.vitals().xpLevel(), "Paper: keepLevel=false → newLevel");
        }
    }
}
