// 공개 스냅샷(N2b-3, 설계 3.6): 주인 region 이 틱 끝에 내는 청크의 불변 사본. 다른 스레드(비동기 작업·FAWE 일꾼·다른 region)가 기다리지 않고 읽는다.
// 바뀌지 않은 섹션은 앞 판의 사본을 그대로 쓴다(섹션 객체·바뀜 표가 같으면). 빛은 담지 않는다(N2b-5).
package org.domi.furnace.server.world;

import org.domi.furnace.api.internal.ChunkInternals;

public final class ChunkReadView implements ChunkInternals.ChunkView {

    /** 섹션 하나의 사본 — 주인 스레드에서 만들고 그 뒤로 바뀌지 않는다. */
    static final class SectionView {
        /** 사본을 뜬 섹션 객체와 그때의 바뀜 표(재사용 판정용 — 읽기에는 쓰지 않는다). */
        final ChunkSection src;
        final int srcVersion;
        /** 팔레트 사본(scratch 가 열려 있던 섹션이면 null 이고 flat 을 쓴다). */
        private final PaletteStorage blocks;
        private final int[] flat;
        private final PaletteStorage biomes;
        final int nonAir, nonEmpty, tickingBlock, tickingFluid;

        SectionView(ChunkSection s) {
            this.src = s;
            this.srcVersion = s.version();
            this.blocks = s.blocksReadCopy();
            this.flat = blocks == null ? s.blocks() : null;   // scratch 가 열려 있으면 바깥이 그 배열을 고칠 수 있다 — 지금 값을 펼쳐 둔다
            this.biomes = s.biomesReadCopy();
            int n;
            if (flat != null) {
                n = 0;
                for (int v : flat) if (!ChunkSection.isAir(v)) n++;
            } else {
                n = s.nonAirCount();
            }
            this.nonAir = n;
            int[] c = s.vanillaCounts();   // 모르면 주인 스레드인 지금 센다(읽는 쪽은 섹션을 고치지 않는다)
            this.nonEmpty = c[0];
            this.tickingBlock = c[1];
            this.tickingFluid = c[2];
        }

        int stateAt(int idx) { return flat != null ? flat[idx] : blocks.get(idx); }

        int[] states() { return flat != null ? flat.clone() : blocks.unpack(); }

        int[] biomeIds() { return biomes.unpack(); }

        int parts() { return (tickingBlock > 0 ? 1 : 0) | (tickingFluid > 0 ? 2 : 0); }

        boolean reusableFor(ChunkSection s) { return s == src && s.version() == srcVersion && !s.scratchOpen(); }
    }

    private final Object loadToken;
    private final long version;
    private final int minY;
    private final SectionView[] sections;
    /** [종류(HeightmapType 순서)][z*16+x] = 첫 빈 y - minY. */
    private final int[][] heights;
    private final long[] blockEntities;
    /** 이 판을 낸 때의 컬럼 수정 카운터(재사용 판정용). */
    final long modCount;

    ChunkReadView(Object loadToken, long version, int minY, SectionView[] sections, int[][] heights, long[] blockEntities, long modCount) {
        this.loadToken = loadToken;
        this.version = version;
        this.minY = minY;
        this.sections = sections;
        this.heights = heights;
        this.blockEntities = blockEntities;
        this.modCount = modCount;
    }

    SectionView section(int i) { return sections[i]; }

    @Override public long version() { return version; }

    @Override public Object loadToken() { return loadToken; }

    @Override public int sectionCount() { return sections.length; }

    private SectionView at(int y) {
        int local = y - minY;
        if (local < 0 || local >= sections.length * 16) throw new IndexOutOfBoundsException("높이 밖: " + y);
        return sections[local >> 4];
    }

    private static int cell(int x, int localY, int z) { return (localY & 15) * 256 + (z & 15) * 16 + (x & 15); }

    @Override
    public int stateId(int x, int y, int z) { return at(y).stateAt(cell(x, y - minY, z)); }

    @Override
    public int vanillaStateId(int x, int y, int z) {
        SectionView s = at(y);
        return s.nonEmpty == 0 ? ChunkSection.AIR : s.stateAt(cell(x, y - minY, z));
    }

    private SectionView sec(int i) {
        if (i < 0 || i >= sections.length) throw new IndexOutOfBoundsException("섹션 번호 " + i + " (0.." + (sections.length - 1) + ")");
        return sections[i];
    }

    @Override public int[] sectionStates(int i) { return sec(i).states(); }

    @Override public int sectionNonAir(int i) { return sec(i).nonAir; }

    @Override
    public int[] sectionCounts(int i) {
        SectionView s = sec(i);
        return new int[] {s.nonEmpty, s.tickingBlock, s.tickingFluid};
    }

    @Override public int sectionRandomTickingParts(int i) { return sec(i).parts(); }

    @Override public int[] sectionBiomes(int i) { return sec(i).biomeIds(); }

    @Override
    public int firstAvailable(String heightmap, int localX, int localZ) {
        HeightmapType type = HeightmapType.byKey(heightmap.toLowerCase(java.util.Locale.ROOT));
        if (type == null) throw new IllegalArgumentException("모르는 하이트맵: " + heightmap);
        return minY + heights[type.ordinal()][(localZ & 15) * 16 + (localX & 15)];
    }

    @Override public long[] blockEntityPositions() { return blockEntities.clone(); }

    /** 바닐라 BlockPos.asLong. */
    static long packPos(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | (long) y & 0xFFFL;
    }

    @Override
    public String toString() { return "ChunkReadView[v" + version + ", " + sections.length + " sections]"; }
}
