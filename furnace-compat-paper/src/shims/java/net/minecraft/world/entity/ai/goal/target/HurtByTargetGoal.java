package net.minecraft.world.entity.ai.goal.target;

/** 바닐라 목표 생성자(몹 목표 설계 M2b): 그 자리에서 생성자 몹의 주인 스레드에 서버 목표를 만든다 — {@code NmsGoals#vanilla}. */
class HurtByTargetGoal {

    protected HurtByTargetGoal(org.domi.furnace.compat.nms.ShimCtor tag) { super(tag); }

    public HurtByTargetGoal(net.minecraft.world.entity.PathfinderMob mob, Class<?>... ignored) {
        super(ignored.length == 0 ? org.domi.furnace.compat.nms.NmsGoals.vanilla(mob, "HurtByTargetGoal") : org.domi.furnace.compat.nms.NmsGoals.vanilla(mob, "HurtByTargetGoal", org.domi.furnace.compat.nms.NmsGoals.classes("HurtByTargetGoal#<init>", ignored)));
    }

    /** 바닐라처럼 같은 종류 몹에게 알리고, 정확히 그 클래스인 몹은 뺀다(빈 목록이면 아무도 빼지 않는다). 넣기 전 몹이면 기록해 넣을 때 재생한다. */
    public HurtByTargetGoal setAlertOthers(Class<?>... ignored) {
        String what = "HurtByTargetGoal#setAlertOthers";
        org.domi.furnace.compat.nms.NmsGoals.configure(this, what, org.domi.furnace.compat.nms.NmsGoals.exactClasses(what, ignored));
        return this;
    }
}
