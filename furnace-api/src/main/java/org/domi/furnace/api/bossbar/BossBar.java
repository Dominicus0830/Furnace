// 보스바 — 이름·진행도·색·모양·옵션을 가진 값 객체. 한 번 만들어 여러 플레이어에게 보이면(Audience.showBossBar) 바꿀 때 모두에게 반영된다.
// 바닐라 /bossbar 와 달리 저장되지 않는 일회용이다(서버를 다시 켜면 사라짐). 어느 스레드에서 바꿔도 된다.
package org.domi.furnace.api.bossbar;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.domi.furnace.api.text.Text;

public final class BossBar {

    /** 막대 색(바닐라 BossBarColor). */
    public enum Color { PINK, BLUE, RED, GREEN, YELLOW, PURPLE, WHITE }

    /** 막대 모양: 연속 막대 또는 6·10·12·20칸으로 나뉜 막대. */
    public enum Overlay { PROGRESS, NOTCHED_6, NOTCHED_10, NOTCHED_12, NOTCHED_20 }

    /** 화면 효과 옵션. */
    public enum Flag { DARKEN_SCREEN, PLAY_BOSS_MUSIC, CREATE_WORLD_FOG }

    /** 무엇이 바뀌었는지. */
    public enum Change { NAME, PROGRESS, STYLE, FLAGS }

    /** 서버 구현이 보는 플레이어에게 변경을 전하는 데 쓴다. 플러그인은 쓸 일이 없다. */
    public interface Listener {
        void changed(BossBar bar, Change change);
    }

    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private volatile Text name;
    private volatile float progress;
    private volatile Color color;
    private volatile Overlay overlay;
    private volatile Set<Flag> flags;

    private BossBar(Text name, float progress, Color color, Overlay overlay, Set<Flag> flags) {
        this.name = Objects.requireNonNull(name, "name");
        this.progress = clamp(progress);
        this.color = Objects.requireNonNull(color, "color");
        this.overlay = Objects.requireNonNull(overlay, "overlay");
        this.flags = flags.isEmpty() ? Set.of() : java.util.Collections.unmodifiableSet(EnumSet.copyOf(flags));
    }

    /** 이름과 진행도(0..1)로 만든다. 색은 흰색, 모양은 연속 막대. */
    public static BossBar of(Text name, float progress) {
        return new BossBar(name, progress, Color.WHITE, Overlay.PROGRESS, Set.of());
    }

    public static BossBar of(Text name, float progress, Color color, Overlay overlay, Flag... flags) {
        return new BossBar(name, progress, color, overlay, flags.length == 0 ? Set.of() : EnumSet.copyOf(java.util.List.of(flags)));
    }

    private static float clamp(float p) {
        if (Float.isNaN(p)) throw new IllegalArgumentException("진행도가 NaN");
        return p < 0f ? 0f : Math.min(p, 1f);
    }

    public Text name() { return name; }

    /** 0..1. */
    public float progress() { return progress; }

    public Color color() { return color; }

    public Overlay overlay() { return overlay; }

    /** 바꿀 수 없는 사본. */
    public Set<Flag> flags() { return flags; }

    public boolean hasFlag(Flag flag) { return flags.contains(flag); }

    public BossBar name(Text name) {
        Objects.requireNonNull(name, "name");
        if (name.equals(this.name)) return this;
        this.name = name;
        fire(Change.NAME);
        return this;
    }

    /** 0..1 로 자른다(NaN 은 IllegalArgumentException). */
    public BossBar progress(float progress) {
        float p = clamp(progress);
        if (p == this.progress) return this;
        this.progress = p;
        fire(Change.PROGRESS);
        return this;
    }

    public BossBar color(Color color) {
        Objects.requireNonNull(color, "color");
        if (color == this.color) return this;
        this.color = color;
        fire(Change.STYLE);
        return this;
    }

    public BossBar overlay(Overlay overlay) {
        Objects.requireNonNull(overlay, "overlay");
        if (overlay == this.overlay) return this;
        this.overlay = overlay;
        fire(Change.STYLE);
        return this;
    }

    public BossBar flags(Set<Flag> flags) {
        Set<Flag> next = flags.isEmpty() ? Set.of() : java.util.Collections.unmodifiableSet(EnumSet.copyOf(flags));
        if (next.equals(this.flags)) return this;
        this.flags = next;
        fire(Change.FLAGS);
        return this;
    }

    public BossBar addFlag(Flag flag) {
        EnumSet<Flag> s = flags.isEmpty() ? EnumSet.noneOf(Flag.class) : EnumSet.copyOf(flags);
        s.add(flag);
        return flags(s);
    }

    public BossBar removeFlag(Flag flag) {
        if (!flags.contains(flag)) return this;
        EnumSet<Flag> s = EnumSet.copyOf(flags);
        s.remove(flag);
        return flags(s);
    }

    /** 서버 구현 전용. */
    public void addListener(Listener l) { listeners.add(Objects.requireNonNull(l)); }

    /** 서버 구현 전용. */
    public void removeListener(Listener l) { listeners.remove(l); }

    private void fire(Change change) {
        for (Listener l : listeners) l.changed(this, change);
    }
}
