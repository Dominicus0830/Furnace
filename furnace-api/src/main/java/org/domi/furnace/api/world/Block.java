// 월드의 한 칸에 대한 살아 있는 손잡이 — 값을 들고 있지 않고, 읽을 때마다 지금 상태를 본다. 만드는 데 비용이 거의 없다(읽기 전까지 아무것도 하지 않는다).
// 읽기·쓰기는 그 칸을 가진 region 스레드에서만 동기로 된다(아니면 ThreadOwnershipException). 다른 스레드에서는 *Async 를 쓴다.
// 읽은 값을 오래 들고 있거나 다른 스레드로 넘길 때는 {@link #snapshot()} 으로 복사한다({@link BlockSnapshot}).
package org.domi.furnace.api.world;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface Block {

    /** 차원 키(minecraft:overworld). 아무 스레드. */
    String world();

    /** 아무 스레드. */
    BlockPos pos();

    /** 이 칸을 가진 월드. 아무 스레드. */
    World worldHandle();

    /** 지금 상태. 소유 스레드 전용 — 청크가 로드돼 있지 않으면 IllegalStateException. */
    BlockData data();

    /** 지금 종류(minecraft:stone). 소유 스레드 전용. */
    default String type() { return data().type(); }

    /** 지금 상태를 복사한다. 소유 스레드 전용 — 청크가 로드돼 있지 않으면 empty. */
    Optional<BlockSnapshot> snapshot();

    /** 소유 region 스레드에서 읽어 복사한다. 청크가 로드돼 있지 않으면 empty. 아무 스레드. */
    CompletableFuture<Optional<BlockSnapshot>> snapshotAsync();

    /**
     * 이 칸을 바꾼다(World.setBlock 과 같은 규칙: 이웃 갱신 + 방송, BlockPlace/BlockBreak 이벤트 없음). 소유 스레드 전용.
     * 청크가 로드돼 있지 않거나 높이 밖이면 false. 알 수 없는 블록·속성이면 IllegalArgumentException.
     */
    boolean set(BlockData data);

    /** set 의 비동기판 — 소유 region 스레드에서 실행된다. 아무 스레드. */
    CompletableFuture<Boolean> setAsync(BlockData data);

    /** 상대 좌표의 이웃 손잡이(읽지 않는다). */
    Block relative(int dx, int dy, int dz);

    default Block relative(Direction face) { return relative(face.dx(), face.dy(), face.dz()); }

    /** 이 칸에서 속성 하나를 바꾼 상태로 쓴다. 소유 스레드 전용. */
    default <T> boolean setProperty(BlockProperty<T> property, T value) { return set(data().with(property, value)); }
}
