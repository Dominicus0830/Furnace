// 사망 드롭 목록은 region 스레드에서 죽는 순간 뜨고 인벤토리는 나중에 연결 이벤트 루프에서 비운다 — 그 사이 죽은 플레이어가 보낸
// 인벤토리 조작(Q·Ctrl+Q 버리기, 창 THROW 클릭)은 Paper 처럼 무시한다(PR #164 검토·후속: 무덤 플러그인과 바닥에 둘 다 생기던 틈). 관전자 keepLevel(false) 리스폰은 Paper 처럼 newLevel.
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

    private static void sendDropKey(TestPlayer tp, int status) {
        tp.sendPacket(0x24, b -> {
            Types.writeVarInt(b, status);       // 4 DROP_ITEM · 3 DROP_ALL_ITEMS
            Types.writeBlockPos(b, 0, 0, 0);
            b.writeByte(0);
            Types.writeVarInt(b, 0);
        });
    }

    /** 인벤토리 창(0)의 핫바 0 칸(36)을 THROW(clickType 4) 클릭 — 버튼 0 은 하나, 1 은 한 칸 통째. */
    private static void sendThrowClick(TestPlayer tp, int button) {
        tp.sendPacket(0x0E, b -> {
            Types.writeVarInt(b, 0);       // containerId
            Types.writeVarInt(b, 0);       // stateId
            b.writeShort(36);
            b.writeByte(button);
            Types.writeVarInt(b, 4);       // THROW
            Types.writeVarInt(b, 0);       // changedSlots 없음
            Types.writeVarInt(b, 0);       // carried: 빈 칸
        });
    }

    /** 사망 이벤트 도중(죽은 뒤) 클라이언트가 보낸 Q 는 무시한다 — 바닐라·Paper 는 죽은 플레이어의 Q 가 아무 일도 안 한다. 흙은 목록에서 4 개만 떨어진다. */
    @Test
    void dropKeyWhileDeadIsIgnored() throws Exception { run(p -> sendDropKey(p, 4), 4, 0); }

    /** Ctrl+Q 도 마찬가지. */
    @Test
    void dropAllKeyWhileDeadIsIgnored() throws Exception { run(p -> sendDropKey(p, 3), 4, 0); }

    /** 창 THROW 클릭(Ctrl 없이)도 무시. */
    @Test
    void windowThrowClickWhileDeadIsIgnored() throws Exception { run(p -> sendThrowClick(p, 0), 4, 0); }

    /** 창 THROW 클릭(통째)도 무시. */
    @Test
    void windowThrowAllClickWhileDeadIsIgnored() throws Exception { run(p -> sendThrowClick(p, 1), 4, 0); }

    /**
     * 무덤 플러그인: 사망 이벤트에서 드롭 목록을 가져가(비운다) 무덤에 넣었다. 그 뒤 죽은 플레이어의 Q·창 클릭이 처리되면 아이템이 인벤토리에서 나와
     * 무덤과 바닥에 둘 다 생긴다 — 이제는 무시되므로 바닥에는 아무것도 없어야 한다(PR #164 후속).
     */
    @Test
    void graveRobbedDropsAreNotDuplicatedByDropKey() throws Exception { run(p -> sendDropKey(p, 4), 0, 4); }

    @Test
    void graveRobbedDropsAreNotDuplicatedByDropAllKey() throws Exception { run(p -> sendDropKey(p, 3), 0, 4); }

    @Test
    void graveRobbedDropsAreNotDuplicatedByWindowThrow() throws Exception { run(p -> sendThrowClick(p, 0), 0, 4); }

    @Test
    void graveRobbedDropsAreNotDuplicatedByWindowThrowAll() throws Exception { run(p -> sendThrowClick(p, 1), 0, 4); }

    /** 살아 있을 때의 Q 는 그대로 된다(회귀 방지): 흙 4 개에서 하나 던지고 3 개 남는다. */
    @Test
    void dropKeyWhileAliveStillDrops() throws Exception {
        try (var server = FurnaceTestServer.builder().plugin(CounterPlugin.class).start()) {
            TestPlayer tp = server.join("Dana");
            server.tick(2);
            tp.sync();
            var write = tp.player().inventory().set(0, new ItemStack("minecraft:dirt", 4));
            for (int i = 0; i < 40 && !write.isDone(); i++) { server.tick(1); tp.sync(); }
            assertTrue(write.isDone());
            var p = server.internals().playerByName("Dana");
            sendDropKey(tp, 4);
            long dl = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (p.inventory().get(36).count() == 4 && System.nanoTime() < dl) java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L);
            assertEquals(3, p.inventory().get(36).count());
            server.tick(3);
            tp.sync();
            assertEquals(1, floor(server).getOrDefault("minecraft:dirt", 0));
        }
    }

    /**
     * @param action 사망 이벤트 도중 보낼 클라이언트 패킷
     * @param expectedFloor 사망 처리가 끝난 뒤 바닥 흙 개수
     * @param graveTakes 무덤 플러그인이 목록에서 가져가는 흙 개수(0 이면 안 가져간다)
     */
    private void run(java.util.function.Consumer<TestPlayer> action, int expectedFloor, int graveTakes) throws Exception {
        List<String> notes = new CopyOnWriteArrayList<>();
        try (var server = FurnaceTestServer.builder().plugin(CounterPlugin.class).start()) {
            TestPlayer tp = server.join("Dana");
            server.tick(2);
            tp.sync();
            var write = tp.player().inventory().set(0, new ItemStack("minecraft:dirt", 4));   // 든 칸(핫바 0)
            for (int i = 0; i < 40 && !write.isDone(); i++) { server.tick(1); tp.sync(); }
            assertTrue(write.isDone());
            var p = server.internals().playerByName("Dana");

            server.api().events().subscribe(server.plugin(CounterPlugin.class), PlayerDeathEvent.class, EventPriority.MONITOR, false, e -> {
                if (graveTakes > 0) e.drops().clear();   // 무덤 플러그인이 드롭을 가져갔다
                // 이벤트 루프가 이 패킷을 처리할 시간을 준다(실제로는 느린 리스너·패킷 경합)
                action.accept(tp);
                java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(300));
                notes.add("held during event=" + p.inventory().get(36).count());
                assertEquals(4, p.inventory().get(36).count(), "죽은 뒤의 Q·창 클릭이 인벤토리를 바꿨다");
            });

            tp.player().setHealth(0f);
            server.tick(3);
            tp.sync();
            server.tick(5);
            int dirt = floor(server).getOrDefault("minecraft:dirt", 0);
            notes.add("dirt on floor=" + dirt);
            assertEquals(expectedFloor, dirt, "흙이 늘었거나 줄었다: " + notes);
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
