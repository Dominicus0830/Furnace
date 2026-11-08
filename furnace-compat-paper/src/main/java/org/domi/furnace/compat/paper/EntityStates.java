// 엔티티 상태(이름·플래그·탑승·효과)의 Bukkit 동기 호출을 Furnace EntityState 위에 올린다.
// 소유 스레드(이벤트 처리기·entityScheduler 작업·옛 동기 작업의 빌림 구간)면 stateNow 로 곧바로 읽고 쓴다(Bukkit 처럼 쓴 값이 곧바로 읽힌다).
// 아니면 읽기와 결과가 필요한 호출은 ThreadOwnershipException, 결과가 필요 없는 쓰기는 editState 로 소유 스레드에 예약한다(순서 유지).
package org.domi.furnace.compat.paper;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import org.domi.furnace.api.concurrent.ThreadOwnershipException;
import org.domi.furnace.api.entity.Entity;
import org.domi.furnace.api.entity.EntityState;

public final class EntityStates {

    private static final System.Logger LOG = System.getLogger("FurnaceCompat");

    private EntityStates() {}

    /** 소유 스레드의 지금 상태(사라졌으면 empty). 상태 손잡이가 없는 엔티티(플레이어)는 UnsupportedFeature. */
    private static Optional<EntityState> now(Entity handle, String what) {
        try {
            return handle.stateNow();
        } catch (ThreadOwnershipException e) {
            throw e;
        } catch (UnsupportedOperationException e) {
            throw new UnsupportedFeature(what, handle.type() + " 의 상태 쓰기 API 가 아직 없다");
        }
    }

    /**
     * 읽기·결과가 필요한 호출: 소유 스레드에서 op. 엔티티가 사라졌거나 상태가 그 종류(kind)가 아니면 gone.
     * 소유 스레드가 아니면 ThreadOwnershipException.
     */
    public static <S extends EntityState, T> T read(Entity handle, Class<S> kind, Function<? super S, ? extends T> op, T gone, String what) {
        Optional<EntityState> s = now(handle, what);
        if (s.isEmpty() || !kind.isInstance(s.get())) return gone;
        return op.apply(kind.cast(s.get()));
    }

    /** 결과가 필요 없는 쓰기: 소유 스레드면 곧바로, 아니면 소유 스레드로 예약한다. 사라진 엔티티·다른 종류에는 아무 일도 하지 않는다(Paper 와 같이 조용히). */
    public static <S extends EntityState> void write(Entity handle, Class<S> kind, Consumer<? super S> op, String what) {
        Optional<EntityState> s;
        try {
            s = now(handle, what);
        } catch (ThreadOwnershipException notOwner) {
            handle.editState(st -> {
                if (kind.isInstance(st)) op.accept(kind.cast(st));
                return null;
            }).whenComplete((v, e) -> {
                // 그 사이 사라진 엔티티(IllegalStateException)는 Paper 처럼 조용히 넘긴다
                if (e != null && !(unwrap(e) instanceof IllegalStateException)) LOG.log(System.Logger.Level.WARNING, what + " 예약 작업이 실패했다", e);
            });
            return;
        }
        if (s.isPresent() && kind.isInstance(s.get())) op.accept(kind.cast(s.get()));
    }

    private static Throwable unwrap(Throwable e) { return e instanceof java.util.concurrent.CompletionException c && c.getCause() != null ? c.getCause() : e; }
}
