// 오라클↔테스트 공유 fixture 바이너리 포맷. 카테고리 바이트: 0=air 1=stone 2=water 3=lava 4=other
package org.domi.oracle;

public final class FixtureFormat {
    private FixtureFormat() {}
    public static final byte AIR = 0, STONE = 1, WATER = 2, LAVA = 3, OTHER = 4;
    public static final int MIN_Y = -64, HEIGHT = 384; // y: MIN_Y .. MIN_Y+HEIGHT-1
    // CHUNK fixture "chunk_<seed>_<cx>_<cz>.bin": little-endian.
    //   magic int32 = 0x46584331 ("FXC1"); long seed; int32 cx; int32 cz;
    //   then HEIGHT*16*16 category bytes, index = (y-MIN_Y)*256 + localZ*16 + localX
    // RNG fixture "rng_<seed>.bin": little-endian. int32 count N; then N int64 = XoroshiroRandomSource(seed).nextLong() sequence.
    // SURFACE fixture "surface_<seed>_<cx>_<cz>.bin": little-endian.
    //   magic int32 = 0x53524643 ("SRFC"); long seed; int32 cx; int32 cz;
    //   then HEIGHT*16*16 int16 (block state id, unsigned),
    //   index = (y-MIN_Y)*256 + localZ*16 + localX
    public static final int SURFACE_MAGIC = 0x53524643;
    public static int blockIndex(int localX, int y, int localZ) {
        return (y - MIN_Y) * 256 + localZ * 16 + localX;
    }
}
