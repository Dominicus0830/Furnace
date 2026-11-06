// 차원 이동 계획(원천 레벨에서 만든다): 목적 차원, 목적 쪽에서 미리 올릴 청크 범위, 목적 레벨을 받아 목적지를 확정하는 함수.
// 바닐라는 한 스레드에서 Portal.getPortalDestination 으로 바로 계산하지만, Furnace 는 목적 레벨이 다른 region 스레드 소유라
// 원천 쪽 정보(포털 사각형·상대 위치·몸 크기)는 여기서 떠 두고 나머지는 목적 region 스레드에서 complete 로 계산한다.
package org.domi.furnace.server.entity;

import java.util.function.Function;
import org.domi.furnace.server.block.ChunkLevel;

/**
 * @param centerX      목적 쪽 중심 블록 x(이 청크의 region 에서 complete 를 돌린다)
 * @param searchRadius 디스크에 있는 청크만 미리 올릴 반경(블록) — 포털 POI 탐색
 * @param createRadius 없으면 생성해서라도 미리 올릴 반경(블록) — 포털·단상 만들기, 도착 칸
 * @param complete     목적 레벨 → 목적지(null 이면 이동하지 않는다)
 */
public record PortalTravel(String targetDimension, int centerX, int centerZ, int searchRadius, int createRadius,
                           Function<ChunkLevel, DimensionTransition> complete) {}
