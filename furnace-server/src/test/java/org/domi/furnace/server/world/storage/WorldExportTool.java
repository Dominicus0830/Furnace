// 상호운용 점검 도구: Furnace 가 생성·저장한 월드(level.dat + region)를 만들어 바닐라 서버로 열어 볼 수 있게 한다.
// -Dfurnace.exportWorld=<폴더> 일 때만 실행(평소 테스트에서는 건너뜀). 스폰 청크 (0,0)의 (0, 200, 0) 에 다이아 블록 표식을 둔다.
package org.domi.furnace.server.world.storage;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Path;
import java.util.List;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.Difficulty;
import org.domi.furnace.server.world.GameMode;
import org.domi.furnace.server.world.ItemRegistry;
import org.domi.furnace.server.world.anvil.AnvilChunkCodec;
import org.domi.furnace.server.world.anvil.RegionFileStore;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.junit.jupiter.api.Test;

class WorldExportTool {

    @Test
    void export() throws Exception {
        String out = System.getProperty("furnace.exportWorld");
        assumeTrue(out != null, "-Dfurnace.exportWorld 미지정");
        long seed = Long.getLong("furnace.exportSeed", 1234L);
        int radius = Integer.getInteger("furnace.exportRadius", 4);
        Path dir = Path.of(out);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");
        List<String> biomes = registries.entries("minecraft:worldgen/biome");
        AnvilChunkCodec codec = new AnvilChunkCodec(reg, ItemRegistry.load("items/1.21.1.txt"), biomes,
                biomes.indexOf("minecraft:plains"), ChunkColumn.MIN_Y, ChunkColumn.SECTION_COUNT);
        NoiseWorldSource source = NoiseWorldSource.overworld(seed, reg, registries);
        try (LevelStorage storage = LevelStorage.open(dir)) {
            LevelData level = LevelData.create("world", seed, true, false, GameMode.SURVIVAL, false, Difficulty.EASY,
                    List.of("vanilla"), List.of("bundle", "trade_rebalance"));
            var spawn = source.defaultSpawn();
            level.spawnX = spawn.x(); level.spawnY = spawn.y(); level.spawnZ = spawn.z();
            level.initialized = true;
            level.setModdedInfo("Furnace", true);
            storage.saveLevelData(level);
            int scx = spawn.x() >> 4, scz = spawn.z() >> 4;
            RegionFileStore store = new RegionFileStore(dir);
            for (int cx = scx - radius; cx <= scx + radius; cx++) {
                for (int cz = scz - radius; cz <= scz + radius; cz++) {
                    ChunkColumn col = source.generateWithFeatures(cx, cz);
                    if (cx == scx && cz == scz) col.setBlock(0, 200, 0, reg.defaultStateId("minecraft:diamond_block"));
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    NbtIo.write(new DataOutputStream(bos), codec.toNbt(col, cx, cz), "");
                    store.writeChunk(cx, cz, bos.toByteArray());
                }
            }
            store.closeAll();
            System.out.println("exported spawn chunk " + scx + "," + scz + " radius " + radius + " → " + dir);
        }
    }
}
