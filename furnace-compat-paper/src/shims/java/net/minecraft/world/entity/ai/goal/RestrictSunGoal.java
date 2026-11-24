package net.minecraft.world.entity.ai.goal;

/** 바닐라 목표 생성자(몹 목표 설계 M2b): 그 자리에서 생성자 몹의 주인 스레드에 서버 목표를 만든다 — {@code NmsGoals#vanilla}. */
class RestrictSunGoal {

    protected RestrictSunGoal(org.domi.furnace.compat.nms.ShimCtor tag) { super(tag); }

    public RestrictSunGoal(net.minecraft.world.entity.PathfinderMob mob) { super(org.domi.furnace.compat.nms.NmsGoals.vanilla(mob, "RestrictSunGoal")); }
}
