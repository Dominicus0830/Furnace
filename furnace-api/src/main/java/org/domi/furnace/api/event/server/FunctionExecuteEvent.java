// 데이터 팩 함수 실행 직전 — 취소하면 그 함수 본문을 통째로 건너뛴다. 전역 스레드(명령 큐)에서 불린다.
package org.domi.furnace.api.event.server;

import org.domi.furnace.api.command.CommandSender;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.event.Event;

public final class FunctionExecuteEvent extends Event implements Cancellable {

    /** 함수가 불린 경로. */
    public enum Trigger {
        /** 매 틱 #minecraft:tick 태그. */
        TICK,
        /** 서버 시작·/reload 뒤 #minecraft:load 태그. */
        LOAD,
        /** /schedule 로 예약한 함수. */
        SCHEDULED,
        /** /function · execute if function · /return run function 등 명령이 직접 부른 최상위 호출. */
        COMMAND,
        /** 업적 보상 함수. */
        ADVANCEMENT,
        /** 다른 함수 안에서 부른 호출(중첩). */
        NESTED
    }

    private final String function;
    private final Trigger trigger;
    private final CommandSender sender;
    private final int depth;
    private boolean cancelled;

    public FunctionExecuteEvent(String function, Trigger trigger, CommandSender sender, int depth) {
        this.function = function;
        this.trigger = trigger;
        this.sender = sender;
        this.depth = depth;
    }

    /** 함수 ID({@code namespace:path}). */
    public String function() { return function; }

    public Trigger trigger() { return trigger; }

    /** 이 호출의 명령 소스(tick·load·schedule 은 권한 2의 서버 소스). */
    public CommandSender sender() { return sender; }

    /** 호출 깊이(최상위 0, 중첩 함수마다 +1). */
    public int depth() { return depth; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
