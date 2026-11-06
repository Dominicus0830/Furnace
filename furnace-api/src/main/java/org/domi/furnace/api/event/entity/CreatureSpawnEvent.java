// 살아 있는 것(플레이어 제외)이 월드에 들어가기 직전 — 넣는 레벨의 region 스레드에서 불린다. EntitySpawnEvent 의 하위라 그 이벤트로 받아도 온다.
// reason() 은 스폰 이유로, 서버가 모르는 경로(위 목록에 없는 곳에서 넣은 것)는 DEFAULT 다. 취소하면 넣지 않는다.
package org.domi.furnace.api.event.entity;

import org.domi.furnace.api.entity.Entity;

public final class CreatureSpawnEvent extends EntitySpawnEvent {

    public enum SpawnReason {
        /** 자연 스폰(몹 상한 안에서 주기적으로, 고양이·팬텀·떠돌이 상인 포함). */
        NATURAL,
        /** 닭 기수·거미 기수처럼 다른 몹을 타고 나타남. */
        JOCKEY,
        /** 청크가 만들어질 때 함께 나온 동물. */
        CHUNK_GEN,
        /** 몬스터 생성기·생성기 수레. */
        SPAWNER,
        /** 시련 생성기. */
        TRIAL_SPAWNER,
        /** 던진 달걀이 부화함·거북 알이 부화함. */
        EGG,
        /** 스폰 알을 사용함. */
        SPAWNER_EGG,
        /** 번개(번개 맞은 돼지·주민이 변함). */
        LIGHTNING,
        BUILD_SNOWMAN,
        BUILD_IRONGOLEM,
        BUILD_WITHER,
        /** 마을을 지키려고 철 골렘이 나타남. */
        VILLAGE_DEFENSE,
        /** 좀비 마을 습격. */
        VILLAGE_INVASION,
        BREEDING,
        SLIME_SPLIT,
        /** 좀비가 지원군을 부름. */
        REINFORCEMENTS,
        /** 발사기가 스폰 알을 사용함. */
        DISPENSE_EGG,
        /** 좀비에게 감염됨(주민→좀비 주민), 벌레 들끓음 효과로 나온 슬라임·좀벌레. */
        INFECTION,
        /** 좀비 주민이 치료됨. */
        CURED,
        SILVERFISH_BLOCK,
        TRAP,
        SHOULDER_ENTITY,
        /** 좀비·허스크가 물에 잠겨 변함. */
        DROWNED,
        SHEARED,
        RAID,
        PATROL,
        /** 호글린→조글린, 피글린→좀비 피글린. */
        PIGLIN_ZOMBIFIED,
        /** 스켈레톤이 가루눈에 얼어 스트레이로 변함. */
        FROZEN,
        /** 올챙이가 개구리로 자람. */
        METAMORPHOSIS,
        /** 소환사의 주문(밴시). */
        SPELL,
        /** /summon 명령. */
        COMMAND,
        /** 플러그인이 월드 핸들로 만듦. */
        CUSTOM,
        /** 위 어디에도 속하지 않음. */
        DEFAULT
    }

    private final SpawnReason reason;

    public CreatureSpawnEvent(Entity entity, SpawnReason reason) {
        super(entity);
        this.reason = reason;
    }

    public SpawnReason reason() { return reason; }
}
