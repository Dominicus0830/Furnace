// 화면 오른쪽 점수판(사이드바) — 제목 하나와 위에서 아래로 최대 15줄의 글. 점수 숫자는 보이지 않고, 글은 40자 제한 없이 서식을 쓸 수 있다.
// 바닐라 점수판(/scoreboard)과 따로 움직인다: 서버 점수판에 목표·점수를 만들지 않고 보는 플레이어에게만 보낸다.
// 한 플레이어에게는 사이드바 하나만 보인다(새로 보이면 바뀐다). 숨기면 서버 점수판의 사이드바 목표가 있으면 그것이 돌아온다.
// 어느 스레드에서 바꿔도 된다.
package org.domi.furnace.api.scoreboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import org.domi.furnace.api.text.Text;

public final class Sidebar {

    /** 클라이언트가 보여 주는 최대 줄 수. */
    public static final int MAX_LINES = 15;

    /** 서버 구현이 보는 플레이어에게 변경을 전하는 데 쓴다. 플러그인은 쓸 일이 없다. */
    public interface Listener {
        void changed(Sidebar sidebar);
    }

    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private volatile Text title;
    private volatile List<Text> lines = List.of();

    private Sidebar(Text title) { this.title = Objects.requireNonNull(title, "title"); }

    public static Sidebar of(Text title) { return new Sidebar(title); }

    public static Sidebar of(Text title, List<Text> lines) { return new Sidebar(title).lines(lines); }

    public Text title() { return title; }

    /** 위에서 아래 순서, 바꿀 수 없는 사본. */
    public List<Text> lines() { return lines; }

    public Sidebar title(Text title) {
        Objects.requireNonNull(title, "title");
        if (title.equals(this.title)) return this;
        this.title = title;
        fire();
        return this;
    }

    /** 모든 줄을 한 번에 바꾼다(15줄 넘으면 IllegalArgumentException). 보는 사람에게는 바뀐 줄만 간다. */
    public synchronized Sidebar lines(List<Text> lines) {
        List<Text> copy = List.copyOf(lines);
        if (copy.size() > MAX_LINES) throw new IllegalArgumentException("사이드바는 최대 " + MAX_LINES + "줄: " + copy.size());
        if (copy.equals(this.lines)) return this;
        this.lines = copy;
        fire();
        return this;
    }

    public Sidebar lines(Text... lines) { return lines(List.of(lines)); }

    /** 한 줄을 바꾼다. 지금 줄 수 끝(index == 줄 수)이면 덧붙인다. */
    public synchronized Sidebar line(int index, Text text) {
        Objects.requireNonNull(text, "text");
        List<Text> next = new ArrayList<>(lines);
        if (index == next.size()) next.add(text);
        else next.set(index, text);
        return lines(next);
    }

    public synchronized Sidebar addLine(Text text) {
        Objects.requireNonNull(text, "text");
        List<Text> next = new ArrayList<>(lines);
        next.add(text);
        return lines(next);
    }

    public synchronized Sidebar clearLines() { return lines(List.of()); }

    /** 서버 구현 전용. */
    public void addListener(Listener l) { listeners.add(Objects.requireNonNull(l)); }

    /** 서버 구현 전용. */
    public void removeListener(Listener l) { listeners.remove(l); }

    private void fire() {
        for (Listener l : listeners) l.changed(this);
    }
}
