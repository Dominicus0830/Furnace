// 청크 티켓 API(불만 표 46번) — Furnace.server().chunkTickets().
// 플러그인이 청크를 로드된 채로 붙잡는다(Paper 의 addPluginChunkTicket 와 같은 뜻, 반경과 틱 여부를 고를 수 있다).
//  - 티켓은 주인 플러그인 몫이다. 같은 청크에 티켓이 여러 개 겹쳐도 되고(세어 둔다), 모두 풀려야 보통 규칙대로 내려간다.
//  - 주인 플러그인이 꺼지면(핫 리로드 포함) 그 플러그인의 티켓은 서버가 모두 놓는다. 꺼진 플러그인의 add 는 IllegalStateException.
//  - 티켓은 저장되지 않는다 — 서버를 다시 켜면 플러그인이 다시 걸어야 한다(/forceload 는 저장된다).
//  - 플레이어 근처가 아니어도 무작위 틱·자연 스폰은 바닐라처럼 플레이어 가까이에서만 돈다(/forceload 와 같다).
package org.domi.furnace.api.world;

import java.util.List;
import org.domi.furnace.api.plugin.FurnacePlugin;

public interface ChunkTickets {

    /** 한 티켓의 반경 상한(청크) — 33x33. */
    int MAX_RADIUS = 16;

    /** 플러그인 하나가 걸어 둘 수 있는 청크 수(겹침 포함) 상한. 넘으면 add 가 IllegalStateException. */
    int MAX_CHUNKS_PER_OWNER = 16384;

    /**
     * (chunkX, chunkZ) 둘레 radius 청크를 붙잡는 티켓을 건다. 아직 안 올라온 청크는 그 청크를 가진 region 이 불러온다(없는 땅은 만든다) —
     * 끝나는 때는 {@link ChunkTicket#loaded()}. 아무 스레드.
     *
     * @throws IllegalArgumentException radius 가 0..{@link #MAX_RADIUS} 밖
     * @throws IllegalStateException 주인이 꺼졌거나 {@link #MAX_CHUNKS_PER_OWNER} 를 넘는다, 또는 내린 월드
     */
    ChunkTicket add(FurnacePlugin owner, World world, int chunkX, int chunkZ, int radius, boolean ticking);

    /** 청크 하나, 엔티티 틱까지(Paper 의 plugin chunk ticket 과 같다). */
    default ChunkTicket add(FurnacePlugin owner, World world, int chunkX, int chunkZ) {
        return add(owner, world, chunkX, chunkZ, 0, true);
    }

    /** owner 가 지금 걸어 둔 티켓 모두(건 순서). */
    List<ChunkTicket> tickets(FurnacePlugin owner);

    /** 그 월드에 걸린 모든 플러그인의 티켓(건 순서). */
    List<ChunkTicket> tickets(World world);

    /** 그 청크를 덮는 티켓 모두(여러 플러그인 것 포함). */
    List<ChunkTicket> ticketsAt(World world, int chunkX, int chunkZ);

    /** 어떤 플러그인이든 그 청크를 티켓으로 붙잡고 있는가. */
    default boolean isHeld(World world, int chunkX, int chunkZ) { return !ticketsAt(world, chunkX, chunkZ).isEmpty(); }

    /** owner 의 티켓을 모두 놓는다(플러그인이 꺼질 때 서버가 저절로 부른다). 놓은 개수. */
    int releaseAll(FurnacePlugin owner);

    /** owner 의 티켓 중 그 월드의 것만 놓는다. 놓은 개수. */
    int releaseAll(FurnacePlugin owner, World world);
}
