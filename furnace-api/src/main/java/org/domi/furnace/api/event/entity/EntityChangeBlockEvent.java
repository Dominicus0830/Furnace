// 엔티티(플레이어 포함)가 블록 하나를 바꾸기 직전 — 엔더맨이 집거나 놓기, 떨어지는 블록이 떨어지기 시작·내려앉기, 양이 풀 먹기, 좀벌레가 숨거나 깨기,
// 토끼·여우·벌·주민·거북·개구리·파괴수·위더가 블록을 바꾸기, 투사체가 블록을 깨기, 번개가 구리를 벗기기 등(Paper 1.21.1 의 EntityChangeBlockEvent 자리).
// 바꿀 블록을 가진 region 스레드에서 불린다. 취소하면 그 블록은 바뀌지 않는다(자리마다 Paper 와 같은 뒤처리를 한다 — 예: 떨어지는 블록은 사라진다).
// 게임 규칙이 이미 막는 경우(mobGriefing 거짓의 양·파괴수·불붙은 몹과 가루눈)는 Paper 처럼 취소된 채로 불린다 — 처리기가 취소를 풀면 바뀐다.
// 바꿀 상태(newData)는 고칠 수 없다(Bukkit 과 같다).
package org.domi.furnace.api.event.entity;

import org.domi.furnace.api.entity.Entity;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;

public class EntityChangeBlockEvent extends EntityEvent implements Cancellable {

    private final String world;
    private final BlockPos pos;
    private final BlockData oldData;
    private final BlockData newData;
    private boolean cancelled;

    public EntityChangeBlockEvent(Entity entity, String world, BlockPos pos, BlockData oldData, BlockData newData) {
        this(entity, world, pos, oldData, newData, false);
    }

    /** cancelled: 미리 취소된 채로 부른다(게임 규칙이 막는 경우). */
    public EntityChangeBlockEvent(Entity entity, String world, BlockPos pos, BlockData oldData, BlockData newData, boolean cancelled) {
        super(entity);
        this.world = world;
        this.pos = pos;
        this.oldData = oldData;
        this.newData = newData;
        this.cancelled = cancelled;
    }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    /** 바뀔 블록의 차원 키. */
    public String world() { return world; }

    public BlockPos pos() { return pos; }

    /** 지금 그 칸의 블록. */
    public BlockData oldData() { return oldData; }

    /** 바뀐 뒤의 블록(부서지면 그 칸의 유체로 — 물에 잠긴 블록이면 물, 아니면 공기). */
    public BlockData newData() { return newData; }
}
