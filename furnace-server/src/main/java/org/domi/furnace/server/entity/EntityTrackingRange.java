// Spigot·Paper TrackingRange.getEntityTrackingRange(T-122): 엔티티 종류별 추적 거리(블록). 설정이 꺼져 있으면 바닐라(clientTrackingRange × 16) 그대로.
// 플레이어는 Furnace 에서 Entity 가 아니라 Region 의 플레이어 보임 판정이 EntityHandleSettings.playerRange() 를 직접 쓴다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.config.EntityHandleSettings;

public final class EntityTrackingRange {

    private EntityTrackingRange() {}

    /**
     * Paper 1.21.1(Tracking-Range-Improvements): 바닐라 거리가 0 이면 0(추적 안 함). 활성 범위 종류(생성 때 정한 activationType)로
     * 습격 몹·적대 몹·나는 적대 몹 = monsters, 물 동물·주민·동물 = animals. 그 밖(MISC)은 아이템·경험치 구슬·그림·아이템 액자 = misc,
     * 디스플레이 = display, 나머지 = other. Paper 의 "엔더 드래곤은 서버 시야" 예외는 드래곤이 적대 몹(Enemy)이라 monsters 에서 먼저 끝나
     * 닿지 않는다 — Furnace 도 Paper 와 같게 드래곤은 monsters 다.
     */
    public static int of(Entity e, int vanillaRange, EntityHandleSettings s) {
        if (vanillaRange == 0 || !s.trackingEnabled()) return vanillaRange;
        switch (e.activationType) {
            case EntityActivation.RAIDER, EntityActivation.MONSTER, EntityActivation.FLYING_MONSTER -> {
                return s.trackMonsters();
            }
            case EntityActivation.WATER, EntityActivation.VILLAGER, EntityActivation.ANIMAL -> {
                return s.trackAnimals();
            }
            default -> { }
        }
        if (e instanceof ItemFrame || e instanceof Painting || e instanceof ItemEntity || e instanceof ExperienceOrb) return s.trackMisc();
        if (e instanceof Display) return s.trackDisplay();
        return s.trackOther();
    }
}
