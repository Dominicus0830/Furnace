// 메시지를 받을 수 있는 대상(플레이어·콘솔·여러 명 묶음). 받을 수 없는 종류는 조용히 무시한다.
package org.domi.furnace.api.audience;

import java.util.Collection;
import org.domi.furnace.api.text.Text;

public interface Audience {

    /** 채팅 창 시스템 메시지(바닐라 system_chat). */
    void sendMessage(Text message);

    /** 액션바(핫바 위). */
    default void sendActionBar(Text message) {}

    default void showTitle(Title title) {}

    /** 제목을 지운다(시간 설정은 유지). */
    default void clearTitle() {}

    /** 제목을 지우고 시간 설정도 기본값으로. */
    default void resetTitle() {}

    /** 탭 목록 머리말·꼬리말. */
    default void sendPlayerListHeaderAndFooter(Text header, Text footer) {}

    /** 여러 대상을 하나로 — 같은 메시지는 한 번만 인코딩된다(Text 가 인코딩을 품는다). */
    static Audience audience(Collection<? extends Audience> members) {
        return new Audience() {
            public void sendMessage(Text m) { for (Audience a : members) a.sendMessage(m); }
            public void sendActionBar(Text m) { for (Audience a : members) a.sendActionBar(m); }
            public void showTitle(Title t) { for (Audience a : members) a.showTitle(t); }
            public void clearTitle() { for (Audience a : members) a.clearTitle(); }
            public void resetTitle() { for (Audience a : members) a.resetTitle(); }
            public void sendPlayerListHeaderAndFooter(Text h, Text f) { for (Audience a : members) a.sendPlayerListHeaderAndFooter(h, f); }
        };
    }
}
