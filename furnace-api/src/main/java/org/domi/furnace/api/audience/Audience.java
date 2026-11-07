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

    /** 그 사람이 있는 자리에서 소리를 들려준다(그 사람에게만). */
    default void playSound(Sound sound) {}

    /** 월드의 한 지점에서 나는 것처럼 들려준다(그 사람에게만 — 멀면 크기에 따라 작게 들린다). */
    default void playSound(Sound sound, double x, double y, double z) {}

    /** 소리를 멈춘다. source·key 중 null 인 쪽은 가리지 않는다(둘 다 null 이면 모든 소리). */
    default void stopSound(Sound.Source source, String key) {}

    default void stopSound(String key) { stopSound(null, key); }

    default void stopAllSounds() { stopSound(null, null); }

    /** 탭 목록 머리말·꼬리말. */
    default void sendPlayerListHeaderAndFooter(Text header, Text footer) {}

    /** 보스바를 보여 준다(이미 보이면 아무 일도 없다). 보스바를 바꾸면 보는 사람 화면이 따라온다. */
    default void showBossBar(org.domi.furnace.api.bossbar.BossBar bar) {}

    default void hideBossBar(org.domi.furnace.api.bossbar.BossBar bar) {}

    /** 화면 오른쪽 점수판을 보여 준다(이미 다른 사이드바가 보이면 바뀐다). 사이드바를 바꾸면 보는 사람 화면이 따라온다. */
    default void showSidebar(org.domi.furnace.api.scoreboard.Sidebar sidebar) {}

    /** 이 사이드바가 보이는 중이면 숨긴다. */
    default void hideSidebar(org.domi.furnace.api.scoreboard.Sidebar sidebar) {}

    /** 여러 대상을 하나로 — 같은 메시지는 한 번만 인코딩된다(Text 가 인코딩을 품는다). */
    static Audience audience(Collection<? extends Audience> members) {
        return new Audience() {
            public void sendMessage(Text m) { for (Audience a : members) a.sendMessage(m); }
            public void sendActionBar(Text m) { for (Audience a : members) a.sendActionBar(m); }
            public void showTitle(Title t) { for (Audience a : members) a.showTitle(t); }
            public void clearTitle() { for (Audience a : members) a.clearTitle(); }
            public void resetTitle() { for (Audience a : members) a.resetTitle(); }
            public void showBossBar(org.domi.furnace.api.bossbar.BossBar b) { for (Audience a : members) a.showBossBar(b); }
            public void hideBossBar(org.domi.furnace.api.bossbar.BossBar b) { for (Audience a : members) a.hideBossBar(b); }
            public void showSidebar(org.domi.furnace.api.scoreboard.Sidebar s) { for (Audience a : members) a.showSidebar(s); }
            public void hideSidebar(org.domi.furnace.api.scoreboard.Sidebar s) { for (Audience a : members) a.hideSidebar(s); }
            public void playSound(Sound s) { for (Audience a : members) a.playSound(s); }
            public void playSound(Sound s, double x, double y, double z) { for (Audience a : members) a.playSound(s, x, y, z); }
            public void stopSound(Sound.Source src, String k) { for (Audience a : members) a.stopSound(src, k); }
            public void sendPlayerListHeaderAndFooter(Text h, Text f) { for (Audience a : members) a.sendPlayerListHeaderAndFooter(h, f); }
        };
    }
}
