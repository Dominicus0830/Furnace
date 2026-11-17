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
}
