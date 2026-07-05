// 직렬화용 불변 청크 카피. region 스레드가 떠서 chunkPool이 락프리로 직렬화한다.
package org.domi.furnace.server.world;

import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import java.util.ArrayList;
import java.util.List;

public record ChunkSnapshot(int chunkX, int chunkZ, long[] heightmap, long[] worldSurfaceHeightmap,
                             Section[] sections, List<BlockEntitySnapshot> blockEntities) {

    /** 체스트 block_entity_type id(§0.2 BlockEntityType 등록순서: FURNACE=0, CHEST=1). */
    public static final int CHEST_TYPE_ID = 1;

    public record Section(short nonAirCount, int[] blocks, int[] biomes) {}

    /** 청크 패킷 block_entities 배열 항목 — localX/localZ는 청크 내부 0..15, y는 절대 월드 y. */
    public record BlockEntitySnapshot(int localX, int y, int localZ, int typeId, NbtCompound tag) {}

    /** region 스레드서 호출 — 섹션 블록/바이옴 배열 + 블록엔티티를 카피해 불변 스냅샷 생성. */
    public static ChunkSnapshot of(int chunkX, int chunkZ, ChunkColumn col) {
        ChunkSection[] src = col.sections();
        Section[] out = new Section[src.length];
        for (int i = 0; i < src.length; i++) {
            out[i] = new Section(src[i].nonAirCount(), src[i].blocks(), src[i].biomes());
        }
        List<BlockEntitySnapshot> beOut = new ArrayList<>();
        for (ChestBlockEntity ce : col.blockEntities().values()) {
            int localX = ce.x & 15;
            int localZ = ce.z & 15;
            beOut.add(new BlockEntitySnapshot(localX, ce.y, localZ, CHEST_TYPE_ID, chestTag(ce.items)));
        }
        return new ChunkSnapshot(chunkX, chunkZ, col.motionBlockingHeightmap(), col.worldSurfaceHeightmap(), out, beOut);
    }

    /** §2.1 스키마: Compound{ Items: List<Compound>{Slot,Id,Count,Comp?} }, 빈 체스트면 null(=updateTag 없음). */
    private static NbtCompound chestTag(ItemStack[] items) {
        NbtList list = new NbtList(NbtIo.TAG_COMPOUND);
        for (int i = 0; i < items.length; i++) {
            ItemStack s = items[i];
            if (s == null || s.isEmpty()) continue;
            NbtCompound entry = new NbtCompound();
            entry.putByte("Slot", (byte) i);
            entry.putInt("Id", s.itemId());
            entry.putInt("Count", s.count());
            if (s.components() != null) entry.putByteArray("Comp", s.components());
            list.add(entry);
        }
        if (list.size() == 0) return null;
        NbtCompound tag = new NbtCompound();
        tag.putList("Items", list);
        return tag;
    }
}
