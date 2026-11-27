// 투사체(화살·눈덩이·불덩이·낚시찌·셜커 탄환·바람 돌격 등)의 지금 상태 손잡이. 같은 소유 스레드 규칙을 따른다.
package org.domi.furnace.api.entity;

public interface ProjectileState extends EntityState {

    /**
     * 이 투사체가 그 엔티티를 맞출 수 있는가(Paper Projectile#canHitEntity — 종류마다 바닐라 규칙: 관통한 엔티티 제외, 물리 없는 대상 제외,
     * 낚시찌는 아이템도, 바람 돌격은 바람 돌격·엔드 수정 제외, 소유자가 숨긴 플레이어 제외). 대상은 이 투사체와 같은 region 에 있어야 한다.
     */
    boolean canHitEntity(Entity target);
}
