package net.minecraft.world.entity.ai.goal;

/** 바닐라 목표 생성자(몹 목표 설계 M2b): 그 자리에서 생성자 몹의 주인 스레드에 서버 목표를 만든다 — {@code NmsGoals#vanilla}. */
class AvoidEntityGoal {

    protected AvoidEntityGoal(org.domi.furnace.compat.nms.ShimCtor tag) { super(tag); }

    public AvoidEntityGoal(net.minecraft.world.entity.PathfinderMob mob, Class<T> type, float distance, double walkSpeed, double sprintSpeed) { super(org.domi.furnace.compat.nms.NmsGoals.vanilla(mob, "AvoidEntityGoal", type, distance, walkSpeed, sprintSpeed)); }

    public AvoidEntityGoal(net.minecraft.world.entity.PathfinderMob mob, Class<T> type, java.util.function.Predicate<net.minecraft.world.entity.LivingEntity> avoidFilter, float distance, double walkSpeed, double sprintSpeed, java.util.function.Predicate<net.minecraft.world.entity.LivingEntity> pathFilter) { super(org.domi.furnace.compat.nms.NmsGoals.vanilla(mob, "AvoidEntityGoal", type, org.domi.furnace.compat.nms.NmsGoals.entities(avoidFilter), distance, walkSpeed, sprintSpeed, org.domi.furnace.compat.nms.NmsGoals.entities(pathFilter))); }

    public AvoidEntityGoal(net.minecraft.world.entity.PathfinderMob mob, Class<T> type, float distance, double walkSpeed, double sprintSpeed, java.util.function.Predicate<net.minecraft.world.entity.LivingEntity> pathFilter) { super(org.domi.furnace.compat.nms.NmsGoals.vanilla(mob, "AvoidEntityGoal", type, distance, walkSpeed, sprintSpeed, org.domi.furnace.compat.nms.NmsGoals.entities(pathFilter))); }
}
