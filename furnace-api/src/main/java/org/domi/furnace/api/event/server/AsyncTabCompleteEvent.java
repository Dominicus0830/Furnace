// 명령 자동완성 요청 — 비동기 이벤트(Paper AsyncTabCompleteEvent). 플레이어가 Tab 을 눌러 서버에 후보를 물으면, 서버가 명령 트리를 보기 전에 불린다.
// 처리기가 후보를 채우거나 handled 로 표시하면 서버는 그 후보만 보낸다(명령 트리·접속자 이름 기본 후보는 묻지 않는다). 취소하거나 후보가 없고 handled 도 아니면:
// 취소는 아무 응답도 보내지 않고, 취소가 아니면 서버가 평소처럼 후보를 만든다. 처리기가 하나도 없으면 서버는 이 이벤트를 만들지 않는다.
// 불리는 스레드: 서버의 자동완성 전용 스레드(플레이어) 또는 콘솔 입력 스레드 — 월드·엔티티 상태를 바로 만지지 말고 스케줄러로 넘긴다.
package org.domi.furnace.api.event.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.domi.furnace.api.command.CommandSender;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.event.Event;
import org.domi.furnace.api.text.Text;

public final class AsyncTabCompleteEvent extends Event implements Cancellable {

    /** 후보 하나: 넣을 문자열과 (없을 수 있는) 설명 글. */
    public record Completion(String suggestion, Text tooltip) {
        public Completion {
            Objects.requireNonNull(suggestion, "suggestion");
        }

        public Completion(String suggestion) { this(suggestion, null); }
    }

    private final CommandSender sender;
    private final String buffer;
    private final boolean command;
    private final List<Completion> completions = new ArrayList<>();
    private boolean handled;
    private boolean cancelled;

    /**
     * @param buffer 입력 칸의 전체 글(플레이어 명령 자동완성은 앞의 '/' 를 포함한다)
     * @param command 명령 자동완성이면 true(서버가 플레이어에게서 받는 요청은 늘 true, 콘솔도 true)
     */
    public AsyncTabCompleteEvent(CommandSender sender, String buffer, boolean command) {
        super(true);
        this.sender = Objects.requireNonNull(sender, "sender");
        this.buffer = Objects.requireNonNull(buffer, "buffer");
        this.command = command;
    }

    public CommandSender sender() { return sender; }

    /** 입력 칸의 전체 글(플레이어는 앞의 '/' 를 포함한다). */
    public String buffer() { return buffer; }

    /** 명령 자동완성인가(채팅 자동완성이면 false). */
    public boolean isCommand() { return command; }

    /** 서버가 보낼 후보(바꿀 수 있는 목록, 보낼 순서). 비어 있지 않으면 서버는 명령 트리로 후보를 만들지 않는다. */
    public List<Completion> completions() { return completions; }

    /** handled 로 표시했는가 — 후보가 하나라도 있으면 늘 true. */
    public boolean isHandled() { return !completions.isEmpty() || handled; }

    /** 후보와 따로 정한 handled 표시(후보가 없어도 서버가 후보를 만들지 않게 할 때). */
    public boolean handledFlag() { return handled; }

    public void setHandled(boolean handled) { this.handled = handled; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
