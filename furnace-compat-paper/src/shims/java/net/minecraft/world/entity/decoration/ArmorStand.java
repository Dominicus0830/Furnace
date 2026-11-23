package net.minecraft.world.entity.decoration;

/** 바닐라 ArmorStand 의 손 몸통. (EntityType, Level) 생성자는 생성기가 모든 엔티티 종류 클래스에 붙인다. */
class ArmorStand {

    /** 바닐라 {@code ArmorStand(level, x, y, z)}: 갑옷 거치대 종류로 만들고 그 자리에 놓는다(아직 월드에 없다). */
    public ArmorStand(net.minecraft.world.level.Level level, double x, double y, double z) {
        this(net.minecraft.world.entity.EntityType.ARMOR_STAND, level);
        setPos(x, y, z);
    }
}
