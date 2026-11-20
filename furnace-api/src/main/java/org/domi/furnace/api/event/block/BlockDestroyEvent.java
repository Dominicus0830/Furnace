// 블록이 "부서져" 사라지기 직전(바닐라 Level#destroyBlock 을 타는 모든 길 — 선인장·사탕수수가 받침을 잃을 때, 물이 덮칠 때, 엔티티가 부술 때,
// /setblock … destroy, 플러그인 destroyBlock 등). 플레이어가 손으로 부수는 것은 BlockBreakEvent 다. 그 칸의 region 스레드에서 불린다.
// 취소하면 블록이 그대로다. 부서지는 효과 블록·효과 끄기·떨굼·경험치를 바꿀 수 있다(Paper BlockDestroyEvent 와 같다).
// 구독자가 있으면 서버는 Paper 순서를 쓴다: 경험치를 이벤트 전에 먼저 뽑는다(떨굼이 꺼져 있어도 — 경험치 블록은 레벨 난수를 쓴다).
// 구독자가 없으면 바닐라 순서(떨굼 안에서 경험치)다.
package org.domi.furnace.api.event.block;

import java.util.Objects;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;

public final class BlockDestroyEvent extends BlockEvent implements Cancellable {

    private final BlockData oldData;
    private final BlockData newData;
    private BlockData effectData;
    private boolean playEffect = true;
    private boolean willDrop;
    private int expToDrop;
    private boolean cancelled;

    public BlockDestroyEvent(String world, BlockPos pos, BlockData oldData, BlockData newData, int expToDrop, boolean willDrop) {
        super(world, pos);
        this.oldData = Objects.requireNonNull(oldData, "oldData");
        this.newData = Objects.requireNonNull(newData, "newData");
        this.effectData = oldData;
        this.expToDrop = expToDrop;
        this.willDrop = willDrop;
    }

    /** 부서지는 블록. */
    public BlockData oldData() { return oldData; }

    /** 부서진 뒤 그 칸(잠겨 있던 블록이면 물, 아니면 공기). */
    public BlockData newData() { return newData; }

    /** 부서지는 효과(입자·소리, 레벨 이벤트 2001)에 쓸 블록. 처음엔 부서지는 블록. 불 종류면 효과를 내지 않는다(바닐라). */
    public BlockData effectData() { return effectData; }

    public void setEffectData(BlockData effectData) { this.effectData = Objects.requireNonNull(effectData, "effectData"); }

    /** 부서지는 효과를 낼까(처음엔 참). */
    public boolean playEffect() { return playEffect; }

    public void setPlayEffect(boolean playEffect) { this.playEffect = playEffect; }

    /** 떨굼(아이템·경험치)을 낼까. 처음 값은 부수는 쪽이 떨굼을 원했는가. */
    public boolean willDrop() { return willDrop; }

    public void setWillDrop(boolean willDrop) { this.willDrop = willDrop; }

    /** 떨굼이 켜져 있으면 낼 경험치. 0 이하면 내지 않는다. 게임 규칙 doTileDrops 가 거짓이면 내지 않는다. */
    public int expToDrop() { return expToDrop; }

    public void setExpToDrop(int expToDrop) { this.expToDrop = expToDrop; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
