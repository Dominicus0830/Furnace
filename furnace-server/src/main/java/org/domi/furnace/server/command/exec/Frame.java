// 바닐라 Frame(hu) — 함수 호출 깊이, 반환 콜백, 그 깊이 이상 대기 항목을 버리는 discard.
package org.domi.furnace.server.command.exec;

import java.util.concurrent.CompletableFuture;

public record Frame(int depth, ResultCallback returnValueConsumer, FrameControl frameControl) {

    @FunctionalInterface
    public interface FrameControl {
        void discard();
    }

    public CompletableFuture<Void> returnSuccess(int value) { return returnValueConsumer.onSuccess(value); }

    public CompletableFuture<Void> returnFailure() { return returnValueConsumer.onFailure(); }

    public void discard() { frameControl.discard(); }
}
