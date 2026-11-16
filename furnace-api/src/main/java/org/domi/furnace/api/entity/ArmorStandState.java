// 갑옷 거치대의 지금 상태 손잡이. 작음, 마커, 팔, 받침, 투명을 다룬다(NBT Small·Marker·ShowArms·NoBasePlate·Invisible). 같은 소유 스레드 규칙을 따른다.
package org.domi.furnace.api.entity;

public interface ArmorStandState extends LivingEntityState {

    boolean isSmall();

    void setSmall(boolean small);

    /** 마커. 판정 상자가 없어 클릭과 충돌이 안 되고, 물리가 꺼진다. */
    boolean isMarker();

    void setMarker(boolean marker);

    boolean hasArms();

    void setArms(boolean arms);

    boolean hasBasePlate();

    void setBasePlate(boolean basePlate);

    /** 거치대 자신의 투명 설정(NBT Invisible). */
    void setInvisible(boolean invisible);
}
