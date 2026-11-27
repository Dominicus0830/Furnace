package net.minecraft.world.entity.ai.goal;

/**
 * 바닐라 목표 생성자(몹 목표 설계 M2b): 그 자리에서 생성자 몹의 주인 스레드에 서버 목표를 만든다 — {@code NmsGoals#vanilla}. 아이템 판정은
 * {@code NmsGoals#items}(Ingredient 는 아이템 종류 집합, 그 밖의 판정은 주인 스레드에서 스택 사본으로).
 */
class TemptGoal {

    protected TemptGoal(org.domi.furnace.compat.nms.ShimCtor tag) { super(tag); }

    public TemptGoal(net.minecraft.world.entity.PathfinderMob mob, double speed, java.util.function.Predicate<net.minecraft.world.item.ItemStack> items, boolean canScare) {
        super(org.domi.furnace.compat.nms.NmsGoals.vanilla(mob, "TemptGoal", speed, org.domi.furnace.compat.nms.NmsGoals.items(items), canScare));
    }
}
