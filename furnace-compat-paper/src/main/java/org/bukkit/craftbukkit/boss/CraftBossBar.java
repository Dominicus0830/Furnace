// Bukkit.createBossBar 의 보스바 — Furnace 보스바(org.domi.furnace.api.bossbar.BossBar) 위에 있다.
// 보는 사람 목록은 이 객체가 갖고(Paper 처럼 접속이 끊겨도 목록에서 저절로 빠지지 않는다), 숨기면 목록은 그대로 두고 화면에서만 뺀다.
package org.bukkit.craftbukkit.boss;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.domi.furnace.compat.paper.Compat;

public class CraftBossBar implements BossBar {

    private final org.domi.furnace.api.bossbar.BossBar handle;
    private final Set<org.domi.furnace.api.entity.Player> players = new LinkedHashSet<>();   // this 로 동기화
    private boolean visible = true;   // this 로 동기화

    public CraftBossBar(String title, BarColor color, BarStyle style, BarFlag... flags) {
        java.util.Objects.requireNonNull(color, "color");
        java.util.Objects.requireNonNull(style, "style");
        EnumSet<org.domi.furnace.api.bossbar.BossBar.Flag> fs = EnumSet.noneOf(org.domi.furnace.api.bossbar.BossBar.Flag.class);
        for (BarFlag f : flags) if (f != null) fs.add(flag(f));
        handle = org.domi.furnace.api.bossbar.BossBar.of(text(title), 1f, color(color), overlay(style));
        handle.flags(fs);
    }

    /** 호환 모듈 전용 — 밑에 있는 Furnace 보스바. */
    public org.domi.furnace.api.bossbar.BossBar furnaceHandle() { return handle; }

    private static org.domi.furnace.api.text.Text text(String title) {
        return Compat.text(title == null ? "" : title);
    }

    @Override
    public String getTitle() { return Compat.legacy(handle.name()); }

    @Override
    public void setTitle(String title) { handle.name(text(title)); }

    @Override
    public BarColor getColor() { return BarColor.valueOf(handle.color().name()); }

    @Override
    public void setColor(BarColor color) {
        java.util.Objects.requireNonNull(color, "color");
        handle.color(color(color));
    }

    @Override
    public BarStyle getStyle() {
        return switch (handle.overlay()) {
            case PROGRESS -> BarStyle.SOLID;
            case NOTCHED_6 -> BarStyle.SEGMENTED_6;
            case NOTCHED_10 -> BarStyle.SEGMENTED_10;
            case NOTCHED_12 -> BarStyle.SEGMENTED_12;
            case NOTCHED_20 -> BarStyle.SEGMENTED_20;
        };
    }

    @Override
    public void setStyle(BarStyle style) {
        java.util.Objects.requireNonNull(style, "style");
        handle.overlay(overlay(style));
    }

    @Override
    public void removeFlag(BarFlag flag) { if (flag != null) handle.removeFlag(flag(flag)); }

    @Override
    public void addFlag(BarFlag flag) { if (flag != null) handle.addFlag(flag(flag)); }

    @Override
    public boolean hasFlag(BarFlag flag) { return flag != null && handle.hasFlag(flag(flag)); }

    @Override
    public void setProgress(double progress) {
        if (!(progress >= 0.0 && progress <= 1.0)) throw new IllegalArgumentException("Progress must be between 0.0 and 1.0 (" + progress + ")");
        handle.progress((float) progress);
    }

    @Override
    public double getProgress() { return handle.progress(); }

    @Override
    public void addPlayer(Player player) {
        if (player == null) throw new IllegalArgumentException("player == null");
        if (!player.isOnline()) throw new IllegalArgumentException("player is not fully connected (wait for PlayerJoinEvent)");
        var fp = ((CraftPlayer) player).furnaceHandle();
        boolean show;
        synchronized (this) {
            show = players.add(fp) && visible;
        }
        if (show) fp.showBossBar(handle);
    }

    @Override
    public void removePlayer(Player player) {
        if (player == null) throw new IllegalArgumentException("player == null");
        var fp = ((CraftPlayer) player).furnaceHandle();
        boolean was;
        synchronized (this) { was = players.remove(fp); }
        if (was) fp.hideBossBar(handle);
    }

    @Override
    public void removeAll() {
        List<org.domi.furnace.api.entity.Player> all;
        synchronized (this) {
            all = new ArrayList<>(players);
            players.clear();
        }
        for (var fp : all) fp.hideBossBar(handle);
    }

    @Override
    public List<Player> getPlayers() {
        List<org.domi.furnace.api.entity.Player> all;
        synchronized (this) { all = new ArrayList<>(players); }
        List<Player> out = new ArrayList<>(all.size());
        for (var fp : all) out.add(Compat.player(fp));
        return java.util.Collections.unmodifiableList(out);
    }

    @Override
    public void setVisible(boolean visible) {
        List<org.domi.furnace.api.entity.Player> all;
        synchronized (this) {
            if (this.visible == visible) return;
            this.visible = visible;
            all = new ArrayList<>(players);
        }
        for (var fp : all) {
            if (visible) fp.showBossBar(handle);
            else fp.hideBossBar(handle);
        }
    }

    @Override
    public synchronized boolean isVisible() { return visible; }

    @Override
    @Deprecated
    public void show() { setVisible(true); }

    @Override
    @Deprecated
    public void hide() { setVisible(false); }

    private static org.domi.furnace.api.bossbar.BossBar.Color color(BarColor c) {
        return org.domi.furnace.api.bossbar.BossBar.Color.valueOf(c.name());
    }

    private static org.domi.furnace.api.bossbar.BossBar.Overlay overlay(BarStyle s) {
        return switch (s) {
            case SOLID -> org.domi.furnace.api.bossbar.BossBar.Overlay.PROGRESS;
            case SEGMENTED_6 -> org.domi.furnace.api.bossbar.BossBar.Overlay.NOTCHED_6;
            case SEGMENTED_10 -> org.domi.furnace.api.bossbar.BossBar.Overlay.NOTCHED_10;
            case SEGMENTED_12 -> org.domi.furnace.api.bossbar.BossBar.Overlay.NOTCHED_12;
            case SEGMENTED_20 -> org.domi.furnace.api.bossbar.BossBar.Overlay.NOTCHED_20;
        };
    }

    private static org.domi.furnace.api.bossbar.BossBar.Flag flag(BarFlag f) {
        return switch (f) {
            case DARKEN_SKY -> org.domi.furnace.api.bossbar.BossBar.Flag.DARKEN_SCREEN;
            case PLAY_BOSS_MUSIC -> org.domi.furnace.api.bossbar.BossBar.Flag.PLAY_BOSS_MUSIC;
            case CREATE_FOG -> org.domi.furnace.api.bossbar.BossBar.Flag.CREATE_WORLD_FOG;
        };
    }
}
