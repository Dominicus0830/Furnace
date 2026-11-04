// region 틱에서 지도를 든 플레이어: 로드된 청크 둘레가 칠해지고, 갱신 패킷(map_item_data 0x2C)은 바뀔 때만 나간다.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.PlayerInventory;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.map.MapColor;
import org.domi.furnace.server.map.MapData;
import org.domi.furnace.server.map.MapItems;
import org.domi.furnace.server.map.MapStore;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MapRegionTest {

    @TempDir Path dir;

    @BeforeAll
    static void install() { ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt"))); }

    @AfterEach
    void tearDown() { MapStore.uninstall(); }

    private static int mapPackets(EmbeddedChannel ch, int[] firstBody) {
        ch.runPendingTasks();
        int n = 0;
        Object o;
        while ((o = ch.readOutbound()) != null) {
            ByteBuf bb = (ByteBuf) o;
            if (Types.readVarInt(bb) == 0x2C) {
                if (n == 0 && firstBody != null) {
                    firstBody[0] = Types.readVarInt(bb);   // 지도 번호
                    firstBody[1] = bb.readByte();          // 배율
                    firstBody[2] = bb.readByte();          // 잠금
                    firstBody[3] = bb.readByte();          // 표식 있음
                    int count = Types.readVarInt(bb);
                    firstBody[4] = count;
                    for (int i = 0; i < count; i++) {
                        Types.readVarInt(bb);              // 종류
                        bb.skipBytes(3);                   // x, y, 회전
                        bb.skipBytes(1);                   // 이름 없음
                    }
                    firstBody[5] = bb.readUnsignedByte();  // 조각 너비
                    firstBody[6] = bb.readUnsignedByte();  // 높이
                }
                n++;
            }
            bb.release();
        }
        return n;
    }

    @Test
    void playerHoldingAMapPaintsLoadedChunksAndGetsPacketsOnlyOnChange() throws Exception {
        MapStore.install(dir);
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        var io = Executors.newSingleThreadExecutor();
        World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1),
                sched, sched.regionThreadCount(), 16, 5, 5, reg.directBits(), 7, io);
        RegionPos rp = new RegionPos(0, 0);
        EmbeddedChannel ch = new ConcurrentEmbeddedChannel();
        Player p = new Player(w.entityIds().next(), UUID.randomUUID(), "A", ch);
        p.inventory(new PlayerInventory());
        p.applyMove(0.5, -60, 0.5, 0, 0, true);
        ItemStackHolder map = new ItemStackHolder();
        map.stack = MapItems.create("minecraft:overworld", 0, 0, (byte) 0, true, false);
        p.inventory().set(PlayerInventory.windowSlot(0), map.stack);   // 선택한 핫바 0 번
        int[] body = new int[7];
        int[] counts = new int[2];
        sched.region(rp).execute(() -> {
            Region r = w.regionAt(rp);
            r.adopt(p);
            r.chunkAtBlocking(new ChunkPos(0, 0));
            for (int i = 0; i < 20; i++) r.tick();
            counts[0] = mapPackets(ch, body);
            for (int i = 0; i < 40; i++) r.tick();   // 더 바뀔 것이 없다
            counts[1] = mapPackets(ch, null);
        }).get();
        MapData d = MapStore.of(map.stack);
        // 청크 (0,0) = 월드 x,z 0..15 = 지도 칸 64..79 가 칠해진다(슈퍼플랫 맨 위 층 = 잔디 블록, 색 번호 1). 첫 표본만 높이 차(-61)라 LOW, 나머지 NORMAL
        assertEquals(1, d.color(70, 70) >> 2, "잔디 색 번호");
        assertEquals(MapColor.NORMAL, d.color(70, 70) & 3);
        assertEquals(MapColor.LOW, d.color(70, 64) & 3);
        assertEquals(0, d.color(10, 10), "로드되지 않은 곳은 비어 있다");
        assertEquals(0, d.color(80, 70), "다음 청크도 이 region 에 로드되지 않았다");
        // 첫 패킷: 지도 0, 배율 0, 잠금 아님, 표식 있음(플레이어 자신 하나), 전체 128x128
        assertEquals(0, body[0]);
        assertEquals(0, body[1]);
        assertEquals(0, body[2]);
        assertEquals(1, body[3]);
        assertEquals(1, body[4]);
        assertEquals(128, body[5]);
        assertEquals(128, body[6]);
        assertTrue(counts[0] >= 2 && counts[0] <= 20, "칠해지는 동안 바뀐 부분만 나간다: " + counts[0]);
        assertEquals(0, counts[1], "더 바뀔 게 없으면 패킷이 없다");
        assertEquals(1, d.decorations().size());
        assertEquals(org.domi.furnace.server.map.MapDecorationType.PLAYER, d.decorations().get("A").type());
        io.shutdown();
        sched.close();
    }

    private static final class ItemStackHolder { org.domi.furnace.server.entity.ItemStack stack; }
}
