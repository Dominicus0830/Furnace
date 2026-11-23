package net.minecraft.world.entity;

/** 바닐라 AreaEffectCloud 의 손 몸통. (EntityType, Level) 생성자는 생성기가 모든 엔티티 종류 클래스에 붙인다. */
class AreaEffectCloud {

    /** 바닐라 {@code AreaEffectCloud(level, x, y, z)}: 영역 효과 구름 종류로 만들고 그 자리에 놓는다(아직 월드에 없다). */
    public AreaEffectCloud(net.minecraft.world.level.Level level, double x, double y, double z) {
        this(EntityType.AREA_EFFECT_CLOUD, level);
        setPos(x, y, z);
    }
}
