// 습격 한 건의 관측 사본(읽기 전용). 잰 순간의 값이라 곧 낡을 수 있다. 아무 스레드에서 읽는다.
package org.domi.furnace.api.world;

import java.util.Set;
import java.util.UUID;

/**
 * @param id             습격 번호(월드 안에서 유일, 바닐라 raids.dat 의 Id)
 * @param center         습격 중심
 * @param started        첫 파도를 위해 시작했는가(전 단계 대기 중이면 false)
 * @param active         진행 중인가(승리·패배·중지 전)
 * @param omenLevel      습격 징조 단계(0..5). 2 이상이면 보너스 파도가 하나 더 있다
 * @param groupsSpawned  지금까지 나온 파도 수
 * @param totalWaves     보너스 파도를 포함한 전체 파도 수
 * @param raidersAlive   살아 있는 습격자 수
 * @param raidersHealth  살아 있는 습격자 체력 합
 * @param preRaidTicks   다음 파도(첫 파도면 시작)까지 남은 대기 틱
 * @param betweenWaves   파도 사이 쉬는 중인가
 * @param heroes         마을의 영웅(이긴 뒤 효과를 받을 플레이어)
 */
public record RaidInfo(int id, String dimension, BlockPos center, RaidStatus status, boolean started, boolean active, int omenLevel,
                       int groupsSpawned, int totalWaves, int raidersAlive, float raidersHealth, int preRaidTicks, boolean betweenWaves,
                       Set<UUID> heroes) {

    public RaidInfo {
        heroes = Set.copyOf(heroes);
    }
}
