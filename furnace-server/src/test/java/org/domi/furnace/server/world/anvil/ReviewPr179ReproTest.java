// PR #179 독립 검토 재현 시험(A05-01 손상 청크 새로 생성 경로).
// R1: 다른 region 파일 닫기 실패(drainClosed)가 이 청크 읽기 실패로 번져, 멀쩡한 청크가 "깨진 청크"로 새로 생성된다(백업도 없다).
package org.domi.furnace.server.world.anvil;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.AnvilWorldSource;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReviewPr179ReproTest {

    private static final BlockRegistry REG = BlockRegistry.load("blocks/1.21.1.txt");

    /** (0,0) 에 평지 + (1,10,1) 금 블록을 저장한다 — 새로 생성한 평지에는 금 블록이 없다. */
    private static int writeMarkedChunk(Path dir) throws Exception {
        AnvilChunkCodec codec = new AnvilChunkCodec(REG, 0);
        ChunkColumn col = new SuperflatWorldSource(REG, 0).generate(0, 0);
        int gold = REG.stateIdOrAir("minecraft:gold_block");
        col.setBlock(1, 10, 1, gold);
        RegionFileStore store = new RegionFileStore(dir);
        store.writeChunk(0, 0, NbtIo.toBytes(codec.toNbt(col, 0, 0), ""));
        store.closeAll();
        return gold;
    }

    @Test
    void r1_otherRegionCloseFailure_regeneratesGoodChunk(@TempDir Path dir) throws Exception {
        int gold = writeMarkedChunk(dir);
        RegionFileStore store = new RegionFileStore(dir);
        AnvilWorldSource src = new AnvilWorldSource(dir, store, new AnvilChunkCodec(REG, 0), new SuperflatWorldSource(REG, 0));
        try {
            // 다른 region(r.5.0) 을 열고, 그 핸들 닫기가 실패하게 만든다(이미 닫힌 채널 → force 가 ClosedChannelException).
            // 실제로는 디스크 오류·네트워크 파일 시스템에서 force(true) 가 IOException 을 내는 경우.
            store.readPayload(5 * 32, 0);
            store.openHandle(5 * 32, 0).close();
            // 열린 핸들을 MAX_OPEN 까지 채운다 — 다음 새 파일(r.0.0) 열기가 가장 오래된 r.5.0 을 내보낸다.
            for (int i = 0; i < RegionFileStore.MAX_OPEN - 1; i++) store.readPayload((10 + i) * 32, 0);
            assertEquals(RegionFileStore.MAX_OPEN, store.openCount());

            ChunkColumn col = src.generate(0, 0);   // r.0.0 읽기는 성공한 뒤 drainClosed 가 r.5.0 닫기 실패를 던진다
            boolean regenerated = col.getBlock(1, 10, 1) != gold;
            System.out.println("[PR179-R1] 멀쩡한 청크 (0,0): " + (regenerated ? "새로 생성됨(dirty=" + col.dirty() + ")" : "디스크에서 읽음")
                    + ", region_corrupt 있음=" + Files.exists(dir.resolve("region_corrupt")));
            assertFalse(regenerated, "다른 파일 닫기 실패로 멀쩡한 청크가 새로 생성됐다 — 다음 저장에서 덮이고, 본문이 없어 백업도 남지 않는다");
        } finally {
            try { store.closeAll(); } catch (java.io.IOException ignored) { }
        }
    }
}
