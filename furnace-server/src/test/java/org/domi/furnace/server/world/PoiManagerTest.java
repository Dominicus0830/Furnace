// POI: 블록 설치·제거 갱신, 저장 왕복(Sections NBT), 블록에서 복구, 조회 거리, 티켓 점유, 마을 근처 판정.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.HashMap;
import java.util.Map;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.domi.furnace.server.world.poi.PoiChunk;
import org.domi.furnace.server.world.poi.PoiManager;
import org.domi.furnace.server.world.poi.PoiManager.Occupancy;
import org.domi.furnace.server.world.poi.PoiTypes;
import org.junit.jupiter.api.Test;

class PoiManagerTest {

    private static final SuperflatWorldSource GEN = new SuperflatWorldSource(BlockRegistry.load("blocks/1.21.1.txt"), 0);

    static class Lvl extends ChunkLevel {
        final Map<Long, ChunkColumn> chunks = new HashMap<>();

        Lvl() { super(1000000); }

        @Override
        protected ChunkColumn chunk(int x, int z) {
            if (Math.abs(x) > 8 || Math.abs(z) > 8) return null;
            return chunks.computeIfAbsent((long) x << 32 | (z & 0xFFFFFFFFL), k -> GEN.generate(x, z));
        }

        @Override protected void markDirty(int x, int z) {}
        @Override protected void broadcastToChunk(int x, int z, Packet p) {}
        @Override protected LevelServices services() { return LevelServices.DEFAULT; }
    }

    private static final int BELL = B.defaultState("bell"), LECTERN = B.defaultState("lectern"), AIR = B.defaultState("air");

    @Test
    void placeAndRemoveUpdatesPoi() {
        Lvl l = new Lvl();
        Pos p = new Pos(3, -50, 4);
        l.setBlock(p, BELL, 3);
        assertEquals(PoiTypes.MEETING, l.poiManager().getType(p));
        assertEquals(32, l.poiManager().getFreeTickets(p));
        l.setBlock(p, LECTERN, 3);
        assertEquals(PoiTypes.LIBRARIAN, l.poiManager().getType(p));
        l.setBlock(p, AIR, 3);
        assertNull(l.poiManager().getType(p));
    }

    @Test
    void bedOnlyHeadIsHome() {
        var part = B.prop("part");
        int head = part.with(B.defaultState("red_bed"), "head");
        int foot = part.with(B.defaultState("red_bed"), "foot");
        assertSame(PoiTypes.HOME, PoiTypes.forState(head));
        assertNull(PoiTypes.forState(foot));
        assertSame(PoiTypes.LEATHERWORKER, PoiTypes.forState(B.defaultState("water_cauldron")));
        assertEquals(20, PoiTypes.all().size());
    }

    @Test
    void rangeIsDistSqr() {
        Lvl l = new Lvl();
        Pos center = new Pos(0, -50, 0);
        Pos a = new Pos(3, -50, 4);    // distSqr 25
        Pos b = new Pos(20, -50, 0);   // 다른 청크
        l.setBlock(a, LECTERN, 3);
        l.setBlock(b, LECTERN, 3);
        PoiManager pm = l.poiManager();
        assertEquals(0, pm.getInRange(t -> true, center, 4, Occupancy.ANY).size());
        assertEquals(1, pm.getInRange(t -> true, center, 5, Occupancy.ANY).size());
        assertEquals(2, pm.getInRange(t -> true, center, 20, Occupancy.ANY).size());
        assertEquals(a, pm.findClosest(t -> t == PoiTypes.LIBRARIAN, center, 32, Occupancy.ANY));
        // 사각 범위: 대각 (15,15)는 반경 16 사각 안이지만 원 밖
        l.setBlock(new Pos(15, -50, 15), BELL, 3);
        assertEquals(0, pm.getInRange(t -> t == PoiTypes.MEETING, center, 16, Occupancy.ANY).size());
        assertEquals(1, pm.getInSquare(t -> t == PoiTypes.MEETING, center, 16, Occupancy.ANY).size());
    }

    @Test
    void ticketsTakeAndRelease() {
        Lvl l = new Lvl();
        Pos p = new Pos(1, -50, 1);
        l.setBlock(p, LECTERN, 3);
        PoiManager pm = l.poiManager();
        Pos O = new Pos(0, -50, 0);
        assertEquals(p, pm.take(t -> t == PoiTypes.LIBRARIAN, (t, q) -> true, O, 8));
        assertEquals(0, pm.getFreeTickets(p));
        assertNull(pm.take(t -> t == PoiTypes.LIBRARIAN, (t, q) -> true, O, 8), "빈자리 없음");
        assertEquals(1, pm.getInRange(t -> true, O, 8, Occupancy.IS_OCCUPIED).size());
        assertEquals(0, pm.getInRange(t -> true, O, 8, Occupancy.HAS_SPACE).size());
        assertTrue(pm.release(p));
        assertFalse(pm.release(p), "이미 다 돌려받음");
        assertThrows(IllegalStateException.class, () -> pm.release(new Pos(2, -50, 2)));
    }

    @Test
    void saveRoundTripAndRestore() throws Exception {
        Lvl l = new Lvl();
        Pos p = new Pos(2, -50, 2);
        l.setBlock(p, BELL, 3);
        l.poiManager().take(t -> t == PoiTypes.MEETING, (t, q) -> true, p, 1);
        ChunkColumn col = l.chunk(0, 0);
        assertTrue(col.poi().dirty());
        NbtCompound root = col.poi().save(3955);
        assertEquals(3955, root.getInt("DataVersion"));
        NbtCompound sec = root.getCompound("Sections").getCompound("-4");
        assertEquals((byte) 1, sec.getByte("Valid"));
        var rec = (NbtCompound) sec.getList("Records").get(0);
        assertArrayEquals(new int[] {2, -50, 2}, (int[]) rec.tags().get("pos"));
        assertEquals("minecraft:meeting", rec.getString("type"));
        assertEquals(31, rec.getInt("free_tickets"));

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        NbtIo.write(new DataOutputStream(bos), root, "");
        NbtCompound back = NbtIo.read(new DataInputStream(new ByteArrayInputStream(bos.toByteArray())));
        PoiChunk again = PoiManager.restore(col, 0, 0, back);
        assertFalse(again.dirty(), "유효 섹션은 그대로");
        col.poi(again);
        assertEquals(31, l.poiManager().getFreeTickets(p));

        // 무효 섹션: 블록에서 다시 만들되 같은 칸의 티켓은 이어 쓴다. 블록이 없어진 기록은 버린다.
        sec = back.getCompound("Sections").getCompound("-4");
        sec.putBoolean("Valid", false);
        var ghost = new NbtCompound();
        ghost.putIntArray("pos", new int[] {9, -50, 9});
        ghost.putString("type", "minecraft:home");
        ghost.putInt("free_tickets", 1);
        sec.getList("Records").add(ghost);
        PoiChunk rebuilt = PoiManager.restore(col, 0, 0, back);
        col.poi(rebuilt);
        assertTrue(rebuilt.dirty());
        assertEquals(31, l.poiManager().getFreeTickets(p));
        assertNull(l.poiManager().getType(new Pos(9, -50, 9)));

        // 저장 없음: 블록에서 만든다
        PoiChunk fresh = PoiManager.restore(col, 0, 0, null);
        col.poi(fresh);
        assertEquals(32, l.poiManager().getFreeTickets(p));
    }

    @Test
    void closeToVillageBySectionDistance() {
        Lvl l = new Lvl();
        l.setBlock(new Pos(0, -50, 0), BELL, 3);   // 섹션 (0,-4,0)
        // 바닐라 isVillageCenter 는 점유된(IS_OCCUPIED) 마을 POI 만 센다 — 아무도 안 쓰는 종은 마을이 아니다
        assertFalse(l.isVillage(new Pos(0, -50, 0)));
        assertEquals(7, l.poiManager().sectionsToVillage(0, -4, 0));
        assertEquals(new Pos(0, -50, 0), l.poiManager().take(org.domi.furnace.server.world.poi.PoiTypes.VILLAGE, (t, q) -> true, new Pos(0, -50, 0), 1));
        assertTrue(l.isVillage(new Pos(17, -50, 0)));
        assertFalse(l.isVillage(new Pos(33, -50, 0)));
        assertTrue(l.isCloseToVillage(new Pos(33, -50, 33), 2));
        assertTrue(l.isCloseToVillage(new Pos(0, -50 + 32, 0), 2), "세로도 섹션 거리");
        assertFalse(l.isCloseToVillage(new Pos(48, -50, 0), 2));
        assertEquals(3, l.poiManager().sectionsToVillage(3, -4, 0));
        assertEquals(7, l.poiManager().sectionsToVillage(8, -4, 0));
        assertFalse(l.isCloseToVillage(Pos.ZERO, 7), "6 넘으면 거짓");
        l.poiManager().release(new Pos(0, -50, 0));
        assertFalse(l.isVillage(new Pos(0, -50, 0)), "놓으면 다시 마을이 아니다");
        // 마을 POI 가 아닌 종류(꿀벌집·피뢰침)는 세지 않는다
        Lvl l2 = new Lvl();
        l2.setBlock(new Pos(0, -50, 0), B.defaultState("lightning_rod"), 3);
        assertFalse(l2.isVillage(new Pos(0, -50, 0)));
    }
}
