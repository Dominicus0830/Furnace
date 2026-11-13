// 엔티티(크리퍼·TNT·화염구·위더 등)가 일으킨 폭발이 블록을 부수기 직전 — 폭발 region 스레드에서 불린다.
// 엔티티 피해·밀림은 이미 끝났다(Bukkit 과 같다). blocks() 는 고칠 수 있는 목록이고, 취소하면 블록을 하나도 부수지 않고 폭발 패킷도 보내지 않는다.
// 블록을 부수지 않는 폭발(mobGriefing 거짓 등)은 부르지 않는다.
package org.domi.furnace.api.event.entity;

import java.util.List;
import org.domi.furnace.api.entity.Entity;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.api.world.Location;

public final class EntityExplodeEvent extends EntityEvent implements Cancellable, org.domi.furnace.api.event.cause.Caused {

    private final Location location;
    private final List<BlockPos> blocks;
    private boolean cancelled;
    private org.domi.furnace.api.event.cause.CauseChain cause;
    private float yield = 1.0F;
    private String result = "DESTROY";

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public EntityExplodeEvent(Entity entity, Location location, List<BlockPos> blocks) {
        super(entity);
        this.location = location;
        this.blocks = blocks;
    }

    /** 원인 사슬을 직접 준다(null 이면 터진 엔티티와 그 {@link Entity#origin()}). */
    public EntityExplodeEvent(Entity entity, Location location, List<BlockPos> blocks, org.domi.furnace.api.event.cause.CauseChain cause) {
        this(entity, location, blocks);
        this.cause = cause;
    }

    /** [터진 엔티티 explode] ← 그 엔티티의 origin(켠 플레이어·앞선 TNT·레드스톤 칸 …). */
    /** 서버가 아는 폭발 정보까지 담는 생성자(BlockExplodeEvent 와 같은 모양). */
    public EntityExplodeEvent(Entity entity, Location location, List<BlockPos> blocks, org.domi.furnace.api.event.cause.CauseChain cause,
                              float yield, String result) {
        this(entity, location, blocks, cause);
        this.yield = yield;
        this.result = result;
    }

    /** 블록이 부서질 때 남는 비율(DESTROY_WITH_DECAY 는 1/반지름, 그 밖은 1). 서버는 읽기만 한다 — 고쳐도 적용하지 않는다. */
    public float yield() { return yield; }

    /** 폭발의 블록 상호작용(KEEP·DESTROY·DESTROY_WITH_DECAY·TRIGGER_BLOCK). 기본 DESTROY. */
    public String result() { return result; }

    @Override
    public org.domi.furnace.api.event.cause.CauseChain causeChain() {
        if (cause == null) cause = org.domi.furnace.api.event.cause.CauseChain.of(entity(), org.domi.furnace.api.event.cause.CauseEntry.EXPLODE);
        return cause;
    }

    /** 폭발 중심. */
    public Location location() { return location; }

    /** 부서질(바람 폭발이면 건드릴) 칸 — 빼거나 넣을 수 있다. 넣은 칸도 폭발 저항과 상관없이 부서진다. */
    public List<BlockPos> blocks() { return blocks; }
}
