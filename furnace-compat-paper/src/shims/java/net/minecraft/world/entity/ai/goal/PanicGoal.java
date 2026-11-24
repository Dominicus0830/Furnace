package net.minecraft.world.entity.ai.goal;

/** 바닐라 목표 생성자(몹 목표 설계 M2b): 그 자리에서 생성자 몹의 주인 스레드에 서버 목표를 만든다 — {@code NmsGoals#vanilla}. */
class PanicGoal {

    protected PanicGoal(org.domi.furnace.compat.nms.ShimCtor tag) { super(tag); }

    public PanicGoal(net.minecraft.world.entity.PathfinderMob mob, double speed) { super(org.domi.furnace.compat.nms.NmsGoals.vanilla(mob, "PanicGoal", speed)); }

    public PanicGoal(net.minecraft.world.entity.PathfinderMob mob, double speed, net.minecraft.tags.TagKey<net.minecraft.world.damagesource.DamageType> causes) { super(org.domi.furnace.compat.nms.NmsGoals.unsupported("PanicGoal#<init>")); }

    public PanicGoal(net.minecraft.world.entity.PathfinderMob mob, double speed, java.util.function.Function<net.minecraft.world.entity.PathfinderMob, net.minecraft.tags.TagKey<net.minecraft.world.damagesource.DamageType>> causes) { super(org.domi.furnace.compat.nms.NmsGoals.unsupported("PanicGoal#<init>")); }
}
