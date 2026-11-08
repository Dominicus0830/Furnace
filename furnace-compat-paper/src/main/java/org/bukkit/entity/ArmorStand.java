package org.bukkit.entity;

/**
 * 갑옷 거치대. 플래그는 엔티티를 가진 region 스레드에서 지금 값을 읽고 쓴다(Entity 의 스레드 규칙).
 * 장비·자세(EulerAngle)·장비 잠금·canMove·canTick 은 아직 없다.
 */
public interface ArmorStand extends LivingEntity {

    boolean hasBasePlate();

    void setBasePlate(boolean basePlate);

    /** 보이는가(NBT Invisible 의 반대). */
    boolean isVisible();

    void setVisible(boolean visible);

    boolean hasArms();

    void setArms(boolean arms);

    boolean isSmall();

    void setSmall(boolean small);

    /** 마커: 판정 상자가 없다(클릭·충돌 안 됨). */
    boolean isMarker();

    void setMarker(boolean marker);
}
