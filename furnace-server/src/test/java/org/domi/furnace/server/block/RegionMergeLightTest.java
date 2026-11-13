// F8 / C-L1 회귀: region 합치기(absorb)·나누기(splitTo)가 미처리 빛 검사와 방송 전 빛 변경을 옮겨야 한다.
// 바닐라는 차원 하나의 빛 엔진이 하나라 이런 틈이 없다 — 합치기·나누기를 거쳐도 결과는 합치지 않은 것과 같아야 한다.
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RegionMergeLightTest {
    private final Map<Long, ChunkColumn> shared = new HashMap<>();
    private final SuperflatWorldSource gen = new SuperflatWorldSource(BlockRegistry.load("blocks/1.21.1.txt"), 0);

    /** 같은 청크 자료를 함께 보되 owns 로 자기 것만 "올라온" 것으로 치는 레벨 — 합치기 전·나눈 뒤의 region 하나하나를 흉내 낸다. */
    private final class Lvl extends ChunkLevel {
        final List<Packet> light = new ArrayList<>();
        BiPredicate<Integer, Integer> owns = (x, z) -> true;

        Lvl() { super(1000000); }

        @Override protected ChunkColumn chunk(int x, int z) {
            if (Math.abs(x) > 2 || Math.abs(z) > 2 || !owns.test(x, z)) return null;
            return shared.computeIfAbsent((long) x << 32 | (z & 0xFFFFFFFFL), k -> gen.generate(x, z));
        }

        @Override protected void markDirty(int x, int z) {}
        @Override protected void broadcastToChunk(int x, int z, Packet p) {}
        @Override protected void broadcastLightToChunk(int x, int z, Packet p) { light.add(p); }
        @Override protected LevelServices services() { return LevelServices.DEFAULT; }
    }

    private int glow;

    @BeforeEach
    void setUp() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
        shared.clear();
        glow = B.defaultState("glowstone");
    }

    private Lvl lit() {
        Lvl l = new Lvl();
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) l.lightNewChunk(x, z);
        l.runLightUpdates();
        return l;
    }

    @Test
    void absorbKeepsPendingLightChecks() {
        Lvl a = lit(), b = new Lvl();
        Pos p = new Pos(0, -59, 0);
        b.setBlock(p, glow, 2);   // 틱 끝 계산 전에 놓았다
        a.absorb(b);
        a.runLightUpdates();
        assertEquals(15, a.getBlockLight(p));
        assertEquals(14, a.getBlockLight(new Pos(1, -59, 0)));
    }

    @Test
    void absorbKeepsPendingLightBroadcast() {
        lit();   // 청크 빛을 먼저 만든다(공유 청크 자료)
        Lvl b = new Lvl();
        b.setBlock(new Pos(20, -59, 20), glow, 2);
        b.runLightUpdates();   // 빛은 계산됐고 다음 틱 broadcastChanges 를 기다린다
        Lvl a = new Lvl();
        a.absorb(b);
        a.broadcastChanges();
        assertTrue(a.light.size() > 0, "합친 쪽이 빛 갱신 패킷을 보내야 한다");
        int sent = a.light.size();
        b.broadcastChanges();
        assertEquals(0, b.light.size(), "버려진 쪽에는 방송 대기분이 남지 않는다");
        assertEquals(sent, a.light.size());
    }

    @Test
    void absorbMergesLightMasksOfSameChunk() {
        // 같은 청크의 다른 구역을 각자 바꾼 두 레벨을 합치면, 한 레벨이 둘 다 바꾼 것과 같은 방송(청크당 패킷 하나, 마스크 합침)
        Pos p1 = new Pos(20, -59, 20), p2 = new Pos(21, 40, 21);
        lit();
        Lvl ref = new Lvl();
        ref.setBlock(p1, glow, 2);
        ref.setBlock(p2, glow, 2);
        ref.runLightUpdates();
        ref.broadcastChanges();
        shared.clear();
        lit();
        Lvl a = new Lvl(), b = new Lvl();
        a.setBlock(p1, glow, 2);
        a.runLightUpdates();
        b.setBlock(p2, glow, 2);
        b.runLightUpdates();
        a.absorb(b);
        a.broadcastChanges();
        assertEquals(ref.light.size(), a.light.size());
        assertTrue(a.light.size() > 0);
        Map<Long, org.domi.furnace.server.net.protocol.play.LightUpdate> byChunk = new HashMap<>();
        for (Packet pk : ref.light) {
            var lu = (org.domi.furnace.server.net.protocol.play.LightUpdate) pk;
            byChunk.put((long) lu.chunkX() << 32 | (lu.chunkZ() & 0xFFFFFFFFL), lu);
        }
        for (Packet pk : a.light) {
            var lu = (org.domi.furnace.server.net.protocol.play.LightUpdate) pk;
            var want = byChunk.get((long) lu.chunkX() << 32 | (lu.chunkZ() & 0xFFFFFFFFL));
            org.junit.jupiter.api.Assertions.assertNotNull(want);
            assertEquals(want.skyMask(), lu.skyMask());
            assertEquals(want.blockMask(), lu.blockMask());
            org.junit.jupiter.api.Assertions.assertArrayEquals(want.blockData(), lu.blockData());
        }
    }

    @Test
    void splitCarriesPendingLightChecksByChunk() {
        Lvl c = lit(), d = new Lvl();
        Pos far = new Pos(20, -58, 20), near = new Pos(-20, -58, -20);
        c.setBlock(far, glow, 2);
        c.setBlock(near, glow, 2);
        c.splitTo(d, (cx, cz) -> cx >= 1 && cz >= 1);
        c.owns = (cx, cz) -> !(cx >= 1 && cz >= 1);
        d.owns = (cx, cz) -> cx >= 1 && cz >= 1;
        c.runLightUpdates();
        d.runLightUpdates();
        assertEquals(15, d.getBlockLight(far));
        assertEquals(15, c.getBlockLight(near));
        assertEquals(0, c.getBlockLight(far), "c 는 더 이상 d 쪽 칸을 보지 않는다");
    }

    @Test
    void splitCarriesPendingLightBroadcastByChunk() {
        Lvl c = lit(), d = new Lvl();
        c.setBlock(new Pos(20, -58, 20), glow, 2);
        c.setBlock(new Pos(-20, -58, -20), glow, 2);
        c.runLightUpdates();
        c.splitTo(d, (cx, cz) -> cx >= 1 && cz >= 1);
        c.broadcastChanges();
        d.broadcastChanges();
        assertTrue(c.light.size() > 0 && d.light.size() > 0, "양쪽이 자기 청크의 빛 갱신을 보낸다");
    }

    private int[] snapshot(Lvl l) {
        int[] out = new int[21 * 21 * 14];
        int n = 0;
        for (int x = -40; x <= 40; x += 4) for (int z = -40; z <= 40; z += 4) for (int y = -60; y < -46; y++) out[n++] = l.getBlockLight(new Pos(x, y, z));
        return out;
    }

    @Test
    void mergedResultEqualsSingleLevel() {
        // 틱 사이에 합친 두 레벨의 결과 == 한 레벨에서 같은 블록을 놓고 계산한 결과(바닐라는 엔진이 하나)
        java.util.Random r = new java.util.Random(7);
        List<Pos> ps = new ArrayList<>();
        for (int i = 0; i < 16; i++) ps.add(new Pos(r.nextInt(64) - 32, -59 + r.nextInt(6), r.nextInt(64) - 32));
        Lvl a = lit(), b = new Lvl();
        for (int i = 0; i < ps.size(); i++) (i % 2 == 0 ? a : b).setBlock(ps.get(i), glow, 2);
        a.absorb(b);
        a.runLightUpdates();
        int[] merged = snapshot(a);
        shared.clear();
        Lvl ref = lit();
        for (Pos p : ps) ref.setBlock(p, glow, 2);
        ref.runLightUpdates();
        org.junit.jupiter.api.Assertions.assertArrayEquals(snapshot(ref), merged);
    }
}
