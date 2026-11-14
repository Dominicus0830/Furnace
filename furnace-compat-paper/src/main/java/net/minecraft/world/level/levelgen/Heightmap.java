package net.minecraft.world.level.levelgen;

import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * 하이트맵 shim(Mojang 이름, N2a): 로드된 청크의 하이트맵 <b>보기</b> — 읽을 때마다 서버 청크의 지금 값을 본다(소유 region 스레드 전용).
 * 월드젠 전용 종류(*_WG)는 바닐라 LevelChunk 처럼 남아 있지 않아 비어 있는(모든 칸 = 최저 높이) 하이트맵이다.
 * 쓰기(update·setRawData)와 따로 만드는 생성자는 없다(N2b) — 그 호출은 UnsupportedNmsCall.
 */
public class Heightmap {

    private final ChunkAccess chunk;
    private final Types type;

    private Heightmap(ChunkAccess chunk, Types type, boolean view) {
        this.chunk = java.util.Objects.requireNonNull(chunk, "chunk");
        this.type = java.util.Objects.requireNonNull(type, "type");
    }

    /** 호환 모듈 전용: 청크의 하이트맵 보기. */
    public static Heightmap furnaceView(ChunkAccess chunk, Types type) { return new Heightmap(chunk, type, true); }

    /** 그 칸(청크 안 x·z, 0~15) 위로 처음 비는 y — 가장 높은 해당 블록 y + 1. 빈 기둥이면 최저 높이. */
    public int getFirstAvailable(int x, int z) {
        if (!type.keepAfterWorldgen()) return chunk.getMinBuildHeight();
        return chunk.furnaceFirstAvailable(type, x & 15, z & 15);
    }

    public int getHighestTaken(int x, int z) { return getFirstAvailable(x, z) - 1; }

    /**
     * 바닐라 저장·패킷 꼴(SimpleBitStorage: 칸 256개, 칸당 ceil(log2(높이 + 1)) 비트, long 을 넘지 않게 채움, 값 = 첫 빈 y - 최저 높이).
     * 새 배열이다 — 고쳐도 청크에 닿지 않는다(바닐라는 내부 배열을 준다).
     */
    public long[] getRawData() {
        int min = chunk.getMinBuildHeight();
        int bits = 32 - Integer.numberOfLeadingZeros(chunk.getHeight());   // Mth.ceillog2(height + 1)
        int perLong = 64 / bits;
        long[] out = new long[(256 + perLong - 1) / perLong];
        long mask = (1L << bits) - 1L;
        for (int i = 0; i < 256; i++) {
            long v = (getFirstAvailable(i & 15, i >> 4) - min) & mask;
            out[i / perLong] |= v << ((i % perLong) * bits);
        }
        return out;
    }

    /** 바닐라 Heightmap.Types — 이름·직렬화 키·용도(월드젠/살아 있는 월드/클라이언트)가 같다. */
    public enum Types implements net.minecraft.util.StringRepresentable {
        WORLD_SURFACE_WG("WORLD_SURFACE_WG", Usage.WORLDGEN),
        WORLD_SURFACE("WORLD_SURFACE", Usage.CLIENT),
        OCEAN_FLOOR_WG("OCEAN_FLOOR_WG", Usage.WORLDGEN),
        OCEAN_FLOOR("OCEAN_FLOOR", Usage.LIVE_WORLD),
        MOTION_BLOCKING("MOTION_BLOCKING", Usage.CLIENT),
        MOTION_BLOCKING_NO_LEAVES("MOTION_BLOCKING_NO_LEAVES", Usage.LIVE_WORLD);

        private final String serializationKey;
        private final Usage usage;

        Types(String serializationKey, Usage usage) {
            this.serializationKey = serializationKey;
            this.usage = usage;
        }

        public String getSerializationKey() { return serializationKey; }

        public boolean sendToClient() { return usage == Usage.CLIENT; }

        public boolean keepAfterWorldgen() { return usage != Usage.WORLDGEN; }

        @Override
        public String getSerializedName() { return serializationKey; }
    }

    public enum Usage { WORLDGEN, LIVE_WORLD, CLIENT }
}
