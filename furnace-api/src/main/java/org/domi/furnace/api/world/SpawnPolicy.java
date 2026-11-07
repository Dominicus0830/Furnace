// 월드의 자연 스폰 정책 — World.spawnPolicy(). 기본값은 바닐라 상수(MobCategory.max)와 같다: 아무것도 바꾸지 않으면 스폰 루프는 바닐라 그대로다.
// 상한은 바닐라가 쓰는 두 가지다 — 월드 상한(종류별 max × 스폰 대상 청크 수 ÷ 289 보다 적어야 스폰)과 플레이어별 상한(그 플레이어 128칸 안의 같은 종류 수가 max 보다 적어야 스폰).
// 활성 범위·몹 수 자체를 지우는 일은 하지 않는다 — 정책은 "새로 스폰할지" 만 정한다. 이미 있는 몹은 그대로 둔다.
// 상한을 정책으로 바꾼 값은 저장되지 않는다(플러그인이 켜질 때마다 건다). furnace.properties 의 spawn.limit.* / spawn.player-limit.* 는 모든 월드의 바탕값이고,
// 이 객체로 건 월드별 값이 그 위에 덮인다. 모든 메서드는 아무 스레드에서 불러도 된다(바뀐 값은 다음 틱부터).
package org.domi.furnace.api.world;

import java.util.UUID;
import org.domi.furnace.api.event.EventSubscription;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.worldgen.SpawnCategory;

public interface SpawnPolicy {

    /** 바닐라 상수(MONSTER 70, CREATURE 10, AMBIENT 15, AXOLOTLS 5, UNDERGROUND_WATER_CREATURE 5, WATER_CREATURE 5, WATER_AMBIENT 20). MISC 는 자연 스폰이 없어 IllegalArgumentException. */
    int vanillaCap(SpawnCategory category);

    // ------------------------------------------------------------------ 월드 상한

    /** 지금 적용되는 월드 상한(청크 289 칸당 마릿수 — 월드별 값 > furnace.properties > 바닐라 순). */
    int categoryCap(SpawnCategory category);

    /** 월드 상한을 cap 으로(0 이상, 0 이면 그 종류는 새로 스폰되지 않는다). 너무 큰 값은 틱 비용이 늘 수 있다 — 1만 이하만 받는다. */
    void setCategoryCap(SpawnCategory category, int cap);

    /** 월드별 값을 지우고 바탕값(설정·바닐라)으로 돌린다. */
    void resetCategoryCap(SpawnCategory category);

    // ------------------------------------------------------------------ 플레이어별 상한

    /** 모든 플레이어의 기본 플레이어 상한(월드별 값 > 설정 > 바닐라). */
    int playerCap(SpawnCategory category);

    void setPlayerCap(SpawnCategory category, int cap);

    void resetPlayerCap(SpawnCategory category);

    /** 그 플레이어에게 적용되는 상한(그 사람만의 값 > 기본 플레이어 상한). 접속 중이 아니어도 값을 걸어 둘 수 있다. */
    int playerCap(UUID player, SpawnCategory category);

    void setPlayerCap(UUID player, SpawnCategory category, int cap);

    void resetPlayerCap(UUID player, SpawnCategory category);

    // ------------------------------------------------------------------ 조건 술어

    /** 조건 술어를 건다. 여러 개면 모두 true 여야 스폰한다. 돌려준 손잡이의 unsubscribe 로 뗀다. */
    EventSubscription addFilter(FurnacePlugin plugin, SpawnFilter filter);

    /** 지금 걸린 필터 수. */
    int filterCount();
}
