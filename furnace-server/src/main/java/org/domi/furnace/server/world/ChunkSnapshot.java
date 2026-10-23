// 직렬화용 불변 청크 카피. region 스레드가 떠서 chunkPool이 락프리로 직렬화한다.
package org.domi.furnace.server.world;

import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import java.util.ArrayList;
import java.util.List;

public record ChunkSnapshot(int chunkX, int chunkZ, long[] heightmap, long[] worldSurfaceHeightmap,
                             Section[] sections, List<BlockEntitySnapshot> blockEntities, SaveData save) {

    /**
     * 디스크 저장에만 필요한 추가 상태(forSave 로만 채워진다, 네트워크용 스냅샷은 null).
     * generatedBlockEntities 의 BlockEntitySnapshot.tag 는 깊은 복사본, typeId 는 -1.
     */
    public record SaveData(int minY, long[] oceanFloorHeightmap, long[] motionBlockingNoLeavesHeightmap,
                           List<BlockEntitySnapshot> generatedBlockEntities, List<NbtCompound> entities,
                           NbtCompound extraNbt, long inhabitedTime) {}

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
        for (var be : col.blockEntities().values()) {
            var p = be.pos();
            if (be instanceof ChestBlockEntity) {
                // 바닐라 상자 getUpdateTag 는 빈 컴파운드 → 청크 패킷에는 null(TAG_END). 내용물은 클라이언트로 보내지 않는다.
                beOut.add(new BlockEntitySnapshot(p.x() & 15, p.y(), p.z() & 15, be.typeId(), null));
            } else {
                // ClientboundLevelChunkPacketData.BlockEntityInfo: getUpdateTag 가 비면 null
                NbtCompound tag = be.updateTag();
                beOut.add(new BlockEntitySnapshot(p.x() & 15, p.y(), p.z() & 15, be.typeId(), tag == null || tag.tags().isEmpty() ? null : tag));
            }
        }
        return new ChunkSnapshot(chunkX, chunkZ, col.motionBlockingHeightmap(), col.worldSurfaceHeightmap(), out, beOut, null);
    }

    /** region 스레드서 호출 — 디스크 저장용(of + 높이맵 4종·생성 블록 엔티티·엔티티·미지 NBT 사본). */
    public static ChunkSnapshot forSave(int chunkX, int chunkZ, ChunkColumn col) {
        ChunkSnapshot base = of(chunkX, chunkZ, col);
        // 저장본에는 아직 열지 않은 전리품 상자의 LootTable·LootTableSeed 도 남긴다(클라이언트로는 보내지 않는다).
        List<BlockEntitySnapshot> chests = new ArrayList<>();
        List<BlockEntitySnapshot> generated = new ArrayList<>();
        for (var be : col.blockEntities().values()) {
            if (!(be instanceof ChestBlockEntity ce)) {
                // 종류별 블록 엔티티는 바닐라 저장 꼴(id·x·y·z 포함) 그대로 — 코덱이 생성 블록 엔티티와 같은 길로 쓴다.
                var p = be.pos();
                generated.add(new BlockEntitySnapshot(p.x() & 15, p.y(), p.z() & 15, -1, be.saveWithFullMetadata()));
                continue;
            }
            NbtCompound tag = chestTag(ce.items);
            if (ce.lootTable != null) {
                if (tag == null) tag = new NbtCompound();
                tag.putString("LootTable", ce.lootTable);
                if (ce.lootSeed != 0L) tag.putLong("LootTableSeed", ce.lootSeed);
            }
            chests.add(new BlockEntitySnapshot(ce.x & 15, ce.y, ce.z & 15, CHEST_TYPE_ID, tag));
        }
        col.forEachGeneratedBlockEntity((lx, y, lz, nbt) -> generated.add(new BlockEntitySnapshot(lx, y, lz, -1, nbt.copy())));
        List<NbtCompound> entities = new ArrayList<>();
        for (NbtCompound e : col.generatedEntities()) entities.add(e.copy());
        NbtCompound extra = col.extraNbt() == null ? null : col.extraNbt().copy();
        SaveData save = new SaveData(col.minY(), col.oceanFloorHeightmap(), col.motionBlockingNoLeavesHeightmap(),
                generated, entities, extra, col.inhabitedTime());
        return new ChunkSnapshot(chunkX, chunkZ, base.heightmap, base.worldSurfaceHeightmap, base.sections, chests, save);
    }

    /** 저장 전 내부 꼴: Compound{ Items: List<Compound>{Slot,Id,Count,components?} }, 빈 체스트면 null(=updateTag 없음). */
    private static NbtCompound chestTag(ItemStack[] items) {
        NbtList list = new NbtList(NbtIo.TAG_COMPOUND);
        for (int i = 0; i < items.length; i++) {
            ItemStack s = items[i];
            if (s == null || s.isEmpty()) continue;
            NbtCompound entry = new NbtCompound();
            entry.putByte("Slot", (byte) i);
            entry.putInt("Id", s.itemId());
            entry.putInt("Count", s.count());
            NbtCompound components = org.domi.furnace.server.item.ItemNbt.write(s.patch());
            if (components != null) entry.putCompound(org.domi.furnace.server.item.ItemNbt.KEY, components);
            list.add(entry);
        }
        if (list.size() == 0) return null;
        NbtCompound tag = new NbtCompound();
        tag.putList("Items", list);
        return tag;
    }
}
