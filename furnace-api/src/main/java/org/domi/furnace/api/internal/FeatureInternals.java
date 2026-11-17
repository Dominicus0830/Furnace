// 지형지물 배치 다리(T-211). Bukkit·Paper 호환 계층의 NMS {@code ConfiguredFeature#place}·{@code PlacedFeature#place} shim 이
// 서버의 지형 생성 코드(바닐라 1.21.1 이식)를 플러그인이 준 레벨·난수로 돌리는 길이다. 불안정한 내부 다리다(호환 계층 전용).
package org.domi.furnace.api.internal;

import java.util.Random;
import org.domi.furnace.api.world.World;

/**
 * 설정된 지형지물(configured feature)·배치된 지형지물(placed feature)을 이름으로 놓는다.
 *
 * <p>"Now" 메서드는 원점 칸을 가진 region 스레드(전역 빌림 구간 포함)에서만 부를 수 있다. 아니면
 * {@link org.domi.furnace.api.concurrent.ThreadOwnershipException}.
 *
 * <p>공통 오류:
 * <ul>
 *   <li>모르는 이름: IllegalArgumentException</li>
 *   <li>아직 구현하지 않은 지형지물이 하위에 있다, 또는 플러그인 레벨의 블록 엔티티 내용을 바꿔야 한다: UnsupportedOperationException</li>
 *   <li>원점 청크가 로드돼 있지 않다, 또는 배치가 로드 안 됨·다른 region·원점에서 2청크 넘는 청크에 닿았다: IllegalStateException
 *       (그 전까지의 쓰기는 이미 {@link LevelSink} 에 갔을 수 있다 — 서버 월드에 직접 놓는 길은 아무것도 바꾸지 않는다)</li>
 *   <li>바이옴 거르기가 든 배치된 지형지물: 바닐라처럼 IllegalStateException("Tried to biome check an unregistered feature, …")</li>
 * </ul>
 */
public interface FeatureInternals {

    /**
     * 플러그인 레벨(바닐라 {@code WorldGenLevel})로 가는 쓰기 통로. 지형 생성 코드가 쓰는 차례 그대로 부른다.
     * 블록 읽기는 서버가 실제 월드 + 받아들인 쓰기로 답하고, 하이트맵·유체는 실제 월드로 답한다(Paper 의 populator 와 같다).
     */
    interface LevelSink {

        /** 바닐라 {@code LevelWriter#setBlock(pos, state, flags)}. 거짓이면 그 쓰기는 없던 것이 된다(다음 읽기에 보이지 않는다). */
        boolean setBlock(int x, int y, int z, int stateId, int flags);

        /** 그 칸에 레벨이 가진 블록 엔티티의 종류 id(예: {@code minecraft:beehive}), 없으면 null — 바닐라 {@code getBlockEntity(pos)} 자리. */
        String blockEntityType(int x, int y, int z);
    }

    /**
     * 지형지물을 원점에 놓는다. placed 가 참이면 배치된 지형지물 표, 거짓이면 설정된 지형지물 표에서 id 를 찾는다(그 월드가 쓰는 표). sink 가 null 이면 서버 월드에 바로 놓고(바닐라 런타임 배치 — 블록 엔티티·생성 NBT 포함, 쓰기 플래그 그대로),
     * 아니면 쓰기를 sink 로만 보내고 서버 월드는 바꾸지 않는다(생성 NBT·엔티티·유체 예약 틱은 버린다 — Paper 의 populator 와 같다).
     * 난수는 random 에서 뽑을 때마다 그대로 뽑는다(원천 상태도 같이 나아간다).
     *
     * @return 바닐라 {@code place} 의 반환값
     */
    boolean placeNow(World world, boolean placed, String id, int x, int y, int z, Random random, LevelSink sink);
}
