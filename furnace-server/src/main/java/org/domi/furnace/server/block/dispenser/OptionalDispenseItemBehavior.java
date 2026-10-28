// 바닐라 OptionalDispenseItemBehavior: 성공이면 1000, 아니면 1001. 성공 표시는 동작 객체에 남아 다음 발사로 이어진다(바닐라 그대로 —
// 표시를 안 바꾸는 경로는 지난 값을 쓴다). region 스레드가 여럿이라 스레드마다 따로 둔다.
package org.domi.furnace.server.block.dispenser;

public abstract class OptionalDispenseItemBehavior extends DefaultDispenseItemBehavior {

    private final ThreadLocal<boolean[]> success = ThreadLocal.withInitial(() -> new boolean[] {true});

    public boolean isSuccess() { return success.get()[0]; }

    public void setSuccess(boolean v) { success.get()[0] = v; }

    @Override
    protected void playSound(BlockSource source) { source.level().levelEvent(null, isSuccess() ? 1000 : 1001, source.pos(), 0); }
}
