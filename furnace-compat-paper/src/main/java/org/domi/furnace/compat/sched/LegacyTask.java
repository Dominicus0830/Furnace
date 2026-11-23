// Bukkit BukkitTask 에 해당하는 손잡이.
package org.domi.furnace.compat.sched;

import org.domi.furnace.api.plugin.FurnacePlugin;

public interface LegacyTask {

    /** 예약 번호(1 부터, 서버를 켠 뒤 겹치지 않는다). */
    int taskId();

    FurnacePlugin owner();

    /** 전역(주) 스레드에서 도는 작업이면 true, 비동기 실행기에서 도는 작업이면 false. */
    boolean isSync();

    boolean isCancelled();

    /** 취소한다 — 아직 안 돈 실행과 다음 반복이 사라진다. 지금 도는 중인 실행은 끝까지 간다. 여러 번 불러도 된다. */
    void cancel();

    /** 몸통을 이 스레드에서 곧바로 한 번 돌린다(Paper CraftTask#run — 예약·취소 상태를 보지 않는다). */
    void runBody();

    /** callSyncMethod 로 건 작업이면 그 future, 아니면 null(Paper 처럼 대기 목록에서도 CraftFuture 로 보이게). */
    default java.util.concurrent.Future<?> syncCall() { return null; }

    /** 이 작업의 플러그인 쪽 손잡이 — 처음 부를 때 make 로 한 번 만들고 그 뒤로는 같은 객체(Paper 처럼 작업 하나에 객체 하나). */
    <T> T view(java.util.function.Function<LegacyTask, T> make);
}
