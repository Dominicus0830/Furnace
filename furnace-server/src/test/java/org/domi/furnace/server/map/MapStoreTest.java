// 지도 저장소: map_<id>.dat 바닐라 형식 왕복, idcounts 증가·재시작 뒤 이어서, 중심 격자 맞춤.
package org.domi.furnace.server.map;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MapStoreTest {

    @TempDir Path dir;

    @AfterEach
    void tearDown() { MapStore.uninstall(); }

    static NbtCompound readRoot(Path f) throws Exception {
        try (var in = Files.newInputStream(f)) { return NbtIo.readGzip(in); }
    }

    @Test
    void freshMapSnapsToTheScaleGrid() {
        // 배율 2: 한 장이 512 칸. (100, -300) → 격자 (0, -1) → 중심 (192, -320)
        MapData d = MapData.createFresh(100, -300, (byte) 2, true, false, MapData.OVERWORLD);
        assertEquals(192, d.centerX());
        assertEquals(-320, d.centerZ());
        // 배율 0: 128 칸 — 원점이면 중심 (0, 0), 64 를 넘으면 다음 칸
        MapData z = MapData.createFresh(0, 0, (byte) 0, true, false, MapData.OVERWORLD);
        assertEquals(0, z.centerX());
        assertEquals(0, z.centerZ());
        assertEquals(128, MapData.createFresh(64, 0, (byte) 0, true, false, MapData.OVERWORLD).centerX());
        assertEquals(-128, MapData.createFresh(-65, 0, (byte) 0, true, false, MapData.OVERWORLD).centerX());
    }

    @Test
    void idcountsStartAtZeroAndContinueAfterRestart() throws Exception {
        MapStore.install(dir);
        assertEquals(0, MapStore.nextId());
        assertEquals(1, MapStore.nextId());
        assertEquals(2, MapStore.nextId());
        MapStore.saveDirty();
        NbtCompound root = readRoot(dir.resolve("data/idcounts.dat"));
        assertEquals(2, root.getCompound("data").getInt("map"), "idcounts 의 map 은 마지막으로 준 번호");
        assertEquals(3955, root.getInt("DataVersion"));
        MapStore.uninstall();
        MapStore.install(dir);
        assertEquals(3, MapStore.nextId());
    }

    @Test
    void saveRoundTripUsesVanillaFormat() throws Exception {
        MapStore.install(dir);
        int id = MapStore.nextId();
        MapData d = MapData.createFresh(100, -300, (byte) 2, true, true, MapData.OVERWORLD);
        d.setColor(5, 6, (byte) 45);
        d.setColor(127, 127, (byte) -1);
        MapStore.put(id, d);
        MapStore.saveDirty();
        // 파일 모양 — {data:{dimension, xCenter, zCenter, scale(byte), colors[16384], trackingPosition, unlimitedTracking, locked, banners, frames}, DataVersion}
        NbtCompound root = readRoot(dir.resolve("data/map_" + id + ".dat"));
        NbtCompound data = root.getCompound("data");
        assertEquals("minecraft:overworld", data.getString("dimension"));
        assertEquals(192, data.getInt("xCenter"));
        assertEquals(-320, data.getInt("zCenter"));
        assertEquals((byte) 2, data.getByte("scale"));
        assertEquals(16384, data.getByteArray("colors").length);
        assertEquals(45, data.getByteArray("colors")[5 + 6 * 128]);
        assertEquals((byte) 1, data.getByte("trackingPosition"));
        assertEquals((byte) 1, data.getByte("unlimitedTracking"));
        assertEquals((byte) 0, data.getByte("locked"));
        assertEquals(0, data.getList("banners").size());
        assertEquals(0, data.getList("frames").size());
        assertEquals(3955, root.getInt("DataVersion"));
        // 다시 읽기
        MapStore.uninstall();
        MapStore.install(dir);
        MapData r = MapStore.get(id);
        assertNotNull(r);
        assertEquals(192, r.centerX());
        assertEquals(-320, r.centerZ());
        assertEquals(2, r.scale());
        assertTrue(r.trackingPosition());
        assertTrue(r.unlimitedTracking());
        assertFalse(r.locked());
        assertEquals(45, r.color(5, 6));
        assertEquals(-1, r.color(127, 127));
        assertNull(MapStore.get(id + 1), "파일이 없는 번호는 null");
        assertFalse(r.isDirty(), "읽은 직후는 저장할 것이 없다");
    }

    @Test
    void bannersAndFramesAreKept() throws Exception {
        NbtCompound data = new NbtCompound();
        data.putString("dimension", "minecraft:overworld");
        data.putInt("xCenter", 0);
        data.putInt("zCenter", 0);
        data.putByte("scale", (byte) 0);
        data.putByteArray("colors", new byte[16384]);
        NbtList banners = new NbtList((byte) 10);
        NbtCompound b = new NbtCompound();
        b.putIntArray("pos", new int[] {10, 70, -5});
        b.putString("color", "red");
        banners.add(b);
        data.putList("banners", banners);
        NbtList frames = new NbtList((byte) 10);
        NbtCompound f = new NbtCompound();
        f.putIntArray("pos", new int[] {3, 64, 4});
        f.putInt("rotation", 90);
        f.putInt("entity_id", 77);
        frames.add(f);
        data.putList("frames", frames);
        MapData d = MapData.load(data);
        // 깃발·액자는 읽을 때 표식으로 올라간다(깃발 red = banner_red, 액자 = frame)
        var decos = d.decorations();
        assertEquals(MapDecorationType.byName("banner_red"), decos.get("banner-10,70,-5").type());
        assertEquals(MapDecorationType.FRAME, decos.get("frame-77").type());
        assertNotNull(d.frame(3, 64, 4));
        NbtCompound out = d.save();
        assertEquals(1, out.getList("banners").size());
        assertEquals("red", ((NbtCompound) out.getList("banners").get(0)).getString("color"));
        assertEquals(77, ((NbtCompound) out.getList("frames").get(0)).getInt("entity_id"));
    }
}
