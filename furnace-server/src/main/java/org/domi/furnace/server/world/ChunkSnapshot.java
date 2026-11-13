// 직렬화용 불변 청크 카피. region 스레드가 떠서 chunkPool이 락프리로 직렬화한다.
package org.domi.furnace.server.world;

import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import java.util.ArrayList;
import java.util.List;

public record ChunkSnapshot(int chunkX, int chunkZ, long[] heightmap, long[] worldSurfaceHeightmap,
                             Section[] sections, List<BlockEntitySnapshot> blockEntities, SaveData save, Light light) {

    /** 빛 배열 복사본(26섹션, 아직 빛이 없는 청크면 ChunkSnapshot.light 가 null). */
    public record Light(byte[][] sky, byte[][] block) {}

    /** 빛 배열을 뺀 사본(저장하면 isLightOn=0 — 바닐라가 열 때 다시 계산한다). */
    public ChunkSnapshot withoutLight() {
        return new ChunkSnapshot(chunkX, chunkZ, heightmap, worldSurfaceHeightmap, sections, blockEntities, save, null);
    }


    /**
     * 디스크 저장에만 필요한 추가 상태(forSave 로만 채워진다, 네트워크용 스냅샷은 null).
     * generatedBlockEntities 의 BlockEntitySnapshot.tag 는 깊은 복사본, typeId 는 -1.
     */
    public record SaveData(int minY, long[] oceanFloorHeightmap, long[] motionBlockingNoLeavesHeightmap,
                           List<BlockEntitySnapshot> generatedBlockEntities, List<NbtCompound> entities,
                           NbtCompound extraNbt, long inhabitedTime) {}

    /** 체스트 block_entity_type id(§0.2 BlockEntityType 등록순서: FURNACE=0, CHEST=1). */
    public static final int CHEST_TYPE_ID = 1;

    /**
     * 섹션 사본. C-5: region 스레드에서는 팔레트 원본(PaletteStorage 읽기 사본, 4비트면 약 2KB)만 복사하고
     * int[4096] 풀기는 처음 {@link #blocks()} 를 부르는 스레드(본문 직렬화 풀)로 미룬다. 푼 값은 예전 즉시 풀기와 같은 배열이다.
     */
    public static final class Section {
        private final short nonAirCount;
        private final PaletteStorage blockSrc, biomeSrc;
        private volatile int[] blocks, biomes;

        public Section(short nonAirCount, int[] blocks, int[] biomes) {
            this.nonAirCount = nonAirCount;
            this.blockSrc = null;
            this.biomeSrc = null;
            this.blocks = blocks;
            this.biomes = biomes;
        }

        private Section(ChunkSection s) {
            this.nonAirCount = s.nonAirCount();
            this.blockSrc = s.blocksReadCopy();
            this.biomeSrc = s.biomesReadCopy();
            if (blockSrc == null) this.blocks = s.blocks();   // scratch 가 열려 있으면 예전처럼 즉시 복사
        }

        public short nonAirCount() { return nonAirCount; }

        /** 블록 상태 id 4096개(YZX). 같은 배열을 돌려준다 — 고치지 않는다. */
        public int[] blocks() {
            int[] b = blocks;
            if (b == null) blocks = b = blockSrc.unpack();   // 경합 시 같은 값으로 두 번 풀 수 있을 뿐(무해)
            return b;
        }

        /** 바이옴 id 64개. 같은 배열을 돌려준다 — 고치지 않는다. */
        public int[] biomes() {
            int[] b = biomes;
            if (b == null) biomes = b = biomeSrc.unpack();
            return b;
        }

        /** 섹션 로컬 인덱스(y*256+z*16+x)의 블록 하나 — 섹션 전체를 펼치지 않는다. */
        public int blockAt(int idx) {
            int[] b = blocks;
            if (b != null) return b[idx];
            return blockSrc.get(idx);
        }

        /**
         * T-015: 블록의 첫 등장 순 색인(펼치지 않음). 펼친 배열만 가진 섹션(공개 생성자·scratch 사본)이나 직접 저장이면 null —
         * 그때는 {@link #blocks()} 로 옛 경로. 돌려준 객체는 스레드별이라 이 스레드의 다음 색인 호출 전까지만 쓴다.
         */
        public FirstSeenIndex blockIndex() {
            return index(blockSrc);
        }

        /** {@link #blockIndex()} 의 바이옴판. */
        public FirstSeenIndex biomeIndex() {
            return index(biomeSrc);
        }

        private static FirstSeenIndex index(PaletteStorage src) {
            if (src == null) return null;
            FirstSeenIndex out = FirstSeenIndex.get();
            return src.indexFirstSeen(out) ? out : null;
        }
    }

    /** 청크 패킷 block_entities 배열 항목 — localX/localZ는 청크 내부 0..15, y는 절대 월드 y. */
    public record BlockEntitySnapshot(int localX, int y, int localZ, int typeId, NbtCompound tag) {}

    /** region 스레드서 호출 — 섹션 블록/바이옴 배열 + 블록엔티티를 카피해 불변 스냅샷 생성. */
    public static ChunkSnapshot of(int chunkX, int chunkZ, ChunkColumn col) {
        ChunkSection[] src = col.sections();
        Section[] out = new Section[src.length];
        for (int i = 0; i < src.length; i++) {
            out[i] = new Section(src[i]);
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
        return new ChunkSnapshot(chunkX, chunkZ, col.motionBlockingHeightmap(), col.worldSurfaceHeightmap(), out, beOut, null, copyLight(col));
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
            if (ce.customName != null || ce.lock != null || !ce.components().isEmpty()) {   // 이름·잠금·남은 컴포넌트
                if (tag == null) tag = new NbtCompound();
                if (ce.customName != null) tag.putString("CustomName", ce.customName);
                if (ce.lock != null) tag.putString("Lock", ce.lock);
                NbtCompound full = ce.saveWithoutMetadata();
                if (full.tags().get("components") instanceof NbtCompound c) tag.putCompound("components", c);
            }
            chests.add(new BlockEntitySnapshot(ce.x & 15, ce.y, ce.z & 15, CHEST_TYPE_ID, tag));
        }
        col.forEachGeneratedBlockEntity((lx, y, lz, nbt) -> generated.add(new BlockEntitySnapshot(lx, y, lz, -1, nbt.copy())));
        List<NbtCompound> entities = new ArrayList<>();
        for (NbtCompound e : col.generatedEntities()) entities.add(e.copy());
        NbtCompound extra = col.extraNbt() == null ? null : col.extraNbt().copy();
        if (!col.structures().isEmpty()) {
            if (extra == null) extra = new NbtCompound();
            extra.putCompound("structures", col.structures().toNbt());
        }
        SaveData save = new SaveData(col.minY(), col.oceanFloorHeightmap(), col.motionBlockingNoLeavesHeightmap(),
                generated, entities, extra, col.inhabitedTime());
        return new ChunkSnapshot(chunkX, chunkZ, base.heightmap, base.worldSurfaceHeightmap, base.sections, chests, save, base.light);
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

    /** 모두 0·모두 15 인 빛 층의 공유 사본(C-5). 스냅샷 빛 배열은 읽기 전용이라 여러 스냅샷이 같이 가리켜도 된다 — 고치지 않는다. */
    private static final byte[] ZERO_LAYER = new byte[2048], FULL_LAYER = new byte[2048];
    static {
        java.util.Arrays.fill(FULL_LAYER, (byte) 0xFF);
    }

    private static Light copyLight(ChunkColumn col) {
        var l = col.light();
        if (l == null) return null;
        int n = org.domi.furnace.server.light.ChunkLight.SECTIONS;
        byte[][] sky = new byte[n][], block = new byte[n][];
        for (int i = 0; i < n; i++) {
            sky[i] = copyLayer(l.sky(i));
            block[i] = copyLayer(l.block(i));
        }
        return new Light(sky, block);
    }

    /** 균일한 층(0 또는 0xFF)이면 공유 사본, 아니면 복제. 내용은 언제나 원본과 같다. */
    static byte[] copyLayer(byte[] a) {
        // 살아 있는 청크의 공유 층(T-012)은 훑지 않고 바로 — 내용이 같은 스냅샷 공유 사본으로
        if (org.domi.furnace.server.light.ChunkLight.isKnownZero(a)) return ZERO_LAYER;
        if (org.domi.furnace.server.light.ChunkLight.isKnownFull(a)) return FULL_LAYER;
        if (a.length == 2048) {
            byte first = a[0];
            if (first == 0 || first == (byte) 0xFF) {
                int i = 1;
                while (i < 2048 && a[i] == first) i++;
                if (i == 2048) return first == 0 ? ZERO_LAYER : FULL_LAYER;
            }
        }
        return a.clone();
    }
}
