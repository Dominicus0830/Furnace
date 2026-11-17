// /gamerule 로 규칙 값을 바꾸기 직전에 명령을 실행하는 스레드에서 불린다. 값(문자열 true/false/숫자)을 바꾸거나 취소할 수 있다.
// 취소하면 값은 그대로지만 명령은 성공으로 끝나고 지금 값을 알린다(Paper WorldGameRuleChangeEvent 와 같다).
// 정수 규칙에 숫자가 아닌 값을 넣으면 명령이 실패한다. Furnace 게임 규칙은 서버 전체에 하나라서 world 는 명령을 낸 쪽의 월드다.
package org.domi.furnace.api.event.world;

import org.domi.furnace.api.command.CommandSender;
import org.domi.furnace.api.event.Cancellable;

public final class GameRuleChangeEvent extends WorldEvent implements Cancellable {

    private final CommandSender sender;
    private final String rule;
    private String value;
    private boolean cancelled;

    public GameRuleChangeEvent(String world, CommandSender sender, String rule, String value) {
        super(world);
        this.sender = sender;
        this.rule = rule;
        this.value = value;
    }

    /** 명령을 낸 쪽. */
    public CommandSender sender() { return sender; }

    /** 규칙 이름(바닐라 doDaylightCycle, 플러그인 규칙이면 "namespace:이름"). */
    public String rule() { return rule; }

    /** 새 값(문자열). */
    public String value() { return value; }

    public void value(String value) { this.value = value; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
