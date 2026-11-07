// 플러그인 구조물 템플릿 API(W4/43번): 영역 저장 → 파일(.nbt) → 다시 놓기(회전·대칭·팔레트·완전성), 가져오기·내보내기, 내장 템플릿, 오류 상태.
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.api.nbt.NbtList;
import org.domi.furnace.api.structure.PlaceOptions;
import org.domi.furnace.api.structure.PlaceResult;
import org.domi.furnace.api.structure.SaveOptions;
import org.domi.furnace.api.structure.SaveResult;
import org.domi.furnace.api.structure.StructureMirror;
import org.domi.furnace.api.structure.StructureRotation;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StructuresApiTest {

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private PluginHost host;

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    @BeforeEach
    void setUp() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        host = core.plugins();
        core.world().setRegistries(org.domi.furnace.server.world.BlockRegistry.load("blocks/1.21.1.txt"), org.domi.furnace.server.world.ItemRegistry.load("items/1.21.1.txt"));
        for (int cz = 0; cz <= 2; cz++) {   // 시험이 직접 블록을 만지는 청크 (0,0..2) — 이웃 (1,0) 은 일부러 안 올린다
            ChunkPos c = new ChunkPos(0, cz);
            core.world().submitAtChunk(c, r -> r.chunkAtBlocking(c)).get(30, TimeUnit.SECONDS);
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        host.close();
        sched.close();
        TestServers.drainIoPools();
    }

    private org.domi.furnace.api.world.World api() { return host.world("minecraft:overworld"); }

    private void put(BlockPos p, String type) throws Exception {
        api().setBlock(p, BlockData.parse(type)).get(10, TimeUnit.SECONDS);   // 같은 블록이면 false 일 수 있다 — 올라온 청크인지는 아래 읽기에서 본다
    }

    private String typeAt(BlockPos p) throws Exception {
        return api().blockAt(p).get(10, TimeUnit.SECONDS).map(b -> b.data().toString()).orElse("<unloaded>");
    }

    private static final BlockPos BASE = new BlockPos(2, 10, 2);

    /** 4x1x2 영역: (0,0,0)=stone, (3,0,0)=gold_block, (1,0,1)=oak_stairs[facing=north], 나머지는 공기. */
    private void buildSample() throws Exception {
        for (int x = 0; x < 4; x++) for (int z = 0; z < 2; z++) put(BASE.offset(x, 0, z), "air");
        put(BASE, "stone");
        put(BASE.offset(3, 0, 0), "gold_block");
        put(BASE.offset(1, 0, 1), "oak_stairs[facing=north]");
    }

    @Test
    void saveWritesVanillaNbtAndPlaceRestores() throws Exception {
        var st = host.structures();
        buildSample();
        SaveResult saved = st.save(api(), "test:sample", BASE, 4, 1, 2, SaveOptions.DEFAULT.withAuthor("tester")).get(30, TimeUnit.SECONDS);
        assertEquals(SaveResult.Status.SAVED, saved.status());
        var info = saved.info();
        assertEquals("test:sample", info.id());
        assertEquals(4, info.sizeX());
        assertEquals(1, info.sizeY());
        assertEquals(2, info.sizeZ());
        assertEquals(8, info.blockCount());
        assertEquals(1, info.paletteCount());
        assertEquals("tester", info.author());
        assertTrue(info.onDisk());
        Path file = tmp.resolve("generated/test/structures/sample.nbt");
        assertTrue(Files.isRegularFile(file), "구조물 블록과 같은 자리에 쓴다");
        assertTrue(st.templates(api()).contains("test:sample"));
        assertTrue(st.hasTemplate(api(), "test:sample"));

        // 같은 자리에 다시 놓기 전에 지운다 — 놓은 뒤 되살아난 것만 센다
        for (int x = 0; x < 4; x++) for (int z = 0; z < 2; z++) put(BASE.offset(x, 0, z), "air");
        PlaceResult r = st.place(api(), "test:sample", BASE).get(30, TimeUnit.SECONDS);
        assertEquals(PlaceResult.Status.PLACED, r.status());
        assertEquals(BASE, r.min());
        assertEquals(BASE.offset(3, 0, 1), r.max());
        assertEquals("minecraft:stone", typeAt(BASE));
        assertEquals("minecraft:gold_block", typeAt(BASE.offset(3, 0, 0)));
        assertTrue(typeAt(BASE.offset(1, 0, 1)).startsWith("minecraft:oak_stairs"), typeAt(BASE.offset(1, 0, 1)));
        assertTrue(typeAt(BASE.offset(1, 0, 1)).contains("facing=north"));
    }

    @Test
    void placeRotatedAndMirrored() throws Exception {
        var st = host.structures();
        buildSample();
        assertTrue(st.save(api(), "test:sample", BASE, 4, 1, 2).get(30, TimeUnit.SECONDS).saved());
        BlockPos at = new BlockPos(6, 10, 6);
        // 시계 90도: 로컬 (x, z) → (-z, x). (3,0,0) 금 → at+(0,0,3), 상자는 (-1..0, 0..3)
        PlaceResult r = st.place(api(), "test:sample", at, PlaceOptions.DEFAULT.withRotation(StructureRotation.CLOCKWISE_90)).get(30, TimeUnit.SECONDS);
        assertEquals(PlaceResult.Status.PLACED, r.status());
        assertEquals(at.offset(-1, 0, 0), r.min());
        assertEquals(at.offset(0, 0, 3), r.max());
        assertEquals("minecraft:stone", typeAt(at));
        assertEquals("minecraft:gold_block", typeAt(at.offset(0, 0, 3)));
        // 계단 방향도 함께 돈다(북 → 동)
        assertTrue(typeAt(at.offset(-1, 0, 1)).contains("facing=east"), typeAt(at.offset(-1, 0, 1)));

        // 좌우 대칭(z 뒤집기): (3,0,0) 금 → at2+(3,0,0), 계단 (1,0,1) → (1,0,-1)
        BlockPos at2 = new BlockPos(6, 10, 4 + 16);   // 다른 청크쪽(z=20)에 놓아 앞 결과와 안 겹치게
        PlaceResult m = st.place(api(), "test:sample", at2, PlaceOptions.DEFAULT.withMirror(StructureMirror.LEFT_RIGHT)).get(30, TimeUnit.SECONDS);
        assertEquals(PlaceResult.Status.PLACED, m.status());
        assertEquals("minecraft:gold_block", typeAt(at2.offset(3, 0, 0)));
        assertTrue(typeAt(at2.offset(1, 0, -1)).startsWith("minecraft:oak_stairs"), typeAt(at2.offset(1, 0, -1)));
    }

    @Test
    void placeAcrossChunkBorderLoadsTheNeighbours() throws Exception {
        var st = host.structures();
        buildSample();
        assertTrue(st.save(api(), "test:sample", BASE, 4, 1, 2).get(30, TimeUnit.SECONDS).saved());
        BlockPos at = new BlockPos(14, 10, 2);   // x 14..17 — 청크 (0,0)·(1,0) 에 걸친다
        assertFalse(api().isChunkLoaded(1, 0));
        PlaceResult r = st.place(api(), "test:sample", at).get(30, TimeUnit.SECONDS);
        assertEquals(PlaceResult.Status.PLACED, r.status());
        assertEquals("minecraft:gold_block", typeAt(at.offset(3, 0, 0)));   // x=17, 이웃 청크
        assertTrue(api().isChunkLoaded(1, 0));
    }

    @Test
    void importExportRoundTrip() throws Exception {
        var st = host.structures();
        buildSample();
        assertTrue(st.save(api(), "test:sample", BASE, 4, 1, 2).get(30, TimeUnit.SECONDS).saved());
        NbtCompound nbt = st.exportTemplate(api(), "test:sample").orElseThrow();
        assertEquals(3955, nbt.getInt("DataVersion"));
        NbtList size = nbt.getList("size");
        assertEquals(4, size.getInt(0));
        assertEquals(2, size.getInt(2));
        assertEquals(8, nbt.getList("blocks").size());
        byte[] file = st.exportTemplateFile(api(), "test:sample").orElseThrow();
        assertEquals(Files.size(tmp.resolve("generated/test/structures/sample.nbt")), file.length, "파일과 같은 내용(같은 직렬화)");

        // 다른 이름으로 들여와(디스크에 안 쓰고) 놓는다
        assertTrue(st.importTemplate(api(), "other:copy", nbt, false));
        assertFalse(Files.exists(tmp.resolve("generated/other/structures/copy.nbt")));
        assertEquals(java.util.Optional.empty(), java.util.Optional.ofNullable(st.templates(api()).contains("other:copy") ? "listed" : null), "파일이 없으면 목록(디스크)에는 없다");
        assertTrue(st.hasTemplate(api(), "other:copy"));
        assertFalse(st.templateInfo(api(), "other:copy").orElseThrow().onDisk());
        BlockPos at = new BlockPos(8, 10, 40);
        assertTrue(st.place(api(), "other:copy", at).get(30, TimeUnit.SECONDS).placed());
        assertEquals("minecraft:gold_block", typeAt(at.offset(3, 0, 0)));

        // gzip 파일 판 + 디스크 쓰기
        assertTrue(st.importTemplateFile(api(), "other:fromfile", file, true));
        assertTrue(Files.isRegularFile(tmp.resolve("generated/other/structures/fromfile.nbt")));
        assertTrue(st.templates(api()).contains("other:fromfile"));
        assertThrows(IllegalArgumentException.class, () -> st.importTemplateFile(api(), "other:bad", new byte[] {1, 2, 3}, false));
        assertThrows(IllegalArgumentException.class, () -> st.importTemplate(api(), "other:bad", new NbtCompound(), false));
        assertThrows(IllegalArgumentException.class, () -> st.importTemplate(api(), "Bad Name", nbt, false));

        // 지우기: 메모리와 파일, 데이터 팩·내장은 못 지움
        assertTrue(st.deleteTemplate(api(), "other:fromfile"));
        assertFalse(Files.exists(tmp.resolve("generated/other/structures/fromfile.nbt")));
        assertFalse(st.hasTemplate(api(), "other:fromfile"));
        assertFalse(st.deleteTemplate(api(), "other:fromfile"));
        assertTrue(st.deleteTemplate(api(), "other:copy"), "메모리만 있던 것도 지운다");
        assertFalse(st.hasTemplate(api(), "other:copy"));
    }

    @Test
    void builtinTemplateCanBePlaced() throws Exception {
        var st = host.structures();
        var info = st.templateInfo(api(), "minecraft:igloo/top").orElseThrow();
        assertFalse(info.onDisk());
        assertTrue(info.blockCount() > 0);
        BlockPos at = new BlockPos(4, 20, 4);
        PlaceResult r = st.place(api(), "igloo/top", at).get(30, TimeUnit.SECONDS);   // minecraft: 생략
        assertEquals(PlaceResult.Status.PLACED, r.status());
        int solid = 0;
        for (int x = 0; x < info.sizeX(); x++) for (int y = 0; y < info.sizeY(); y++) for (int z = 0; z < info.sizeZ(); z++) {
            if (!typeAt(at.offset(x, y, z)).startsWith("minecraft:air")) solid++;
        }
        assertTrue(solid > 10, "igloo 가 놓였다: " + solid);
    }

    @Test
    void integrityDropsBlocks() throws Exception {
        var st = host.structures();
        for (int x = 0; x < 16; x++) for (int z = 0; z < 2; z++) put(new BlockPos(0 + x, 30, 2 + z), "stone");
        assertTrue(st.save(api(), "test:slab", new BlockPos(0, 30, 2), 16, 1, 2).get(30, TimeUnit.SECONDS).saved());
        BlockPos at = new BlockPos(0, 50, 20);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 2; z++) put(at.offset(x, 0, z), "air");
        assertEquals(PlaceResult.Status.PLACED, st.place(api(), "test:slab", at, PlaceOptions.DEFAULT.withIntegrity(0.5F).withSeed(42L)).get(30, TimeUnit.SECONDS).status());
        int stone = 0;
        for (int x = 0; x < 16; x++) for (int z = 0; z < 2; z++) if (typeAt(at.offset(x, 0, z)).equals("minecraft:stone")) stone++;
        assertTrue(stone > 0 && stone < 32, "일부만 놓였다: " + stone);
        // 같은 시드는 같은 결과
        BlockPos at2 = new BlockPos(0, 50, 40);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 2; z++) put(at2.offset(x, 0, z), "air");
        assertEquals(PlaceResult.Status.PLACED, st.place(api(), "test:slab", at2, PlaceOptions.DEFAULT.withIntegrity(0.5F).withSeed(42L)).get(30, TimeUnit.SECONDS).status());
        for (int x = 0; x < 16; x++) for (int z = 0; z < 2; z++) assertEquals(typeAt(at.offset(x, 0, z)), typeAt(at2.offset(x, 0, z)));
        assertThrows(IllegalArgumentException.class, () -> PlaceOptions.DEFAULT.withIntegrity(1.5F));
    }

    @Test
    void paletteChoice() throws Exception {
        var st = host.structures();
        // 팔레트 둘(돌/금)인 1x1x1 템플릿
        NbtCompound root = new NbtCompound();
        NbtList size = new NbtList();
        size.add(1); size.add(1); size.add(1);
        root.put("size", size);
        NbtList palettes = new NbtList();
        for (String name : new String[] {"minecraft:stone", "minecraft:gold_block"}) {
            NbtList p = new NbtList();
            NbtCompound e = new NbtCompound();
            e.putString("Name", name);
            p.add(e);
            palettes.add(p);
        }
        root.put("palettes", palettes);
        NbtList blocks = new NbtList();
        NbtCompound b = new NbtCompound();
        NbtList pos = new NbtList();
        pos.add(0); pos.add(0); pos.add(0);
        b.put("pos", pos);
        b.putInt("state", 0);
        blocks.add(b);
        root.put("blocks", blocks);
        root.put("entities", new NbtList());
        root.putInt("DataVersion", 3955);
        assertTrue(st.importTemplate(api(), "test:two", root, false));
        assertEquals(2, st.templateInfo(api(), "test:two").orElseThrow().paletteCount());
        BlockPos at = new BlockPos(4, 60, 4);
        assertTrue(st.place(api(), "test:two", at, PlaceOptions.DEFAULT.withPalette(1)).get(30, TimeUnit.SECONDS).placed());
        assertEquals("minecraft:gold_block", typeAt(at));
        assertTrue(st.place(api(), "test:two", at, PlaceOptions.DEFAULT.withPalette(0)).get(30, TimeUnit.SECONDS).placed());
        assertEquals("minecraft:stone", typeAt(at));
        assertEquals(PlaceResult.Status.INVALID_PALETTE, st.place(api(), "test:two", at, PlaceOptions.DEFAULT.withPalette(2)).get(30, TimeUnit.SECONDS).status());
    }

    @Test
    void errorStatuses() throws Exception {
        var st = host.structures();
        var w = api();
        assertEquals(PlaceResult.Status.TEMPLATE_NOT_FOUND, st.place(w, "test:nope", BASE).get(30, TimeUnit.SECONDS).status());
        assertEquals(PlaceResult.Status.TEMPLATE_NOT_FOUND, st.place(w, "Bad Name", BASE).get(30, TimeUnit.SECONDS).status());
        assertTrue(st.templateInfo(w, "test:nope").isEmpty());
        assertTrue(st.exportTemplate(w, "test:nope").isEmpty());

        assertEquals(SaveResult.Status.INVALID_NAME, st.save(w, "Bad Name", BASE, 2, 2, 2).get(30, TimeUnit.SECONDS).status());
        assertEquals(SaveResult.Status.INVALID_SIZE, st.save(w, "test:x", BASE, 0, 2, 2).get(30, TimeUnit.SECONDS).status());
        assertEquals(SaveResult.Status.INVALID_SIZE, st.save(w, "test:x", BASE, 49, 2, 2).get(30, TimeUnit.SECONDS).status());
        assertEquals(SaveResult.Status.OUT_OF_WORLD, st.save(w, "test:x", new BlockPos(0, 1000, 0), 2, 2, 2).get(30, TimeUnit.SECONDS).status());

        buildSample();
        assertTrue(st.save(w, "test:sample", BASE, 4, 1, 2).get(30, TimeUnit.SECONDS).saved());
        assertEquals(PlaceResult.Status.OUT_OF_WORLD, st.place(w, "test:sample", new BlockPos(0, 5000, 0)).get(30, TimeUnit.SECONDS).status());

        // 한 번에 놓을 수 있는 크기(19x19 청크)를 넘는 템플릿
        NbtCompound big = st.exportTemplate(w, "test:sample").orElseThrow();
        NbtList size = new NbtList();
        size.add(400); size.add(1); size.add(1);
        big.put("size", size);
        assertTrue(st.importTemplate(w, "test:big", big, false));
        assertEquals(PlaceResult.Status.TOO_LARGE, st.place(w, "test:big", BASE).get(30, TimeUnit.SECONDS).status());
    }

    @Test
    void savedTemplateIsSharedWithStructureBlocksAndPlaceCommand() throws Exception {
        // 구조물 블록·/place template 과 같은 저장소 — 같은 매니저가 같은 이름을 돌려준다
        var st = host.structures();
        buildSample();
        assertTrue(st.save(api(), "test:shared", BASE, 4, 1, 2, SaveOptions.DEFAULT.withToDisk(false)).get(30, TimeUnit.SECONDS).saved());
        var mgr = org.domi.furnace.server.world.gen.structure.template.TemplateManager.forWorld(core.world().worldDir(), core.world().blockRegistry());
        assertTrue(mgr.get(org.domi.furnace.server.data.ResourceId.parse("test:shared")).isPresent());
        assertFalse(Files.exists(tmp.resolve("generated/test/structures/shared.nbt")), "toDisk=false 면 파일을 안 쓴다");
        assertEquals(List.of(), st.templates(api()).stream().filter(s -> s.equals("test:shared")).toList());
    }
}
