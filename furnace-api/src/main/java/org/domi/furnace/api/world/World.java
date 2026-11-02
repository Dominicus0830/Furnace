// 차원 하나의 월드. 블록 접근은 소유 region 스레드로 넘어가므로 비동기이며, 동기 접근은 소유 스레드에서만 허용한다(위반 시 예외).
package org.domi.furnace.api.world;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.domi.furnace.api.sched.TaskScheduler;

public interface World {

    /** 레벨 이름(Paper 와 같은 규칙: 오버월드 world, 네더 world_nether, 엔드 world_the_end). 불변. 아무 스레드. */
    String name();

    /** 차원 키(minecraft:overworld). 불변. 아무 스레드. */
    String dimension();

    long seed();

    /** 게임룰 값(문자열 true/false/숫자), 모르는 이름이면 null. 최근 값 — 아무 스레드. */
    String gameRule(String name);

    /** 지금 로드된 청크 수(근사, 진단용). */
    int loadedChunkCount();

    /**
     * 그 칸의 블록. 소유 region 스레드에서 읽는다. 청크가 로드돼 있지 않으면 empty(청크를 만들지 않는다).
     * 아무 스레드에서 부를 수 있다. 결과를 같은 region 스레드에서 {@code get()} 으로 기다리지 말 것.
     */
    CompletableFuture<Optional<Block>> blockAt(BlockPos pos);

    /**
     * 그 칸을 data 로 바꾼다(이웃 갱신 + 클라이언트 방송, 바닐라 setBlock 플래그 11). 소유 region 스레드에서 실행된다.
     * 청크가 로드돼 있지 않거나 높이 밖이면 false. 알 수 없는 블록·속성이면 IllegalArgumentException 으로 완료된다.
     * BlockPlace/BlockBreak 이벤트는 거치지 않는다.
     */
    CompletableFuture<Boolean> setBlock(BlockPos pos, BlockData data);

    /**
     * 동기 읽기 — 호출한 스레드가 그 칸을 가진 region 스레드일 때만 된다(예: 그 region 에서 발화한 이벤트 처리기 안).
     * 아니면 {@link org.domi.furnace.api.concurrent.ThreadOwnershipException}.
     */
    Optional<Block> blockNow(BlockPos pos);

    /** 그 칸을 가진 region 의 스케줄러. */
    TaskScheduler scheduler(BlockPos pos);
}
