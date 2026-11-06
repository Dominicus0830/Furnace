// 수명주기 훅 등록. 처리기는 플러그인 의존 순서(켜는 순서), 같은 플러그인 안에서는 등록 순서로 불린다.
package org.domi.furnace.api.plugin.lifecycle;

public interface LifecycleEventManager {

    /** 처리기를 단다. 부트스트랩 단계에서만 부를 수 있다(그 뒤에는 IllegalStateException). */
    <R> void register(LifecycleEventType<R> type, LifecycleEventHandler<? super R> handler);
}
