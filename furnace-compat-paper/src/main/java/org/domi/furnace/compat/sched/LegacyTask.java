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
}
