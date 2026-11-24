package net.minecraft.world.entity.ai.goal.target;

/** 바닐라 목표 생성자(몹 목표 설계 M2b): 그 자리에서 생성자 몹의 주인 스레드에 서버 목표를 만든다 — {@code NmsGoals#vanilla}. */
class HurtByTargetGoal {

    protected HurtByTargetGoal(org.domi.furnace.compat.nms.ShimCtor tag) { super(tag); }

    public HurtByTargetGoal(net.minecraft.world.entity.PathfinderMob mob, Class<?>... ignored) { super(org.domi.furnace.compat.nms.NmsGoals.vanilla(mob, ignored.length == 0 ? "HurtByTargetGoal" : "HurtByTargetGoal(ignored classes)")); }
}
