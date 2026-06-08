// 직렬화용 불변 청크 카피. region 스레드가 떠서 chunkPool이 락프리로 직렬화한다.
package org.domi.furnace.server.world;

public record ChunkSnapshot(int chunkX, int chunkZ, long[] heightmap, Section[] sections) {

    public record Section(short nonAirCount, int[] blocks, int[] biomes) {}

    /** region 스레드서 호출 — 섹션 블록/바이옴 배열을 카피해 불변 스냅샷 생성. */
    public static ChunkSnapshot of(int chunkX, int chunkZ, ChunkColumn col) {
        ChunkSection[] src = col.sections();
        Section[] out = new Section[src.length];
        for (int i = 0; i < src.length; i++) {
            out[i] = new Section(src[i].nonAirCount(), src[i].blocks().clone(), src[i].biomes().clone());
        }
        return new ChunkSnapshot(chunkX, chunkZ, col.motionBlockingHeightmap(), out);
    }
}
