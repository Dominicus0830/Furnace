package net.minecraft.world.level.levelgen;

import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * 하이트맵 shim(Mojang 이름, N2a): 로드된 청크의 하이트맵 <b>보기</b> — 읽을 때마다 서버 청크의 지금 값을 본다(소유 region 스레드 전용).
 * 월드젠 전용 종류(*_WG)는 바닐라 LevelChunk 처럼 남아 있지 않아 비어 있는(모든 칸 = 최저 높이) 하이트맵이다.
 * 살아 있는 네 종류의 {@link #setRawData}·{@link #update}(N2b-2)는 바닐라 그대로 서버 하이트맵에 쓴다 — 서버 청크는 고정 하이트맵 모드로 들어가 그 뒤로는
 * 바닐라 증분 갱신으로만 바뀐다(블록에서 다시 계산하지 않는다). 저장 표시는 하지 않는다. 주인이 아닌 스레드의 쓰기는 거울 일지에 쌓여 주인이 섹션 쓰기와 같은
 * 순서로 재생한다(N2b-4, 보통 같은 틱 안). 그 스레드의 하이트맵 읽기는 재생·공개 전까지 공개 스냅샷이고(하이트맵 덧칠은 없다), {@code update} 의 돌려줌은
 * false 다(주인이 재생할 때의 결과를 그 자리에서 알 수 없다). 월드젠 종류에 쓰기와 따로 만드는 생성자는 없다(N2b-6 — FAWE 는 쓰지 않는다).
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
     * 바닐라 Heightmap.update: 주어진 상태가 이 종류의 술어를 만족하고 y 가 지금 높이 이상이면 y + 1, 지금 맨 위 칸(높이 - 1)이 술어를 잃으면 아래로 훑어
     * 다시 찾는다. 바뀌면 true. 칸은 바닐라처럼 x + z*16 으로 고른다(자르지 않는다 — 범위 밖이면 바닐라 SimpleBitStorage 처럼 IllegalArgumentException).
     */
    public boolean update(int x, int y, int z, net.minecraft.world.level.block.state.BlockState state) {
        if (!type.keepAfterWorldgen()) throw wgWrite("update");
        int index = x + z * 16;
        if (index < 0 || index > 255) {
            throw new IllegalArgumentException(String.format("The value %s is not in the specified inclusive range of %s to %s", (long) index, 0L, 255L));
        }
        return chunk.furnaceHeightmapUpdate(type, index & 15, y, index >> 4, state.furnaceStateId());
    }

    /**
     * 바닐라 Heightmap.setRawData: 길이가 바닐라 배열과 같으면 값을 그대로 복사(검사 없음), 다르면 경고를 남기고 이 종류를 지금 블록에서 다시 채운다
     * (찾지 못한 기둥은 옛 값 그대로 — 바닐라 primeHeightmaps). 바닐라처럼 복사는 이 하이트맵에, 다시 채우기는 <b>인자로 받은</b> 청크·종류에 한다.
     */
    public void setRawData(ChunkAccess chunk, Types type, long[] rawData) {
        if (!this.type.keepAfterWorldgen()) throw wgWrite("setRawData");
        int bits = 32 - Integer.numberOfLeadingZeros(this.chunk.getHeight());   // getRawData 와 같은 배치
        int perLong = 64 / bits;
        if (rawData.length == (256 + perLong - 1) / perLong || (chunk == this.chunk && type == this.type)) {
            this.chunk.furnaceSetHeightmapRaw(this.type, rawData);
        } else {
            if (!type.keepAfterWorldgen()) throw wgWrite("setRawData");
            chunk.furnaceSetHeightmapRaw(type, rawData);   // 길이가 틀려 서버가 그 청크의 그 종류를 prime(경고 포함)
        }
    }

    private RuntimeException wgWrite(String what) {
        return new org.domi.furnace.compat.nms.UnsupportedNmsCall("Heightmap#" + what + " (" + type.getSerializationKey() + ")",
                "로드된 청크의 월드젠 하이트맵(*_WG)은 바닐라에서도 생성이 끝나면 남지 않는다 — 살아 있는 네 종류는 된다");
    }

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
        if (!type.keepAfterWorldgen()) return out;   // 월드젠 종류는 비어 있다(모든 칸 = 최저 높이 → 0)
        int[] first = chunk.furnaceFirstAvailableAll(type);   // 한 판에서(다른 스레드면 공개 스냅샷 하나)
        for (int i = 0; i < 256; i++) {
            long v = (first[i] - min) & mask;
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
