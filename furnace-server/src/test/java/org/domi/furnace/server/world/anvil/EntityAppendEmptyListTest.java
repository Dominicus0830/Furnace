// 빈 Entities 리스트(원소 종류 TAG_END 로 저장됨)가 있는 청크에 엔티티를 덧붙여도 저장이 깨지지 않는지.
// 2026-11-03 오프라인 봇 시험에서 "[SAVE] 엔티티 0,1 덧붙이기 실패 — 미지원 태그: 0" 으로 엔티티가 사라진 것을 재현한다.
package org.domi.furnace.server.world.anvil;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.world.gen.AnvilWorldSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EntityAppendEmptyListTest {

    @TempDir Path tmp;

    private static NbtCompound entity(String id) {
        NbtCompound e = new NbtCompound();
        e.putString("id", id);
        return e;
    }

    @Test
    void appendToChunkSavedWithEmptyEntities() throws Exception {
        AnvilWorldSource src = new AnvilWorldSource(tmp, new RegionFileStore(tmp), null, null);
        src.entityStore(new RegionFileStore(tmp, "entities"));
        // 엔티티가 모두 떠난 청크를 저장 — ChunkLevel.chunkEntitiesForSave 처럼 빈 리스트로 덮어쓴다
        NbtCompound root = new NbtCompound();
        root.putInt("DataVersion", AnvilChunkCodec.DATA_VERSION);
        root.putIntArray("Position", new int[] {0, 1});
        root.putList("Entities", new NbtList(NbtIo.TAG_COMPOUND));
        src.writeEntities(0, 1, root);
        assertEquals(NbtIo.TAG_END, ((NbtList) src.readEntities(0, 1).tags().get("Entities")).elementType(), "빈 리스트는 TAG_END 로 읽힌다");

        // 그 청크가 내려간 뒤 엔티티가 들어와 디스크 묶음에 덧붙는다(ChunkLevel.evictToDisk)
        src.appendEntities(0, 1, List.of(entity("minecraft:strider"), entity("minecraft:zombified_piglin")));
        src.appendEntities(0, 1, List.of(entity("minecraft:cow")));

        NbtList list = (NbtList) src.readEntities(0, 1).tags().get("Entities");
        assertEquals(NbtIo.TAG_COMPOUND, list.elementType());
        assertEquals(3, list.size());
        assertEquals("minecraft:cow", ((NbtCompound) list.get(2)).getString("id"));
    }
}
