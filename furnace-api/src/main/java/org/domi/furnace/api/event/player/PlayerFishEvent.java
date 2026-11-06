// 낚시 상태가 바뀔 때 — 낚시찌를 던질 때, 물고기가 다가오고 물 때, 거둘 때. 낚시찌의 region 스레드에서 불린다.
//  FISHING: 낚싯대를 던지기 직전(취소하면 낚시찌가 생기지 않는다). LURED: 물고기가 다가오기 시작할 때(취소하면 이번 입질은 건너뛴다).
//  BITE: 물고기가 물 때(취소하면 물지 않는다). FAILED_ATTEMPT: 물고기가 문 채로 놓쳐 입질이 끝났을 때.
//  CAUGHT_FISH: 거둬서 전리품을 얻을 때 — 전리품마다 불리고 caught() 는 아직 월드에 없는 아이템이며 expToDrop 을 바꿀 수 있다(취소하면 낚시찌가 남고 아무것도 얻지 못한다).
//  CAUGHT_ENTITY: 엔티티가 걸린 채 거둘 때(취소하면 끌어당기지 않고 낚시찌가 남는다). IN_GROUND: 땅에 박힌 채 거둘 때. REEL_IN: 아무것도 걸리지 않고 거둘 때.
//  거두는 상태를 취소하면 낚시찌가 사라지지 않는다.
package org.domi.furnace.api.event.player;

import org.domi.furnace.api.entity.Entity;
import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.event.Cancellable;

public final class PlayerFishEvent extends PlayerEvent implements Cancellable {

    public enum State { FISHING, CAUGHT_FISH, CAUGHT_ENTITY, IN_GROUND, FAILED_ATTEMPT, REEL_IN, BITE, LURED }

    private final Entity caught;
    private final Entity hook;
    private final State state;
    private int expToDrop;
    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public PlayerFishEvent(Player player, Entity caught, Entity hook, State state, int expToDrop) {
        super(player);
        this.caught = caught;
        this.hook = hook;
        this.state = state;
        this.expToDrop = expToDrop;
    }

    /** 얻은 아이템(CAUGHT_FISH) 또는 걸린 엔티티(CAUGHT_ENTITY), 그 밖에는 null. */
    public Entity caught() { return caught; }

    /** 낚시찌 엔티티(FISHING 에서는 아직 월드에 없다). */
    public Entity hook() { return hook; }

    public State state() { return state; }

    /** CAUGHT_FISH 에서 떨어뜨릴 경험치(0 이면 구슬이 없다). */
    public int expToDrop() { return expToDrop; }

    public void setExpToDrop(int expToDrop) { this.expToDrop = Math.max(0, expToDrop); }
}
