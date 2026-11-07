// 플러그인이 건 청크 티켓 하나 — 중심 청크 둘레 정사각형을 로드해 둔다. 놓거나(release) 주인 플러그인이 꺼지면 풀린다.
package org.domi.furnace.api.world;

import java.util.concurrent.CompletableFuture;
import org.domi.furnace.api.plugin.FurnacePlugin;

public interface ChunkTicket extends AutoCloseable {

    FurnacePlugin owner();

    World world();

    /** 중심 청크 좌표. */
    int centerX();

    int centerZ();

    /** 중심에서 이만큼(청크) 떨어진 곳까지 — 0 이면 청크 하나, 1 이면 3x3. */
    int radius();

    /** 참이면 그 청크의 엔티티·블록 엔티티가 플레이어가 없어도 틱을 받는다(/forceload 와 같은 단계). 거짓이면 올려 두기만 한다. */
    boolean ticking();

    /** 이 티켓이 덮는 청크 수 — (2*radius+1)^2. */
    default int chunkCount() {
        int n = 2 * radius() + 1;
        return n * n;
    }

    /** 아직 걸려 있는가 — 놓았거나, 주인이 꺼졌거나, 월드를 내렸으면 거짓. 최근 값, 아무 스레드. */
    boolean isActive();

    /** 덮는 청크가 모두 올라오면 끝난다. 일부 청크를 못 불러오면 예외로 끝난다(티켓은 그대로 걸려 있다). 이미 놓은 티켓이면 바로 끝난다. */
    CompletableFuture<Void> loaded();

    /**
     * 티켓을 놓는다. 다른 티켓이나 /forceload 가 잡지 않은 청크는 보통 규칙대로(플레이어가 없고 퇴출 유예가 지나면) 내려간다.
     * 이미 놓았으면 아무 일도 없다. 아무 스레드.
     */
    void release();

    @Override
    default void close() { release(); }

    /** 이 티켓이 그 청크를 덮는가. */
    default boolean covers(int chunkX, int chunkZ) {
        return Math.abs(chunkX - centerX()) <= radius() && Math.abs(chunkZ - centerZ()) <= radius();
    }
}
