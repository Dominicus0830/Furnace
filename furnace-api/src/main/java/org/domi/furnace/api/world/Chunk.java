// 청크 좌표 핸들 — 가볍고 불변이며 청크를 로드하지 않는다. 읽기·쓰기는 그 청크를 가진 region 스레드에서 실행되고,
// 로드되지 않은 청크는 빈 값/false 로 끝난다.
package org.domi.furnace.api.world;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.domi.furnace.api.entity.Entity;
import org.domi.furnace.api.persistence.PersistentDataContainer;
import org.domi.furnace.api.persistence.PersistentDataView;
import org.domi.furnace.api.sched.TaskScheduler;

public interface Chunk {

    World world();

    int x();

    int z();

    /** 지금 로드돼 있는가(최근 값 — 아무 스레드). */
    boolean isLoaded();

    /** 이 청크를 가진 region 의 스케줄러. */
    TaskScheduler scheduler();

    /** 이 청크 안의 엔티티(플레이어 포함) 핸들. 로드되지 않았으면 빈 목록. */
    CompletableFuture<List<Entity>> entities();

    /** 청크 영속 데이터의 사본(청크 NBT 의 ChunkBukkitValues). 로드되지 않았으면 empty. */
    CompletableFuture<Optional<PersistentDataView>> persistentData();

    /** 소유 region 스레드에서 영속 데이터를 고친다. 고친 뒤 청크를 저장 대상으로 표시한다. 로드되지 않았으면 false. */
    CompletableFuture<Boolean> editPersistentData(Consumer<PersistentDataContainer> edit);
}
