// 엔티티 하나의 지금 상태를 그 자리에서 읽고 쓰는 손잡이 — 이름·이름표·소리·중력·무적·발광·물리·탑승.
// 그 엔티티를 가진 region 스레드에서만 쓴다(다른 스레드에서 부르면 ThreadOwnershipException — MobAi 와 같은 규칙).
// 얻는 곳: Entity.stateNow()(지금 소유 스레드일 때), Entity.editState(다음 틱 소유 스레드에서).
// 쓰기는 바닐라 setter 와 같은 경로라 보는 플레이어 화면에 다음 엔티티 갱신(데이터 동기화·탑승 패킷)으로 반영된다.
// 엔티티가 사라진 뒤(isValid false)의 쓰기는 아무 일도 하지 않고 읽기는 마지막 값이다. 살아 있는 엔티티는 LivingEntityState, 갑옷 거치대는 ArmorStandState 다.
package org.domi.furnace.api.entity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.domi.furnace.api.text.Text;

public interface EntityState {

    UUID uniqueId();

    /** 종류 키(minecraft:armor_stand). */
    String type();

    /** 아직 월드에 있는가. */
    boolean isValid();

    /** 엔티티를 지운다(바닐라 discard — 곧바로 isValid 가 false). 이미 사라졌으면 아무 일도 하지 않는다. */
    void remove();

    /** 지금 위치(월드 키·좌표·회전). Entity.location() 은 핸들을 만든 시점 값이고, 이것은 소유 스레드에서 읽는 지금 값이다. */
    org.domi.furnace.api.world.Location location();

    /** 사용자 지정 이름(없으면 null). */
    Text customName();

    /** 이름을 바꾼다(null 이면 지운다 — 바닐라 setCustomName). */
    void setCustomName(Text name);

    boolean isCustomNameVisible();

    void setCustomNameVisible(boolean visible);

    /** 소리를 내지 않는가(NBT Silent). */
    boolean isSilent();

    void setSilent(boolean silent);

    /** 중력을 받지 않는가(NBT NoGravity). */
    boolean isNoGravity();

    void setNoGravity(boolean noGravity);

    /** 피해를 받지 않는가(NBT Invulnerable — 창작 플레이어·/kill 은 여전히 닿는다). */
    boolean isInvulnerable();

    void setInvulnerable(boolean invulnerable);

    /** 발광 표시(NBT Glowing — 발광 효과와 별개). */
    boolean hasGlowingTag();

    void setGlowingTag(boolean glowing);

    /** 지금 투명 상태 비트(상태 효과·갑옷 거치대 설정이 정한 값). */
    boolean isInvisible();

    /** {@link Entity#noPhysics()} 의 지금 값. */
    boolean noPhysics();

    /** {@link Entity#setNoPhysics} 의 동기판(저장되지 않는다). */
    void setNoPhysics(boolean noPhysics);

    // ------------------------------------------------------------------ 탑승

    /** 지금 탄 승객(타는 순서). 각 항목은 지금 만든 스냅샷 핸들이다. */
    List<Entity> passengers();

    /** 지금 탄 탈것. */
    Optional<Entity> vehicle();

    /**
     * passenger 를 이 엔티티에 태운다 — 바닐라 startRiding(this, force=true): 탈것의 승객 수 제한·웅크림·다시 타기 기다림을 보지 않는다.
     * 이미 탄 것을 다시 태우거나, 자기 자신이나 자기 탈것 사슬에 있는 것을 태우거나, 플러그인이 탑승 이벤트를 취소하면 false.
     * 승객은 같은 region 에 있어야 한다(아니면 ThreadOwnershipException). 승객이 사라졌으면 false.
     */
    boolean addPassenger(Entity passenger);

    /** passenger 가 이 엔티티에 타 있으면 내린다. 내렸으면 true. */
    boolean removePassenger(Entity passenger);

    /** 승객을 모두 내린다. 승객이 있었으면 true. */
    boolean ejectPassengers();

    /** 이 엔티티가 탈것에서 내린다. 타고 있었으면 true. */
    boolean leaveVehicle();
}
