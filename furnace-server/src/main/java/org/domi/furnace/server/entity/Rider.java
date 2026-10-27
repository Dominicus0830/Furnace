// 탈것에 탈 수 있는 것(바닐라에서 승객은 Entity, 플레이어도 Entity 다).
// 설계: Furnace 의 플레이어는 Entity 가 아니라서 승객 목록은 이 봉인 인터페이스(Entity 또는 Player)로 담는다.
// 탈것(vehicle)은 언제나 Entity 다 — 플레이어는 승객만 될 수 있다(바닐라 /ride 도 플레이어에 태우지 않는다).
// 승객 목록·탈것 연결은 탈것 엔티티를 가진 region 스레드만 바꾼다. 플레이어의 vehicle 은 IO 스레드가 읽으므로 volatile.
package org.domi.furnace.server.entity;

public sealed interface Rider permits Entity, Player {

    int id();

    double eyeY();

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

    /** startRiding(vehicle, force): 탈것 주인 region 스레드에서만 부른다. */
    boolean startRiding(Entity v, boolean force);

    /** canRide(vehicle): 승객 쪽 조건(웅크리지 않음·다시 타기 기다림 끝). */
    boolean canRide(Entity vehicle);

    /** isShiftKeyDown. */
    boolean isShiftKeyDown();

    /** 바닐라 canControlVehicle: non_controlling_rider 종류가 아니다. */
    default boolean canControlVehicle() { return true; }

    /** 몸 높이(탈것 내릴 자리 계산용). */
    float bbHeight();

    float riderYRot();

    /** 바닐라 Entity.getXRot: 위·아래 각. */
    float riderXRot();

    /** 충돌 상자(선택자의 dx·dy·dz 겹침 판정용). */
    AABB boundingBox();

    /** 지금 있는 차원 키(minecraft:overworld …). */
    String dimensionKey();

    /** 종류 이름(minecraft:player, minecraft:cow …). */
    String typeName();

    /** 바닐라 Entity.getDisplayName. */
    org.domi.furnace.api.text.Text displayName();

    /** 선택자 name= 이 비교하는 이름(커스텀 이름이 없으면 null). 플레이어는 접속 이름. */
    String selectorName();

    /** 바닐라 Entity.getTags — 읽기 전용 사본(순서는 HashSet 순서). */
    java.util.Set<String> getTags();

    /** 바닐라 Entity.addTag: 1024 개를 넘으면 거짓. 새로 넣었으면 참. */
    boolean addTag(String tag);

    /** 바닐라 Entity.removeTag. */
    boolean removeTag(String tag);
}
