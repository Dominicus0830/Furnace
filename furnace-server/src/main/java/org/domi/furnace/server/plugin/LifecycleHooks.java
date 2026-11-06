// 수명주기 훅 저장소(API LifecycleEventManager 구현). 처리기는 플러그인 부트스트랩 순서 → 등록 순서로 불린다.
// 등록은 그 플러그인의 부트스트랩이 도는 동안만 열린다. 플러그인이 꺼지면 removeOwner 로 처리기를 뗀다.
package org.domi.furnace.server.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.logging.Level;
import org.domi.furnace.api.plugin.lifecycle.LifecycleEventHandler;
import org.domi.furnace.api.plugin.lifecycle.LifecycleEventManager;
import org.domi.furnace.api.plugin.lifecycle.LifecycleEventType;

final class LifecycleHooks {

    /** 등록한 플러그인(이름·로거)과 처리기. */
    record Handler(String owner, java.util.logging.Logger logger, LifecycleEventType<?> type, LifecycleEventHandler<?> handler) {}

    private final List<Handler> handlers = new CopyOnWriteArrayList<>();

    /** 그 플러그인 부트스트랩 동안 쓰는 등록기. close() 뒤에는 등록할 수 없다. */
    final class Registrar implements LifecycleEventManager {
        private final String owner;
        private final java.util.logging.Logger logger;
        private volatile boolean open = true;

        Registrar(String owner, java.util.logging.Logger logger) {
            this.owner = owner;
            this.logger = logger;
        }

        @Override
        public <R> void register(LifecycleEventType<R> type, LifecycleEventHandler<? super R> handler) {
            if (type == null || handler == null) throw new NullPointerException(type == null ? "type" : "handler");
            if (!open) throw new IllegalStateException("수명주기 훅은 부트스트랩 단계에서만 등록할 수 있다: " + type);
            handlers.add(new Handler(owner, logger, type, handler));
        }

        void close() { open = false; }
    }

    Registrar registrar(String owner, java.util.logging.Logger logger) { return new Registrar(owner, logger); }

    /**
     * 그 종류의 처리기를 순서대로 부른다. registrarFor 는 플러그인 이름 → 그 플러그인에 묶인 등록기.
     * 처리기 예외는 그 플러그인 로거에 남기고 다음 처리기로 넘어간다.
     */
    @SuppressWarnings("unchecked")
    <R> void fire(LifecycleEventType<R> type, Function<String, R> registrarFor) {
        for (Handler h : handlers) {
            if (h.type() != type) continue;
            try {
                ((LifecycleEventHandler<R>) h.handler()).run(registrarFor.apply(h.owner()));
            } catch (Throwable t) {
                h.logger().log(Level.SEVERE, "수명주기 훅 " + type + " 처리 중 예외", t);
            }
        }
    }

    /** 그 플러그인의 처리기를 모두 뗀다. */
    void removeOwner(String owner) { handlers.removeIf(h -> h.owner().equals(owner)); }

    /** 그 종류의 처리기를 가진 플러그인 이름(등록 순, 중복 없음) — 시험용. */
    List<String> owners(LifecycleEventType<?> type) {
        List<String> out = new ArrayList<>();
        for (Handler h : handlers) if (h.type() == type && !out.contains(h.owner())) out.add(h.owner());
        return out;
    }
}
