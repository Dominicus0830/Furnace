// 바닐라 Leashable(btk): 가죽끈에 매일 수 있는 엔티티(몹과 배). 울타리 매듭·끈 아이템 처리가 공통으로 쓰는 표면만 둔다.
package org.domi.furnace.server.entity;

public interface Leashable {
    /** 붙잡은 쪽(플레이어·매듭 등). 없으면 null. */
    Rider getLeashHolder();

    /** N_: 붙잡은 쪽이 있다. */
    boolean isLeashed();

    /** q(): 맬 수 있고 아직 안 매였다. */
    boolean canHaveALeashAttachedToIt();

    /** b(holder, broadcast): holder 에 맨다. 타고 있던 탈것에서는 내린다. */
    void setLeashedTo(Rider holder, boolean broadcast);

    /** a(broadcast, dropItem): 풀고 끈 아이템을 떨군다. */
    void dropLeash(boolean broadcast, boolean dropItem);

    /** Entity.interact 의 끈 부분: 내가 맨 끈이면 풀고, 끈 아이템을 들었고 맬 수 있으면 맨다. 처리했으면 true. */
    boolean interactLeash(Player p, int hand, ItemStack held);
}
