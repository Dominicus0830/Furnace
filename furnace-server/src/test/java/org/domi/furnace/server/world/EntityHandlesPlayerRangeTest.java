// T-122 검토 보강(PR #395 R3): 플레이어끼리 보임(Region.updateVisibility)이 entity-tracking-range.players 를 따르는가. 시야 20청크(320블록) 장면.
package org.domi.furnace.server.world;

import org.domi.furnace.api.GameVersion;
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

class EntityHandlesPlayerRangeTest {

    private static final RegionPos A = new RegionPos(0, 0);
    private static final Object OWNER = "plugin";

    private FurnaceSchedulerImpl sched;
    private World w;
    private EmbeddedChannel chObs, chTgt;
    private Player obs, tgt;

    @BeforeEach
    void setup() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 2);
        BlockRegistry reg = BlockRegistry.load("blocks/" + GameVersion.RESOURCE_TAG + ".txt");
        w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, sched.regionThreadCount(),
                16, 20, 20, reg.directBits(), 7, Executors.newSingleThreadExecutor());
        chObs = new ConcurrentEmbeddedChannel();
        chTgt = new ConcurrentEmbeddedChannel();
        obs = new Player(1, UUID.randomUUID(), "obs", chObs);
        tgt = new Player(2, UUID.randomUUID(), "tgt", chTgt);
        obs.publishPosition(10, -60, 10, 0, 0, true);
        obs.drainPending();
        tgt.publishPosition(150, -60, 10, 0, 0, true);
        tgt.drainPending();
        w.playerOnline(obs);
        w.playerOnline(tgt);
        sched.region(A).execute(() -> w.regionAt(A).adopt(obs)).get();
        sched.region(A).execute(() -> w.regionAt(A).adopt(tgt)).get();
    }

    @AfterEach
    void close() {
        org.domi.furnace.server.config.EntityHandleSettings.install(org.domi.furnace.server.config.EntityHandleSettings.VANILLA);
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
    void vanillaSeesPlayerAt140Blocks() throws Exception {
        org.domi.furnace.server.config.EntityHandleSettings.install(org.domi.furnace.server.config.EntityHandleSettings.VANILLA);
        ticks(2);
        assertTrue(obs.tracked().containsKey(tgt.entityId()), "바닐라: min(512, 시야 320) 안의 140블록은 보인다");
    }

    @Test
    void paperPlayersRangeHidesPlayerAt140Blocks() throws Exception {
        org.domi.furnace.server.config.EntityHandleSettings.install(org.domi.furnace.server.config.EntityHandleSettings.DEFAULT);
        ticks(2);
        assertFalse(obs.tracked().containsKey(tgt.entityId()), "Paper players=128: 140블록은 보이지 않는다");
    }
}
