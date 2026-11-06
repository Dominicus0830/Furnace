// 구조물 블록: 블록 엔티티 NBT(기본값·한계·이름), 저장 → 파일 → 불러오기(블록 엔티티·회전·완전성), 모서리로 크기 감지, 모드와 블록 상태, 신호 동작.
// 직소 블록 엔티티 NBT. region 스레드에서 실제 슈퍼플랫 월드로 돌린다.
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.block.entity.CommandBlockEntity;
import org.domi.furnace.server.block.entity.JigsawBlockEntity;
import org.domi.furnace.server.block.entity.StructureBlockEntity;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.world.DimensionType;
import org.domi.furnace.server.world.RegionLevel;
import org.domi.furnace.server.world.World;
import org.domi.furnace.server.world.anvil.AnvilChunkCodec;
import org.domi.furnace.server.world.anvil.RegionFileStore;
import org.domi.furnace.server.world.gen.AnvilWorldSource;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StructureBlockTest {

    @BeforeAll
    static void items() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private java.util.concurrent.ExecutorService io;
    private World world;
    private final ChunkPos cp = new ChunkPos(0, 0);

    @AfterEach
    void close() throws InterruptedException {
        if (sched != null) sched.close();
        if (io != null) {
            io.shutdown();
            io.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private void start() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        io = org.domi.furnace.server.TestServers.ioPool();
        DimensionType ow = DimensionType.OVERWORLD;
        var src = new AnvilWorldSource(tmp, new RegionFileStore(ow.storageDir(tmp)), new AnvilChunkCodec(reg, 0), new SuperflatWorldSource(reg, 0));
        world = new World(ow, src, new org.domi.furnace.server.net.ChunkWorkerPool(1), sched, 1, 4, 1, 2, 2, reg.directBits(), 7, io, io);
        world.configurePersistence(Integer.MAX_VALUE, 100);
        world.worldDir(tmp);
        world.setRegistries(reg, null);
        world.submitAtChunk(cp, r -> r.loadChunk(cp)).get(10, TimeUnit.SECONDS).get(10, TimeUnit.SECONDS);
    }

    private <T> T run(java.util.function.Function<RegionLevel, T> f) throws Exception {
        return world.submitAtChunk(cp, r -> f.apply(r.level())).get(30, TimeUnit.SECONDS);
    }

    private static int state(String name) { return B.defaultState(B.id(name)); }

    // ------------------------------------------------------------------ NBT

    @Test
    void structureBlockEntityNbtDefaultsClampAndNames() {
        var be = new StructureBlockEntity(new Pos(0, 64, 0), state("structure_block"));
        NbtCompound t = be.saveCustomOnly();
        assertEquals("", t.getString("name"));
        assertEquals("LOAD", t.getString("mode"), "기본 블록 상태가 mode=load");
        assertEquals("NONE", t.getString("rotation"));
        assertEquals("NONE", t.getString("mirror"));
        assertEquals(1, t.getInt("posY"));
        assertTrue(t.getBoolean("ignoreEntities"));
        assertTrue(t.getBoolean("showboundingbox"));
        assertFalse(t.getBoolean("showair"));
        assertEquals(1.0F, t.getFloat("integrity"));

        NbtCompound in = new NbtCompound();
        in.putString("name", "Bad Name");
        in.putInt("posX", 999);
        in.putInt("posY", -999);
        in.putInt("sizeX", 999);
        in.putInt("sizeY", -5);
        in.putString("rotation", "CLOCKWISE_90");
        in.putString("mirror", "oops");
        in.putString("mode", "LOAD");
        in.putFloat("integrity", 0.5F);
        in.putLong("seed", 77L);
        be.load(in);
        assertFalse(be.hasStructureName(), "올바르지 않은 이름은 이름 없음");
        assertEquals(48, be.structurePos()[0]);
        assertEquals(-48, be.structurePos()[1]);
        assertEquals(48, be.structureSize()[0]);
        assertEquals(0, be.structureSize()[1]);
        assertEquals(1, be.rotation());
        assertEquals(0, be.mirror(), "모르는 이름은 NONE");
        assertEquals(StructureBlockEntity.Mode.LOAD, be.mode());
        assertEquals(0.5F, be.integrity());
        assertEquals(77L, be.seed());

        be.setStructureName("mine:dir/house");
        assertEquals("mine:dir/house", be.getStructureName());
        be.setStructureName("house");
        assertEquals("minecraft:house", be.getStructureName());
        be.setStructureName("   ");
        assertFalse(be.hasStructureName());
    }

    @Test
    void jigsawBlockEntityNbt() {
        int north = B.defaultState(B.id("jigsaw"));
        var be = new JigsawBlockEntity(new Pos(0, 64, 0), north);
        NbtCompound t = be.saveCustomOnly();
        assertEquals("minecraft:empty", t.getString("name"));
        assertEquals("minecraft:empty", t.getString("pool"));
        assertEquals("minecraft:air", t.getString("final_state"));
        assertEquals("rollable", t.getString("joint"));
        assertEquals(0, t.getInt("placement_priority"));
        NbtCompound in = new NbtCompound();
        in.putString("name", "a:b");
        in.putString("target", "c:d");
        in.putString("pool", "minecraft:village/plains/houses");
        in.putString("final_state", "minecraft:stone");
        in.putString("joint", "aligned");
        in.putInt("placement_priority", 3);
        in.putInt("selection_priority", 9);
        be.load(in);
        NbtCompound out = be.saveCustomOnly();
        assertEquals("a:b", out.getString("name"));
        assertEquals("c:d", out.getString("target"));
        assertEquals("minecraft:village/plains/houses", out.getString("pool"));
        assertEquals("aligned", out.getString("joint"));
        assertEquals(3, out.getInt("placement_priority"));
        assertEquals(9, out.getInt("selection_priority"));
        // joint 가 없으면 앞 방향으로: 기본 상태(north_up)는 수평이라 aligned, 위쪽을 보는 상태는 rollable
        var h = new JigsawBlockEntity(new Pos(0, 64, 0), B.defaultState(B.id("jigsaw")));
        h.load(new NbtCompound());
        assertEquals(JigsawBlockEntity.JointType.ALIGNED, h.joint());
        int up = B.prop("orientation").with(B.defaultState(B.id("jigsaw")), "up_north");
        var v = new JigsawBlockEntity(new Pos(0, 64, 0), up);
        v.load(new NbtCompound());
        assertEquals(JigsawBlockEntity.JointType.ROLLABLE, v.joint());
    }

    // ------------------------------------------------------------------ 저장·불러오기

    @Test
    void saveWritesFileAndLoadRestoresBlocksBlockEntitiesAndRotation() throws Exception {
        start();
        // 저장 블록 (2,70,2), 영역 오프셋 (1,1,1) 크기 (3,2,3) → x 3..5, y 71..72, z 3..5
        Pos savePos = new Pos(2, 70, 2);
        run(lvl -> {
            lvl.setBlock(savePos, B.defaultState(B.id("structure_block")), 3);
            for (int x = 3; x <= 5; x++) for (int z = 3; z <= 5; z++) lvl.setBlock(new Pos(x, 71, z), state("stone"), 3);
            lvl.setBlock(new Pos(4, 72, 4), state("command_block"), 3);
            ((CommandBlockEntity) lvl.getBlockEntity(new Pos(4, 72, 4))).logic().setCommand("say hi");
            lvl.setBlock(new Pos(3, 72, 3), state("oak_planks"), 3);
            lvl.setBlock(new Pos(5, 72, 5), state("structure_void"), 3);
            var be = (StructureBlockEntity) lvl.getBlockEntity(savePos);
            be.applyEdit(StructureBlockEntity.Mode.SAVE, "test:house", new int[] {1, 1, 1}, new int[] {3, 2, 3}, 0, 0, "", true, false, true, 1.0F, 0L, lvl);
            assertEquals("save", B.prop("mode").valueName(lvl.getBlockState(savePos)), "모드가 블록 상태에 반영된다");
            assertTrue(be.saveStructure(lvl), "저장 성공");
            return null;
        });
        Path file = tmp.resolve("generated/test/structures/house.nbt");
        assertTrue(Files.isRegularFile(file), "generated/<ns>/structures/<path>.nbt");
        NbtCompound root;
        try (var in = Files.newInputStream(file)) {
            root = NbtIo.readGzip(in);
        }
        assertEquals(3955, root.getInt("DataVersion"));
        var size = root.getList("size");
        assertEquals(3, size.get(0));
        assertEquals(2, size.get(1));
        assertEquals(3, size.get(2));
        assertEquals(17, root.getList("blocks").size(), "3x2x3=18 에서 structure_void 하나 뺀 17칸");
        boolean sawCommand = false;
        for (Object o : root.getList("blocks").items()) {
            NbtCompound b = (NbtCompound) o;
            if (b.contains("nbt")) {
                sawCommand = true;
                assertEquals("minecraft:command_block", b.getCompound("nbt").getString("id"));
                assertEquals("say hi", b.getCompound("nbt").getString("Command"));
                assertFalse(b.getCompound("nbt").contains("x"), "좌표는 저장하지 않는다(saveWithId)");
            }
        }
        assertTrue(sawCommand);
        // 불러오기: 다른 곳 (2,70,8) 에서 오프셋 (1,1,1) → x 3..5, y 71..72, z 9..11
        Pos loadPos = new Pos(2, 70, 8);
        run(lvl -> {
            lvl.setBlock(loadPos, B.defaultState(B.id("structure_block")), 3);
            var be = (StructureBlockEntity) lvl.getBlockEntity(loadPos);
            be.applyEdit(StructureBlockEntity.Mode.LOAD, "test:house", new int[] {1, 1, 1}, new int[] {3, 2, 3}, 0, 0, "", true, false, true, 1.0F, 0L, lvl);
            assertTrue(be.isStructureLoadable(lvl));
            assertTrue(be.placeStructureIfSameSize(lvl), "크기가 같으면 놓는다");
            return null;
        });
        run(lvl -> {
            assertTrue(B.is(lvl.getBlockState(new Pos(3, 71, 9)), "minecraft:stone"));
            assertTrue(B.is(lvl.getBlockState(new Pos(3, 72, 9)), "minecraft:oak_planks"));
            assertTrue(B.is(lvl.getBlockState(new Pos(4, 72, 10)), "minecraft:command_block"));
            assertTrue(B.isAir(lvl.getBlockState(new Pos(5, 72, 11))), "structure_void 자리는 비어 있었다");
            assertEquals("say hi", ((CommandBlockEntity) lvl.getBlockEntity(new Pos(4, 72, 10))).logic().command(), "블록 엔티티 NBT 도 복원");
            return null;
        });
        // 크기가 다르면 놓지 않고 템플릿 크기로 맞춘다
        run(lvl -> {
            var be = (StructureBlockEntity) lvl.getBlockEntity(loadPos);
            be.setStructureSize(1, 1, 1);
            assertFalse(be.placeStructureIfSameSize(lvl));
            assertArrayEquals(new int[] {3, 2, 3}, be.structureSize());
            return null;
        });
        // 회전: CLOCKWISE_90 — 크기 (3,2,3) 이라 같은 칸에 놓이되 돌아간다. command_block 의 facing 이 바뀐다.
        run(lvl -> {
            var be = (StructureBlockEntity) lvl.getBlockEntity(loadPos);
            be.setRotation(1);
            be.loadStructure(lvl);
            return null;
        });
        run(lvl -> {
            assertTrue(B.is(lvl.getBlockState(new Pos(4, 72, 10)), "minecraft:command_block"), "가운데는 그대로");
            return null;
        });
    }

    @Test
    void redstoneSaveOnlyUpdatesMemoryAndCornerUnloads() throws Exception {
        start();
        Pos savePos = new Pos(2, 70, 2);
        run(lvl -> {
            lvl.setBlock(savePos, B.defaultState(B.id("structure_block")), 3);
            lvl.setBlock(new Pos(3, 71, 3), state("stone"), 3);
            var be = (StructureBlockEntity) lvl.getBlockEntity(savePos);
            be.applyEdit(StructureBlockEntity.Mode.SAVE, "test:mem", new int[] {1, 1, 1}, new int[] {1, 1, 1}, 0, 0, "", true, false, true, 1.0F, 0L, lvl);
            assertTrue(be.saveStructure(lvl, false), "신호 저장은 메모리 템플릿만");
            return null;
        });
        assertFalse(Files.exists(tmp.resolve("generated/test/structures/mem.nbt")), "신호로는 파일을 쓰지 않는다");
        run(lvl -> {
            var be = (StructureBlockEntity) lvl.getBlockEntity(savePos);
            var mgr = org.domi.furnace.server.world.gen.structure.template.TemplateManager.forWorld(tmp, world.blockRegistry());
            assertTrue(mgr.get(ResourceId.parse("test:mem")).isPresent());
            // 저장소에서 뺀다(CORNER 신호) → 파일이 없으니 사라진다
            be.setMode(StructureBlockEntity.Mode.CORNER, lvl);
            be.unloadStructure(lvl);
            assertTrue(mgr.get(ResourceId.parse("test:mem")).isEmpty());
            return null;
        });
    }

    @Test
    void cornersDetectSizeAndModeFollowsBlockState() throws Exception {
        start();
        Pos savePos = new Pos(8, 70, 8);
        run(lvl -> {
            lvl.setBlock(savePos, B.defaultState(B.id("structure_block")), 3);
            var save = (StructureBlockEntity) lvl.getBlockEntity(savePos);
            save.applyEdit(StructureBlockEntity.Mode.SAVE, "test:scan", new int[] {0, 1, 0}, new int[] {0, 0, 0}, 0, 0, "", true, false, true, 1.0F, 0L, lvl);
            // 모서리 두 개: (3,66,3) 와 (12,75,12) — 저장 블록 (8,70,8) 은 그 안쪽
            for (Pos c : new Pos[] {new Pos(3, 66, 3), new Pos(12, 75, 12)}) {
                lvl.setBlock(c, B.defaultState(B.id("structure_block")), 3);
                ((StructureBlockEntity) lvl.getBlockEntity(c)).applyEdit(StructureBlockEntity.Mode.CORNER, "test:scan", new int[] {0, 1, 0}, new int[] {0, 0, 0},
                        0, 0, "", true, false, true, 1.0F, 0L, lvl);
            }
            // 이름이 다른 모서리는 무시
            lvl.setBlock(new Pos(14, 80, 14), B.defaultState(B.id("structure_block")), 3);
            ((StructureBlockEntity) lvl.getBlockEntity(new Pos(14, 80, 14))).applyEdit(StructureBlockEntity.Mode.CORNER, "test:other", new int[] {0, 1, 0},
                    new int[] {0, 0, 0}, 0, 0, "", true, false, true, 1.0F, 0L, lvl);
            assertEquals("corner", B.prop("mode").valueName(lvl.getBlockState(new Pos(3, 66, 3))));
            assertTrue(save.detectSize(lvl));
            // 상자 (3..12, 66..75, 3..12) 안쪽: 오프셋 = min - pos + 1, 크기 = (끝-시작) - 1
            assertArrayEquals(new int[] {3 - 8 + 1, 66 - 70 + 1, 3 - 8 + 1}, save.structurePos());
            assertArrayEquals(new int[] {8, 8, 8}, save.structureSize());
            return null;
        });
    }

    @Test
    void invalidNameAndMissingTemplateFail() throws Exception {
        start();
        Pos p = new Pos(2, 70, 2);
        run(lvl -> {
            lvl.setBlock(p, B.defaultState(B.id("structure_block")), 3);
            var be = (StructureBlockEntity) lvl.getBlockEntity(p);
            be.applyEdit(StructureBlockEntity.Mode.LOAD, "nothing:here", new int[] {1, 1, 1}, new int[] {1, 1, 1}, 0, 0, "", true, false, true, 1.0F, 0L, lvl);
            assertFalse(be.isStructureLoadable(lvl));
            assertFalse(be.placeStructureIfSameSize(lvl));
            be.setMode(StructureBlockEntity.Mode.SAVE, lvl);
            be.setStructureName("");
            assertFalse(be.saveStructure(lvl), "이름이 없으면 저장하지 않는다");
            return null;
        });
    }
}
