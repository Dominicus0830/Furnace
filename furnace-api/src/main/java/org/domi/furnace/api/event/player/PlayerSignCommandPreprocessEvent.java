// 표지판 클릭(run_command)이 명령을 실행하기 직전 — PlayerCommandPreprocessEvent 의 하위라서 그 처리기도 받는다(Paper 1.21.1 과 같다).
// 취소하면 명령을 돌리지 않고, 메시지를 바꾸면 바뀐 명령으로 돈다. 실행 권한은 표지판 소스(권한 2) 그대로다 — 플레이어가 평소 못 치는 명령도 표지판으로는 돈다.
// 불리는 스레드: 그 표지판 칸을 가진 region 스레드.
package org.domi.furnace.api.event.player;

import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.world.BlockPos;

public final class PlayerSignCommandPreprocessEvent extends PlayerCommandPreprocessEvent {

    private final String world;
    private final BlockPos pos;
    private final boolean front;

    public PlayerSignCommandPreprocessEvent(Player player, String message, String world, BlockPos pos, boolean front) {
        super(player, message);
        this.world = world;
        this.pos = pos;
        this.front = front;
    }

    /** 표지판이 있는 차원 키(minecraft:overworld). */
    public String world() { return world; }

    public BlockPos pos() { return pos; }

    /** 클릭한 면이 앞면인가. */
    public boolean front() { return front; }
}
