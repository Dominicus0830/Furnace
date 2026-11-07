// 플레이어끼리 추적(Region.updateVisibility)에서 플러그인 숨기기·변장: 숨기면 엔티티와 탭에서 빠지고, 변장하면 다른 종류로 다시 스폰된다.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.entity.EntityType;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.view.Appearance;
import org.domi.furnace.server.entity.view.EntityViewRules;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.play.AddEntity;
import org.domi.furnace.server.net.protocol.play.PlayerInfoRemove;
import org.domi.furnace.server.net.protocol.play.PlayerInfoUpdate;
import org.domi.furnace.server.net.protocol.play.RemoveEntities;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EntityViewPlayersTest {

    private static final RegionPos A = new RegionPos(0, 0);
    private static final Object OWNER = "plugin";

    private FurnaceSchedulerImpl sched;
    private World w;
    private EmbeddedChannel chObs, chTgt;
    private Player obs, tgt;

    @BeforeEach
    void setup() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 2);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, sched.regionThreadCount(),
                16, 20, 20, reg.directBits(), 7, Executors.newSingleThreadExecutor());
        chObs = new ConcurrentEmbeddedChannel();
        chTgt = new ConcurrentEmbeddedChannel();
        obs = new Player(1, UUID.randomUUID(), "obs", chObs);
        tgt = new Player(2, UUID.randomUUID(), "tgt", chTgt);
        obs.publishPosition(10, -60, 10, 0, 0, true);
        obs.drainPending();
        tgt.publishPosition(30, -60, 10, 0, 0, true);
        tgt.drainPending();
        w.playerOnline(obs);
        w.playerOnline(tgt);
        sched.region(A).execute(() -> w.regionAt(A).adopt(obs)).get();
        sched.region(A).execute(() -> w.regionAt(A).adopt(tgt)).get();
    }

    @AfterEach
    void close() {
        EntityViewRules.clearForTest();
        if (sched != null) sched.close();
    }

    private void tick() throws Exception { sched.region(A).execute(() -> w.regionAt(A).tick()).get(); }

    private record Sent(int id, ByteBuf body) {}

    private List<Sent> drain(EmbeddedChannel ch) {
        ch.runPendingTasks();
        List<Sent> out = new ArrayList<>();
        Object o;
        while ((o = ch.readOutbound()) != null) {
            ByteBuf b = (ByteBuf) o;
            out.add(new Sent(Types.readVarInt(b), b));
        }
        return out;
    }

    private List<Integer> ids(EmbeddedChannel ch) {
        List<Integer> out = new ArrayList<>();
        for (Sent s : drain(ch)) {
            out.add(s.id);
            s.body.release();
        }
        return out;
    }

    private List<Integer> ticks(int n) throws Exception {
        List<Integer> all = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            tick();
            all.addAll(ids(chObs));
        }
        return all;
    }

    @Test
    void hiddenPlayerLeavesEntityAndTab_thenComesBack() throws Exception {
        List<Integer> met = ticks(2);
        assertTrue(met.contains(AddEntity.ID) && met.contains(PlayerInfoUpdate.ID), met.toString());
        assertTrue(obs.tracked().containsKey(tgt.entityId()));

        EntityViewRules.hide(OWNER, obs, tgt.uuid(), null, true);
        Player.bumpRoster();
        List<Integer> hidden = ticks(3);
        assertTrue(hidden.contains(RemoveEntities.ID), "엔티티에서 빠진다: " + hidden);
        assertTrue(hidden.contains(PlayerInfoRemove.ID), "탭에서도 빠진다(Bukkit hidePlayer): " + hidden);
        assertFalse(hidden.contains(AddEntity.ID), "숨긴 동안 다시 스폰하지 않는다: " + hidden);
        assertFalse(obs.tracked().containsKey(tgt.entityId()));
        assertFalse(obs.listed().containsKey(tgt.entityId()));
        assertTrue(obs.listed().containsKey(obs.entityId()), "자기 탭 줄은 그대로");

        // 숨긴 사람의 시점은 그대로 — 대상은 관찰자를 계속 본다
        ids(chTgt);
        assertTrue(tgt.tracked().containsKey(obs.entityId()));

        EntityViewRules.show(OWNER, obs, tgt.uuid(), null, true);
        Player.bumpRoster();
        List<Integer> back = ticks(2);
        assertTrue(back.contains(PlayerInfoUpdate.ID) && back.contains(AddEntity.ID), back.toString());
        assertTrue(back.indexOf(PlayerInfoUpdate.ID) < back.indexOf(AddEntity.ID), "탭 정보가 스폰보다 먼저: " + back);
    }

    @Test
    void disguisedPlayerRespawnsAsOtherKind_forThatViewerOnly() throws Exception {
        ticks(2);
        ids(chTgt);
        int creeper = EntityType.registryId("minecraft:creeper");
        EntityViewRules.setAppearance(OWNER, tgt.uuid(), obs, new Appearance(creeper, "minecraft:creeper", 0, new int[0], null, null, null), null, true);
        tick();
        List<Sent> out = drain(chObs);
        List<Integer> seq = new ArrayList<>();
        int type = -1;
        for (Sent s : out) {
            seq.add(s.id);
            if (s.id == AddEntity.ID) {
                ByteBuf b = s.body.duplicate();
                assertEquals(tgt.entityId(), Types.readVarInt(b));
                b.skipBytes(16);
                type = Types.readVarInt(b);
            }
            s.body.release();
        }
        assertTrue(seq.indexOf(RemoveEntities.ID) >= 0 && seq.indexOf(RemoveEntities.ID) < seq.indexOf(AddEntity.ID), "지웠다 다시: " + seq);
        assertEquals(creeper, type);
        assertFalse(seq.contains(PlayerInfoRemove.ID), "탭 줄은 그대로: " + seq);
        assertEquals(List.of(), ticks(2).stream().filter(i -> i == AddEntity.ID).toList(), "모습이 그대로면 다시 스폰하지 않는다");

        // 대상 쪽(자기 화면)은 바뀌지 않는다
        assertFalse(ids(chTgt).contains(AddEntity.ID));

        // 지우면 플레이어로 돌아온다
        EntityViewRules.setAppearance(OWNER, tgt.uuid(), obs, null, null, true);
        tick();
        int back = -1;
        for (Sent s : drain(chObs)) {
            if (s.id == AddEntity.ID) {
                ByteBuf b = s.body.duplicate();
                Types.readVarInt(b);
                b.skipBytes(16);
                back = Types.readVarInt(b);
            }
            s.body.release();
        }
        assertEquals(EntityType.registryId("minecraft:player"), back);
    }
}
