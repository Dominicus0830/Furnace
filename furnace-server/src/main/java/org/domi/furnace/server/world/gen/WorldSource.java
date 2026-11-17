// 청크 컬럼을 생성하는 월드 소스. superflat이 첫 구현, Anvil이 M4서 두 번째 구현.
package org.domi.furnace.server.world.gen;

import org.domi.furnace.server.world.ChunkColumn;

public interface WorldSource {

    ChunkColumn generate(int chunkX, int chunkZ);

    /**
     * 같은 생성 설정(노이즈 설정·차원·고정 바이옴·평지 층·구조물 켬/끔·플러그인 월드젠 훅)에 시드만 seed 로 한 <b>처음 상태의</b> 생성기(T-203 임시 월드).
     * 이미 만든 청크·장식 저장소를 나누지 않으므로, 돌려준 생성기로 만든 청크는 그 시드의 새 월드에서 처음 만드는 청크와 같다. 디스크 원천은 그 밑 생성기를 복사한다.
     * 만들 수 없는 원천(시험용 등)은 UnsupportedOperationException.
     */
    default WorldSource freshCopy(long seed) {
        throw new UnsupportedOperationException("this world generator cannot be copied: " + getClass().getName());
    }

    /**
     * {@link #freshCopy(long)} 에 바이옴 원천을 biomes 로 바꾼 것(T-203 3단계, CraftBukkit CustomWorldChunkManager). null 이면 freshCopy(seed).
     * 바이옴 원천을 바꿀 수 없는 생성기(평지·디버그 등)는 UnsupportedOperationException.
     */
    /**
     * 바닐라 BiomeSource#getNoiseBiome(quart 좌표, 기후 표본): 생성기가 그 칸에 정하는 바이옴 레지스트리 번호(청크에 저장된 것이 아니라 생성기 값 —
     * Level#getUncachedNoiseBiome). 아무 스레드. 모르는 생성기는 UnsupportedOperationException.
     */
    default int noiseBiome(int quartX, int quartY, int quartZ) {
        throw new UnsupportedOperationException("this world generator has no biome source: " + getClass().getName());
    }

    default WorldSource freshCopy(long seed, org.domi.furnace.api.worldgen.BiomeProvider biomes) {
        if (biomes == null) return freshCopy(seed);
        throw new UnsupportedOperationException("a biome provider needs a noise generator, not " + getClass().getSimpleName());
    }
}
