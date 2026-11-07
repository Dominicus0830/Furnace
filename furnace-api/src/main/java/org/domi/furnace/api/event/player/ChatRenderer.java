// 받는 사람마다 다른 채팅 서식 — AsyncChatEvent.renderer(…). 받는 사람마다 한 번씩(그리고 콘솔 로그에 한 번) 불린다.
package org.domi.furnace.api.event.player;

import org.domi.furnace.api.audience.Audience;
import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.text.Text;

@FunctionalInterface
public interface ChatRenderer {

    /**
     * 한 사람에게 갈 채팅 한 줄을 만든다.
     *
     * @param source 말한 사람
     * @param sourceDisplayName 말한 사람의 표시 이름(바닐라 "<이름>" 에 쓰이는 것: 팀 접두·접미, 이름 클릭 제안 포함)
     * @param message 말한 내용(AsyncChatEvent.message())
     * @param viewer 받는 사람 — 접속한 플레이어, 콘솔 로그면 콘솔
     */
    Text render(Player source, Text sourceDisplayName, Text message, Audience viewer);

    /** 바닐라 서식 "<이름> 내용" (chat.type.text). */
    static ChatRenderer vanilla() {
        return (source, name, message, viewer) -> Text.translatable("chat.type.text", name, message);
    }
}
