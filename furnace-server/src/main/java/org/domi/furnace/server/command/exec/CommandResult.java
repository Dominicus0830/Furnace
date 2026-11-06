// performPrefixedCommandAsync 의 결과: 최상위 소스 콜백이 마지막으로 받은 (success, result) 와 콜백 횟수(포크 수).
// 콜백이 한 번도 안 왔으면(파싱 실패·대상 없음으로 포크가 사라짐 등) (false, 0, 0).
package org.domi.furnace.server.command.exec;

public record CommandResult(boolean success, int result, int callbacks) {

    public static final CommandResult NONE = new CommandResult(false, 0, 0);

    /** 콜백을 받아 모으는 기록기(전역 스레드에서만 불린다). */
    public static final class Recorder implements ResultCallback {
        private boolean success;
        private int result;
        private int callbacks;

        @Override
        public java.util.concurrent.CompletableFuture<Void> onResult(boolean success, int result) {
            this.success = success;
            this.result = result;
            this.callbacks++;
            return DONE;
        }

        public CommandResult snapshot() { return callbacks == 0 ? NONE : new CommandResult(success, result, callbacks); }
    }
}
