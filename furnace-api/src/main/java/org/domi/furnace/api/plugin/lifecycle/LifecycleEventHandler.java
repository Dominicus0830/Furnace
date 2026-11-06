package org.domi.furnace.api.plugin.lifecycle;

/** 수명주기 훅 처리기. 예외는 그 플러그인 로거로 남고 다른 플러그인의 처리기는 계속 돈다. */
@FunctionalInterface
public interface LifecycleEventHandler<R> {

    void run(R registrar);
}
