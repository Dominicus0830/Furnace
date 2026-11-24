package net.minecraft.world.entity.ai.goal.target;

/** 바닐라 목표 생성자(몹 목표 설계 M2b): 그 자리에서 생성자 몹의 주인 스레드에 서버 목표를 만든다 — {@code NmsGoals#vanilla}. */
class NearestAttackableTargetGoal {

    protected NearestAttackableTargetGoal(org.domi.furnace.compat.nms.ShimCtor tag) { super(tag); }

    public NearestAttackableTargetGoal(net.minecraft.world.entity.Mob mob, Class<T> type, boolean mustSee) { super(org.domi.furnace.compat.nms.NmsGoals.vanilla(mob, "NearestAttackableTargetGoal", type, mustSee)); }

    public NearestAttackableTargetGoal(net.minecraft.world.entity.Mob mob, Class<T> type, boolean mustSee, java.util.function.Predicate<net.minecraft.world.entity.LivingEntity> filter) { super(org.domi.furnace.compat.nms.NmsGoals.unsupported("NearestAttackableTargetGoal#<init>")); }

    public NearestAttackableTargetGoal(net.minecraft.world.entity.Mob mob, Class<T> type, boolean mustSee, boolean mustReach) { super(org.domi.furnace.compat.nms.NmsGoals.vanilla(mob, "NearestAttackableTargetGoal", type, mustSee, mustReach)); }

    public NearestAttackableTargetGoal(net.minecraft.world.entity.Mob mob, Class<T> type, int interval, boolean mustSee, boolean mustReach, java.util.function.Predicate<net.minecraft.world.entity.LivingEntity> filter) { super(org.domi.furnace.compat.nms.NmsGoals.unsupported("NearestAttackableTargetGoal#<init>")); }
}
