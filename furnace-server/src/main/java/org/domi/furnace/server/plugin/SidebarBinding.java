// 플러그인 Sidebar 하나를 한 플레이어 화면에 잇는다. 서버 점수판은 건드리지 않고 이 플레이어에게만 목표(fs…)·점수 패킷을 보낸다.
// 줄 i 의 점수는 (줄 수 - i) — 클라이언트가 점수 큰 순으로 그려서 위에서 아래 순서가 된다. 점수 숫자는 blank 형식으로 숨긴다.
// 바뀐 줄만 다시 보낸다(줄 수가 바뀌면 점수가 밀리므로 전부).
package org.domi.furnace.server.plugin;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.domi.furnace.api.scoreboard.Sidebar;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.protocol.play.ResetScore;
import org.domi.furnace.server.net.protocol.play.SetDisplayObjective;
import org.domi.furnace.server.net.protocol.play.SetObjective;
import org.domi.furnace.server.net.protocol.play.SetScore;
import org.domi.furnace.server.scores.DisplaySlot;
import org.domi.furnace.server.scores.NumberFormat;
import org.domi.furnace.server.scores.ObjectiveCriteria;

final class SidebarBinding implements Sidebar.Listener {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private final Player p;
    private final Sidebar sidebar;
    private final String objective = "fs" + Integer.toHexString(COUNTER.incrementAndGet() & 0xFFFFFF);
    private Text sentTitle;
    private List<Text> sentLines = List.of();
    private boolean shown;

    SidebarBinding(Player p, Sidebar sidebar) {
        this.p = p;
        this.sidebar = sidebar;
    }

    Sidebar sidebar() { return sidebar; }

    synchronized void show() {
        if (shown) return;
        shown = true;
        sidebar.addListener(this);
        sentTitle = sidebar.title();
        List<Text> lines = sidebar.lines();
        p.send(new SetObjective(objective, SetObjective.METHOD_ADD, p.localize(sentTitle), ObjectiveCriteria.RenderType.INTEGER, NumberFormat.BLANK));
        p.send(new SetDisplayObjective(DisplaySlot.SIDEBAR, objective));
        for (int i = 0; i < lines.size(); i++) sendLine(i, lines.size(), lines.get(i));
        sentLines = lines;
    }

    /** 화면에서 지운다. restore 가 있으면 서버 점수판의 사이드바 목표를 다시 건다. */
    synchronized void hide(org.domi.furnace.server.scores.Objective restore) {
        if (!shown) return;
        shown = false;
        sidebar.removeListener(this);
        p.send(new SetDisplayObjective(DisplaySlot.SIDEBAR, restore == null ? "" : restore.name()));
        p.send(new SetObjective(objective, SetObjective.METHOD_REMOVE, null, null, null));
    }

    /** 접속이 끊겼다 — 패킷 없이 구독만 푼다. */
    synchronized void detach() {
        shown = false;
        sidebar.removeListener(this);
    }

    private void sendLine(int index, int count, Text text) {
        p.send(new SetScore("l" + index, objective, count - index, p.localize(text), NumberFormat.BLANK));
    }

    @Override
    public synchronized void changed(Sidebar s) {
        if (!shown) return;
        if (p.isDisconnected()) {
            detach();
            return;
        }
        Text title = s.title();
        if (!title.equals(sentTitle)) {
            sentTitle = title;
            p.send(new SetObjective(objective, SetObjective.METHOD_CHANGE, p.localize(title), ObjectiveCriteria.RenderType.INTEGER, NumberFormat.BLANK));
        }
        List<Text> next = s.lines();
        boolean all = next.size() != sentLines.size();
        for (int i = 0; i < next.size(); i++) {
            if (all || !next.get(i).equals(sentLines.get(i))) sendLine(i, next.size(), next.get(i));
        }
        for (int i = next.size(); i < sentLines.size(); i++) p.send(new ResetScore("l" + i, objective));
        sentLines = next;
    }
}
