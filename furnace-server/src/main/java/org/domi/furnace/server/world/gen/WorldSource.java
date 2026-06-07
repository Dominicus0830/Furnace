// 청크 컬럼을 생성하는 월드 소스. superflat이 첫 구현, Anvil이 M4서 두 번째 구현.
package org.domi.furnace.server.world.gen;

import org.domi.furnace.server.world.ChunkColumn;

public interface WorldSource {

    ChunkColumn generate(int chunkX, int chunkZ);
}
