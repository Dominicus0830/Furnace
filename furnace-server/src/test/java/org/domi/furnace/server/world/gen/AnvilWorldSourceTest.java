// AnvilWorldSource: 디스크 없으면 base generate, 있으면 .mca 로드 검증
package org.domi.furnace.server.world.gen;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkSection;
import org.domi.furnace.server.world.anvil.AnvilChunkCodec;
import org.domi.furnace.server.world.anvil.RegionFileStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

class AnvilWorldSourceTest {

    @Test
    void absent_fallsBackToGenerate(@TempDir Path dir) throws Exception {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        RegionFileStore store = new RegionFileStore(dir);
        AnvilWorldSource src = new AnvilWorldSource(dir, store,
                new AnvilChunkCodec(reg, 0), new SuperflatWorldSource(reg, 0));
        ChunkColumn col = src.generate(0, 0);
        assertEquals(reg.defaultStateId("minecraft:grass_block"), col.getBlock(0, -61, 0));
        store.closeAll();
    }

    @Test
    void present_loadsFromDisk(@TempDir Path dir) throws Exception {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        AnvilChunkCodec codec = new AnvilChunkCodec(reg, 0);
        RegionFileStore store = new RegionFileStore(dir);
        // 편집된 청크를 디스크에 둔다
        ChunkColumn edited = new SuperflatWorldSource(reg, 0).generate(0, 0);
        edited.setBlock(0, -61, 0, ChunkSection.AIR);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        NbtIo.write(new DataOutputStream(bos), codec.toNbt(edited, 0, 0), "");
        store.writeChunk(0, 0, bos.toByteArray());

        AnvilWorldSource src = new AnvilWorldSource(dir, store, codec, new SuperflatWorldSource(reg, 0));
        ChunkColumn col = src.generate(0, 0);
        assertEquals(ChunkSection.AIR, col.getBlock(0, -61, 0)); // 디스크 편집 반영
        store.closeAll();
    }
}
