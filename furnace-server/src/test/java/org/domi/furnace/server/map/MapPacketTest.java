// map_item_data(0x2C) 바이트 형식, 플레이어별 갱신 사각형·표식 갱신 주기(HoldingPlayer.nextUpdatePacket), 표식 환산(addDecoration).
package org.domi.furnace.server.map;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Predicate;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.net.protocol.play.MapItemData;
import org.junit.jupiter.api.Test;

class MapPacketTest {

    static byte[] bytes(MapItemData p) {
        ByteBuf b = Unpooled.buffer();
        p.write(b);
        byte[] out = new byte[b.readableBytes()];
        b.readBytes(out);
        return out;
    }

    static String hex(MapItemData p) { return HexFormat.of().formatHex(bytes(p)); }

    static class Viewer implements MapData.Viewer {
        double x, z;
        float yaw;
        String name = "Dom";
        public String name() { return name; }
        public boolean removed() { return false; }
        public String dimension() { return MapData.OVERWORLD; }
        public double x() { return x; }
        public double z() { return z; }
        public float yaw() { return yaw; }
        public boolean holdsMap(Predicate<ItemStack> p) { return true; }
    }

    @Test
    void packetBytesWithDecorationsAndPatch() {
        var deco = List.of(new MapData.Decoration(MapDecorationType.PLAYER, (byte) 10, (byte) -20, (byte) 3, null),
                new MapData.Decoration(MapDecorationType.MONUMENT, (byte) -128, (byte) 127, (byte) 31, null));
        var patch = new MapItemData.Patch(2, 3, 2, 2, new byte[] {1, 2, 3, 4});
        MapItemData p = new MapItemData(300, (byte) 2, true, deco, patch);
        assertEquals(0x2C, p.id());
        // 번호 300 = ac 02, 배율 02, 잠금 01, 표식 있음 01 + 개수 02,
        //   (종류 00, x 0a, y ec, 회전 03, 이름 없음 00), (종류 09, x 80, y 7f, 회전 0f(31&15), 이름 없음 00),
        //   색 조각: 너비 02 높이 02 x 02 z 03, 길이 04, 01 02 03 04
        assertEquals("ac0202" + "01" + "01" + "02" + "000aec0300" + "09807f0f00" + "02020203" + "04" + "01020304", hex(p));
    }

    @Test
    void absentPartsAreEncodedCompactly() {
        // 표식도 색도 없으면: 번호 00, 배율 00, 잠금 00, 표식 없음 00, 색 조각 없음 00
        assertEquals("0000000000", hex(new MapItemData(0, (byte) 0, false, null, null)));
        // 색만: 번호 05, 배율 01, 잠금 00, 표식 없음 00, 조각 — 너비 01 높이 01 x 00 z 00, 길이 01, 07
        assertEquals("05010000" + "01010000" + "01" + "07",
                hex(new MapItemData(5, (byte) 1, false, null, new MapItemData.Patch(0, 0, 1, 1, new byte[] {7}))));
    }

    @Test
    void firstPacketCarriesWholeMapThenOnlyChangedRectangles() {
        MapData d = MapData.createFresh(0, 0, (byte) 0, true, false, MapData.OVERWORLD);
        Viewer v = new Viewer();
        d.holding(v);
        MapItemData first = d.getUpdatePacket(7, v);
        assertNotNull(first.patch());
        assertEquals(0, first.patch().startX());
        assertEquals(128, first.patch().width());
        assertEquals(128, first.patch().height());
        assertEquals(128 * 128, first.patch().colors().length);
        assertNotNull(first.decorations(), "처음에는 표식 목록도 같이");
        assertNull(d.getUpdatePacket(7, v), "바뀐 게 없으면 보내지 않는다");
        // 두 칸이 바뀌면 그 둘을 감싸는 사각형만
        d.setColor(3, 4, (byte) 9);
        d.setColor(10, 8, (byte) 11);
        MapItemData second = d.getUpdatePacket(7, v);
        assertNull(second.decorations());
        var p = second.patch();
        assertEquals(3, p.startX());
        assertEquals(4, p.startY());
        assertEquals(8, p.width());   // x 3..10
        assertEquals(5, p.height());  // y 4..8
        assertEquals(9, p.colors()[0]);
        assertEquals(11, p.colors()[(8 - 4) * 8 + (10 - 3)]);
        assertNull(d.getUpdatePacket(7, v));
        // 다른 플레이어는 자기 몫을 따로 받는다(처음이라 전체)
        Viewer w = new Viewer();
        w.name = "Other";
        d.holding(w);
        assertEquals(128, d.getUpdatePacket(7, w).patch().width());
        // 등록 안 된 플레이어는 패킷이 없다
        assertNull(d.getUpdatePacket(7, new Viewer()));
    }

    @Test
    void decorationsAreResentOnlyEveryFifthCheck() {
        MapData d = MapData.createFresh(0, 0, (byte) 0, true, false, MapData.OVERWORLD);
        Viewer v = new Viewer();
        d.holding(v);
        assertNotNull(d.getUpdatePacket(1, v).decorations());   // 처음(틱 0)
        d.addDecoration(MapDecorationType.TARGET_X, null, "x", 10, 10, 180, null);
        // 바닐라: 표식이 더러워진 뒤에는 5 번째 확인에서야 나간다(j++ % 5 == 0, 더럽지 않을 때는 j 가 안 오른다)
        for (int i = 1; i <= 4; i++) assertNull(d.getUpdatePacket(1, v), "확인 " + i);
        MapItemData p = d.getUpdatePacket(1, v);
        assertNotNull(p);
        assertNull(p.patch());
        assertEquals(1, p.decorations().size());
    }

    @Test
    void decorationPlacementMatchesVanillaMath() {
        MapData d = MapData.createFresh(0, 0, (byte) 0, true, false, MapData.OVERWORLD);
        // 중심에서 (10, -20) 떨어진 플레이어, 시선 90 → x = (byte)(10*2+0.5) = 20, y = (byte)(-20*2+0.5) = -39, 회전 (90+8)*16/360 = 4
        d.addDecoration(MapDecorationType.PLAYER, null, "a", 10, -20, 90, null);
        var a = d.decorations().get("a");
        assertEquals(20, a.x());
        assertEquals(-39, a.y());
        assertEquals(4, a.rot());
        // 음수 회전은 -8 을 더한다: (-90-8)*16/360 = -4.35 → (byte)-4 → & 15 = 12
        d.addDecoration(MapDecorationType.PLAYER, null, "b", 0, 0, -90, null);
        assertEquals(12, d.decorations().get("b").rot());
        // 지도 밖(63 칸 넘음): 플레이어는 가장자리 표식, 320 안이면 PLAYER_OFF_MAP, 위치는 끝으로 붙는다
        d.addDecoration(MapDecorationType.PLAYER, null, "c", 200, 0, 0, null);
        var c = d.decorations().get("c");
        assertEquals(MapDecorationType.PLAYER_OFF_MAP, c.type());
        assertEquals(127, c.x());
        assertEquals(0, c.rot());
        // 무제한 추적이 아니면 320 넘게 멀면 지운다
        d.addDecoration(MapDecorationType.PLAYER, null, "c", 500, 0, 0, null);
        assertNull(d.decorations().get("c"));
        // 플레이어가 아닌 표식은 지도 밖이면 들어가지 않는다
        d.addDecoration(MapDecorationType.TARGET_X, null, "t", 200, 0, 0, null);
        assertNull(d.decorations().get("t"));
        MapData unlimited = MapData.createFresh(0, 0, (byte) 0, true, true, MapData.OVERWORLD);
        unlimited.addDecoration(MapDecorationType.PLAYER, null, "far", 1000, -1000, 0, null);
        var far = unlimited.decorations().get("far");
        assertEquals(MapDecorationType.PLAYER_OFF_LIMITS, far.type());
        assertEquals(127, far.x());
        assertEquals(-128, far.y());
    }

    @Test
    void trackedCountLimitCountsOnlyTrackedTypes() {
        MapData d = MapData.createFresh(0, 0, (byte) 0, true, false, MapData.OVERWORLD);
        d.addDecoration(MapDecorationType.PLAYER, null, "p", 1, 1, 0, null);
        d.addDecoration(MapDecorationType.TARGET_X, null, "t", 1, 1, 0, null);
        assertTrue(d.isTrackedCountOverLimit(1));
        assertFalse(d.isTrackedCountOverLimit(2), "target_x 는 세지 않는다");
        assertFalse(d.isExplorationMap());
        d.addDecoration(MapDecorationType.MONUMENT, null, "+", 5, 5, 180, null);
        assertTrue(d.isExplorationMap());
    }
}
