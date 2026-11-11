// A06-06: 탐험 지도(skipKnownStructures=true)는 고른 시작점의 references 를 올리고, 다음 지도는 그 시작점을 건너뛴다.
// /locate·엔더의 눈(false)은 영향받지 않는다. 값은 청크 NBT 와 data 파일로 남고, 디스크의 바닐라 청크 값도 읽는다.
package org.domi.furnace.server.world.gen.structure;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.map.ExplorerMaps;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.anvil.AnvilChunkCodec;
import org.domi.furnace.server.world.anvil.RegionFileStore;
import org.domi.furnace.server.world.gen.AnvilWorldSource;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StructureReferencesTest {
    private static final BlockRegistry REG = BlockRegistry.load("blocks/1.21.1.txt");
    private static final RegistrySet REGS = RegistrySet.load("registries/1.21.1.txt");

    @TempDir Path tmp;

    private ExplorerMaps.Backend backend(ServerCore core) throws Exception {
        Method m = ServerCore.class.getDeclaredMethod("explorerMapBackend");
        m.setAccessible(true);
        return (ExplorerMaps.Backend) m.invoke(core);
    }

    /** 감사 재현(A06MapReproTest): 같은 자리에서 해저 신전 탐험 지도를 두 번 만들면 두 번째는 다른 곳. */
    @Test
    void secondExplorerMapSkipsKnownStructure() throws Exception {
        ServerCore core = TestServers.core(tmp, new FurnaceSchedulerImpl(50_000_000L, 1), reg -> NoiseWorldSource.overworld(1234L, reg, REGS));
        var backend = backend(core);
        int[] located = backend.findNearestMapStructure("minecraft:on_ocean_explorer_maps", 0, 64, 0, 100, false);
        int[] first = backend.findNearestMapStructure("minecraft:on_ocean_explorer_maps", 0, 64, 0, 100, true);
        assertNotNull(first);
        assertArrayEquals(located, first, "references 가 0 이면 /locate 와 같은 것을 고른다");
        int[] second = backend.findNearestMapStructure("minecraft:on_ocean_explorer_maps", 0, 64, 0, 100, true);
        assertNotNull(second);
        assertFalse(first[0] == second[0] && first[1] == second[1], "두 번째 지도가 같은 해저 신전 " + first[0] + "," + first[1]);
        // skipKnown=false(/locate, 엔더의 눈)는 references 를 보지 않는다
        assertArrayEquals(located, backend.findNearestMapStructure("minecraft:on_ocean_explorer_maps", 0, 64, 0, 100, false));
    }

    /** tryAddReference: references < 1 일 때만 올리고 참. */
    @Test
    void tryAddReferenceOnlyOnce() {
        StructureReferences r = new StructureReferences();
        assertEquals(0, r.get("minecraft:monument", 3, -4));
        assertTrue(r.tryAddReference("minecraft:monument", 3, -4));
        assertFalse(r.tryAddReference("minecraft:monument", 3, -4));
        assertEquals(1, r.get("minecraft:monument", 3, -4));
        assertTrue(r.tryAddReference("minecraft:mansion", 3, -4), "구조물마다 따로");
    }

    /** data 파일로 남아 다시 켜도 건너뛴다(아직 만들어지지 않은 시작 청크). */
    @Test
    void persistsInDataFile() {
        Path f = tmp.resolve("data").resolve("furnace_structure_references.dat");
        StructureReferences a = new StructureReferences();
        a.bind(f, (id, x, z) -> -1);
        assertTrue(a.tryAddReference("minecraft:buried_treasure", -7, 12));
        a.saveIfDirty();
        assertTrue(Files.isRegularFile(f));
        StructureReferences b = new StructureReferences();
        b.bind(f, (id, x, z) -> -1);
        assertFalse(b.tryAddReference("minecraft:buried_treasure", -7, 12));
        assertTrue(b.tryAddReference("minecraft:buried_treasure", -7, 13));
    }

    /** 청크 저장: 생성한 시작점의 references 를 그때 값으로 쓰고, 불러온 청크의 값은 저장소에 들어간다. */
    @Test
    void chunkNbtCarriesReferences() {
        NoiseWorldSource gen = NoiseWorldSource.overworld(1234L, REG, REGS);
        StructureEngine.CommandStart cs = null;
        for (int i = 0; i < 40 && (cs == null || cs.start() == null); i++) cs = gen.structureEngine().generateForCommand("minecraft:village_plains", i * 9, 3, gen);
        StructureStart s = cs.start();
        StructureReferences refs = new StructureReferences();
        ChunkStructures cst = ChunkStructures.generated(s.chunkX(), s.chunkZ(), List.of(s), List.of()).attach(refs);
        NbtCompound before = cst.toNbt();
        assertEquals(0, before.getCompound("starts").getCompound("minecraft:village_plains").getInt("references"));
        assertTrue(refs.tryAddReference("minecraft:village_plains", s.chunkX(), s.chunkZ()));
        NbtCompound after = cst.toNbt();
        assertEquals(1, after.getCompound("starts").getCompound("minecraft:village_plains").getInt("references"));

        // 불러온 청크(원본 보존 경로): 새 저장소에 값이 들어가고, 다시 쓸 때도 그대로
        StructureReferences fresh = new StructureReferences();
        ChunkStructures loaded = ChunkStructures.fromNbt(after).attach(fresh);
        assertFalse(fresh.tryAddReference("minecraft:village_plains", s.chunkX(), s.chunkZ()));
        assertEquals(1, loaded.toNbt().getCompound("starts").getCompound("minecraft:village_plains").getInt("references"));

        // 불러온 뒤 올린 값도 원본 사본에 반영
        NbtCompound zero = before.copy();
        StructureReferences r3 = new StructureReferences();
        ChunkStructures l3 = ChunkStructures.fromNbt(zero).attach(r3);
        assertTrue(r3.tryAddReference("minecraft:village_plains", s.chunkX(), s.chunkZ()));
        assertEquals(1, l3.toNbt().getCompound("starts").getCompound("minecraft:village_plains").getInt("references"));
        assertEquals(0, zero.getCompound("starts").getCompound("minecraft:village_plains").getInt("references"), "원본은 그대로");
    }

    /** 디스크 월드: 아직 불러오지 않은 바닐라 청크의 references 를 region 파일에서 읽는다(바닐라 StructureCheck.tryLoadFromStorage). */
    @Test
    void readsStoredReferencesFromDisk() throws Exception {
        NoiseWorldSource gen = NoiseWorldSource.overworld(1234L, REG, REGS);
        RegionFileStore store = new RegionFileStore(tmp);
        new AnvilWorldSource(tmp, store, new AnvilChunkCodec(REG, 0), gen);
        NbtCompound st = new NbtCompound();
        st.putString("id", "minecraft:monument");
        st.putInt("ChunkX", 5);
        st.putInt("ChunkZ", 6);
        st.putInt("references", 1);
        NbtCompound starts = new NbtCompound();
        starts.putCompound("minecraft:monument", st);
        NbtCompound structures = new NbtCompound();
        structures.putCompound("starts", starts);
        NbtCompound root = new NbtCompound();
        root.putCompound("structures", structures);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        NbtIo.write(new DataOutputStream(bos), root, "");
        store.writeChunk(5, 6, bos.toByteArray());
        StructureReferences refs = gen.structureEngine().references();
        assertEquals(1, refs.get("minecraft:monument", 5, 6));
        assertFalse(refs.tryAddReference("minecraft:monument", 5, 6));
        assertTrue(refs.tryAddReference("minecraft:monument", 5, 7), "디스크에 없는 시작점은 0");
        refs.saveIfDirty();
        assertTrue(Files.isRegularFile(tmp.resolve("data").resolve("furnace_structure_references.dat")), "<차원>/data 에 남는다");
    }
}
