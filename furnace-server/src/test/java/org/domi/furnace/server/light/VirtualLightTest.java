// 가짜 광원(W6): 같은 밝기의 빛 블록(minecraft:light)을 둔 것과 칸마다 같은 블록빛, 지우면 원래대로, 다시 올라온 청크·주인별 최댓값·저장 판정.
package org.domi.furnace.server.light;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.play.LightUpdate;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class VirtualLightTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new org.domi.furnace.server.data.TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    static final class Level extends BlockPlacementTest.TestLevel {
        final List<LightUpdate> light = new ArrayList<>();
        final java.util.Set<Long> unloaded = new java.util.HashSet<>();

        @Override
        protected org.domi.furnace.server.world.ChunkColumn chunk(int x, int z) {
            return unloaded.contains(VirtualLights.chunkKey(x, z)) ? null : super.chunk(x, z);
        }
        @Override protected void broadcastLightToChunk(int x, int z, Packet p) { light.add((LightUpdate) p); }

        void lightAll() {
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) lightNewChunk(x, z);
            runLightUpdates();
            broadcastChanges();
            light.clear();
        }
    }

    private static final BlockRegistry REG = BlockRegistry.load("blocks/1.21.1.txt");

    private static int lightBlock(int level) { return REG.stateIdOrAir("minecraft:light", java.util.Map.of("level", String.valueOf(level), "waterlogged", "false")); }

    private static void assertSameBlockLight(Level a, Level b, String what) {
        for (int x = -24; x < 24; x++) for (int z = -24; z < 24; z++) for (int y = -64; y < -30; y++) {
            assertEquals(b.lightEngine().blockLight(x, y, z), a.lightEngine().blockLight(x, y, z), what + " " + x + "," + y + "," + z);
        }
    }

    @Test
    void virtualSource_matchesLightBlock_andRemovalRestores() {
        Level fresh = new Level();
        fresh.lightAll();
        int stone = REG.defaultStateId("minecraft:stone");
        for (int lvl : new int[] {15, 7, 1}) {
            Level real = new Level();
            real.lightAll();
            Level fake = new Level();
            fake.lightAll();
            // 벽 하나로 빛을 막아 가리기도 함께 본다(청크 경계 x=0 걸침)
            for (int y = -60; y < -50; y++) for (int z = -5; z < 5; z++) {
                real.setBlock(new Pos(3, y, z), stone, 3);
                fake.setBlock(new Pos(3, y, z), stone, 3);
            }
            real.setBlock(new Pos(0, -58, 0), lightBlock(lvl), 3);
            real.runLightUpdates();
            assertTrue(fake.setVirtualLight("p", 0, -58, 0, lvl));
            assertEquals(lvl, fake.lightEngine().blockLight(0, -58, 0));
            assertSameBlockLight(fake, real, "level " + lvl);

            // 지우면 벽만 있는 세계와 같다
            Level wallOnly = new Level();
            wallOnly.lightAll();
            for (int y = -60; y < -50; y++) for (int z = -5; z < 5; z++) wallOnly.setBlock(new Pos(3, y, z), stone, 3);
            wallOnly.runLightUpdates();
            assertTrue(fake.setVirtualLight("p", 0, -58, 0, 0));
            assertSameBlockLight(fake, wallOnly, "removed " + lvl);
        }
        assertEquals(0, fresh.lightEngine().blockLight(0, -58, 0));
    }

    @Test
    void ownersStack_maxWins() {
        Level l = new Level();
        l.lightAll();
        assertTrue(l.setVirtualLight("a", 5, -55, 5, 8));
        assertTrue(l.setVirtualLight("b", 5, -55, 5, 12));
        assertEquals(12, l.lightEngine().blockLight(5, -55, 5));
        assertFalse(l.setVirtualLight("a", 5, -55, 5, 10), "더 밝은 주인이 있으면 실제 발광은 그대로");
        assertTrue(l.setVirtualLight("b", 5, -55, 5, 0));
        assertEquals(10, l.lightEngine().blockLight(5, -55, 5));
        assertEquals(10, l.virtualLights().emission(5, -55, 5));
        assertTrue(l.setVirtualLight("a", 5, -55, 5, 0));
        assertEquals(0, l.lightEngine().blockLight(5, -55, 5));
        assertTrue(l.virtualLights().isEmpty());
        assertFalse(l.setVirtualLight("a", 5, -55, 5, 0), "없는 것을 지우면 false");
    }

    @Test
    void outOfHeightOrUnloaded_isRejected() {
        Level l = new Level();
        l.lightAll();
        assertFalse(l.setVirtualLight("p", 0, 400, 0, 10));
        assertFalse(l.setVirtualLight("p", 100, -58, 0, 10));   // TestLevel 은 청크 ±2 만 있다
        assertTrue(l.virtualLights().isEmpty());
    }

    @Test
    void sourceInBlockingBlock_staysInside_likeEmissiveSolid() {
        Level l = new Level();
        l.lightAll();
        // 기반암 안에 둔 광원: 그 칸만 밝고(저장값) 막힌 면을 못 지난다 — 바닥 아래는 기반암뿐이라 위쪽만 본다
        assertTrue(l.setVirtualLight("p", 2, -64, 2, 15));
        assertEquals(15, l.lightEngine().blockLight(2, -64, 2));
        assertEquals(0, l.lightEngine().blockLight(2, -63, 2), "흙은 빛을 막는다");
    }

    @Test
    void reloadedChunk_reappliesSource_andNeighbourEdgeIsBroadcast() {
        Level l = new Level();
        // 광원은 표에만 먼저 있고(이전에 내려간 청크) 이제 청크를 켠다
        l.virtualLights().set("p", 15, -58, 8, 14);
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) if (!(x == 0 && z == 0)) l.lightNewChunk(x, z);
        l.runLightUpdates();
        l.broadcastChanges();
        l.light.clear();
        l.lightNewChunk(0, 0);
        l.broadcastChanges();
        assertEquals(14, l.lightEngine().blockLight(15, -58, 8));
        assertEquals(13, l.lightEngine().blockLight(16, -58, 8), "이웃 청크로 번진다");
        assertTrue(l.light.stream().anyMatch(u -> u.chunkX() == 1 && u.chunkZ() == 0), "이웃 청크 빛 갱신은 보낸다");
        assertTrue(l.light.stream().noneMatch(u -> u.chunkX() == 0 && u.chunkZ() == 0), "새 청크 자신은 첫 빛이라 보내지 않는다");

        Level ref = new Level();
        ref.lightAll();
        ref.setBlock(new Pos(15, -58, 8), lightBlock(14), 3);
        ref.runLightUpdates();
        assertSameBlockLight(l, ref, "reloaded");
    }

    @Test
    void precomputedChunk_reappliesSource() {
        Level l = new Level();
        l.virtualLights().set("p", 8, -58, 8, 15);
        var col = l.column(0, 0);
        org.domi.furnace.server.block.ChunkLevel.precomputeLight(col, 0, 0, l.lightBedrockState(), true);
        l.lightAll();
        assertEquals(15, l.lightEngine().blockLight(8, -58, 8));
        assertEquals(14, l.lightEngine().blockLight(9, -58, 8));
    }

    @Test
    void setVirtualLight_marksChangedSectionsForBroadcast() {
        Level l = new Level();
        l.lightAll();
        assertTrue(l.setVirtualLight("p", 8, -40, 8, 15));
        l.broadcastChanges();
        LightUpdate u = l.light.stream().filter(p -> p.chunkX() == 0 && p.chunkZ() == 0).findFirst().orElseThrow();
        int idx = (-40 >> 4) - (l.minBuildHeight() >> 4) + 1;
        assertNotEquals(0, u.blockMask() & 1L << idx);
    }

    @Test
    void removedWhileUnloaded_isClearedFromNeighbourOnReload() {
        Level l = new Level();
        l.lightAll();
        assertTrue(l.setVirtualLight("p", 15, -58, 8, 14));
        assertEquals(13, l.lightEngine().blockLight(16, -58, 8));
        var a = l.column(0, 0);
        l.unloaded.add(VirtualLights.chunkKey(0, 0));   // 청크 (0,0) 이 내려감 — 이웃 (1,0) 에는 번진 빛이 남아 있다
        assertTrue(l.virtualLights().retire("p", 15, -58, 8));
        assertEquals(14, l.virtualLights().emission(15, -58, 8), "다시 올라올 때까지는 발광이 남는다");
        assertEquals(0, l.virtualLights().ownLevel("p", 15, -58, 8));
        a.light(null);
        l.unloaded.clear();
        l.lightNewChunk(0, 0);
        l.runLightUpdates();
        assertTrue(l.virtualLights().isEmpty());
        Level fresh = new Level();
        fresh.lightAll();
        assertSameBlockLight(l, fresh, "retired");
    }

    @Test
    void lightChange_dropsCachedChunkBodyOfNeighbour() {
        Level l = new Level();
        l.lightAll();
        var b = l.column(1, 0);
        b.cachedSerializedBody(new byte[1]);
        l.column(0, 0).cachedSerializedBody(new byte[1]);
        long before = b.modCount();
        assertTrue(l.setVirtualLight("p", 15, -58, 8, 14));   // (1,0) 로 번진다
        assertNull(b.cachedSerializedBody(), "빛이 바뀐 이웃 청크의 캐시된 청크 패킷은 버린다");
        assertNull(l.column(0, 0).cachedSerializedBody());
        assertTrue(b.modCount() > before);
        var far = l.column(-2, -2);
        far.cachedSerializedBody(new byte[1]);
        assertTrue(l.setVirtualLight("p", 15, -58, 8, 0));
        assertNotNull(far.cachedSerializedBody(), "빛이 안 바뀐 청크는 그대로");
    }

    @Test
    void affects_coversNeighbourChunksOnly() {
        VirtualLights v = new VirtualLights();
        assertFalse(v.affects(0, 0));
        v.set("p", 20, 0, 20, 5);   // 청크 (1,1)
        assertTrue(v.affects(1, 1));
        assertTrue(v.affects(0, 0));
        assertTrue(v.affects(2, 2));
        assertFalse(v.affects(3, 1));
        assertEquals(List.of(LightEngine.pack(20, 0, 20)), v.sourcesOf("p"));
        assertTrue(v.sourcesOf("q").isEmpty());
        v.set("p", 20, 0, 20, 0);
        assertFalse(v.affects(1, 1));
    }
}
