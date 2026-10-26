// 탈것에 탈 수 있는 것(바닐라에서 승객은 Entity, 플레이어도 Entity 다).
// 설계: Furnace 의 플레이어는 Entity 가 아니라서 승객 목록은 이 봉인 인터페이스(Entity 또는 Player)로 담는다.
// 탈것(vehicle)은 언제나 Entity 다 — 플레이어는 승객만 될 수 있다(바닐라 /ride 도 플레이어에 태우지 않는다).
// 승객 목록·탈것 연결은 탈것 엔티티를 가진 region 스레드만 바꾼다. 플레이어의 vehicle 은 IO 스레드가 읽으므로 volatile.
package org.domi.furnace.server.entity;

public sealed interface Rider permits Entity, Player {

    int id();

    double x();

    double y();

    double z();

    /** 지금 탄 탈것(없으면 null). */
    Entity vehicle();

    boolean isPlayer();

    default boolean isPassenger() { return vehicle() != null; }

    /** 바닐라 승객 쪽 getVehicleAttachmentPoint(vehicle): 승객 발에서 탈것에 닿는 점까지. */
    Vec3 vehicleAttachmentPoint(Entity vehicle);

    /** positionRider 의 MoveFunction(Entity::setPos). */
    void setRidingPos(double x, double y, double z);

    /** 탈것이 바꾸는 연결(바닐라 Entity.vehicle 필드 쓰기). Entity 의 startRiding/removeVehicle 만 부른다. */
    void setVehicleField(Entity vehicle);

    /** 내릴 때(removePassenger) 다시 타기까지 기다림(boardingCooldown = 60). */
    void setBoardingCooldown(int ticks);

    /** stopRiding: 탈것에서 내린다(살아 있는 것은 내릴 자리로 옮긴다). */
    void stopRiding();

    /** canRide(vehicle): 승객 쪽 조건(웅크리지 않음·다시 타기 기다림 끝). */
    boolean canRide(Entity vehicle);

    /** isShiftKeyDown. */
    boolean isShiftKeyDown();

    /** 바닐라 canControlVehicle: non_controlling_rider 종류가 아니다. */
    default boolean canControlVehicle() { return true; }

    /** 몸 높이(탈것 내릴 자리 계산용). */
    float bbHeight();

    float riderYRot();
}
