// 데이터 팩 함수 안의 명령 한 줄 실행 직전 — 취소하면 그 줄만 건너뛴다(뒷줄은 그대로 실행). 전역 스레드(명령 큐)에서 불린다.
// 구독자가 없으면 서버는 이 이벤트를 만들지 않는다. 함수 줄은 틱마다 수천 번 돌 수 있으니 핸들러는 가볍게 쓸 것.
package org.domi.furnace.api.event.server;

import org.domi.furnace.api.command.CommandSender;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.event.Event;

public final class FunctionCommandEvent extends Event implements Cancellable {

    private final CommandSender sender;
    private final String command;
    private final int depth;
    private boolean cancelled;

    public FunctionCommandEvent(CommandSender sender, String command, int depth) {
        this.sender = sender;
        this.command = command;
        this.depth = depth;
    }

    /** 이 줄을 실행하는 명령 소스(execute as/at 가 적용되기 전, 함수를 부른 소스). */
    public CommandSender sender() { return sender; }

    /** 함수 파일에 적힌 명령 줄('/' 없음, 매크로는 값이 채워진 뒤). */
    public String command() { return command; }

    /** 이 줄이 들어 있는 함수의 호출 깊이(최상위 함수 안이 1). */
    public int depth() { return depth; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
