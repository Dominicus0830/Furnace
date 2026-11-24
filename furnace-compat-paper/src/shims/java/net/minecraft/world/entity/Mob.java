package net.minecraft.world.entity;

/**
 * 몹 shim(몹 목표 설계 M1). 두 선택기는 서버 몹의 선택기를 보는 shim 이다 — 바닐라처럼 공개 필드라 플러그인이 바로 읽는다
 * (필드 자체를 새 선택기로 바꿔 끼우는 것은 서버에 닿지 않는다: 지원 밖).
 */
class Mob {

    public net.minecraft.world.entity.ai.goal.GoalSelector goalSelector;
    public net.minecraft.world.entity.ai.goal.GoalSelector targetSelector;

    protected Mob(org.domi.furnace.compat.nms.ShimCtor tag) {
        super(tag);
        goalSelector = org.domi.furnace.compat.nms.NmsGoals.selector(this, false);
        targetSelector = org.domi.furnace.compat.nms.NmsGoals.selector(this, true);
    }

    public boolean isNoAi() {
        org.domi.furnace.compat.nms.NmsEntityOwner.read(this, "Mob#isNoAi");
        return org.domi.furnace.compat.nms.NmsGoals.on(this, "Mob#isNoAi", g -> g.isNoAi());
    }

    public void setNoAi(boolean noAi) {
        org.domi.furnace.compat.nms.NmsEntityOwner.write(this, "Mob#setNoAi");
        org.domi.furnace.compat.nms.NmsGoals.on(this, "Mob#setNoAi", g -> {
            g.setNoAi(noAi);
            return null;
        });
    }
}
