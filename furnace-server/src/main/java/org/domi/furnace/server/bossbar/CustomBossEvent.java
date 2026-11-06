// 바닐라 CustomBossEvent(+ServerBossEvent) — /bossbar 로 만드는 보스바 하나. 이름·색·모양·값·최대·가시성·표시 옵션과 플레이어 목록.
// players(UUID) 는 저장되는 대상 목록, online 은 지금 접속해 실제로 패킷을 받는 대상이다.
package org.domi.furnace.server.bossbar;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.domi.furnace.api.text.HoverEvent;
import org.domi.furnace.api.text.NamedTextColor;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.text.TextColor;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.protocol.play.BossEvent;

public final class CustomBossEvent {

    /** 바닐라 BossBarColor — 네트워크 id 순서: 분홍 파랑 빨강 초록 노랑 보라 흰색. 서식색은 이름 장식에 쓴다. */
    public enum Color {
        PINK("pink", NamedTextColor.RED), BLUE("blue", NamedTextColor.BLUE), RED("red", NamedTextColor.DARK_RED),
        GREEN("green", NamedTextColor.GREEN), YELLOW("yellow", NamedTextColor.YELLOW), PURPLE("purple", NamedTextColor.DARK_PURPLE),
        WHITE("white", NamedTextColor.WHITE);

        private final String key;
        private final TextColor formatting;

        Color(String key, TextColor formatting) {
            this.key = key;
            this.formatting = formatting;
        }

        public String key() { return key; }

        public TextColor formatting() { return formatting; }

        public static Color byName(String name) {
            for (Color c : values()) if (c.key.equals(name)) return c;
            return WHITE;
        }
    }

    /** 바닐라 BossBarOverlay — 네트워크 id 순서: 진행 막대, 6·10·12·20칸. */
    public enum Overlay {
        PROGRESS("progress"), NOTCHED_6("notched_6"), NOTCHED_10("notched_10"), NOTCHED_12("notched_12"), NOTCHED_20("notched_20");

        private final String key;

        Overlay(String key) { this.key = key; }

        public String key() { return key; }

        public static Overlay byName(String name) {
            for (Overlay o : values()) if (o.key.equals(name)) return o;
            return PROGRESS;
        }
    }

    private final ResourceId id;
    private final UUID uuid = UUID.randomUUID();
    private final Function<UUID, Player> lookup;
    private Text name;
    private Color color = Color.WHITE;
    private Overlay overlay = Overlay.PROGRESS;
    private float progress;
    private boolean visible = true;
    private boolean darkenScreen, playBossMusic, createWorldFog;
    private int value;
    private int max = 100;
    private final Set<UUID> players = new LinkedHashSet<>();
    private final Set<Player> online = new LinkedHashSet<>();

    CustomBossEvent(ResourceId id, Text name, Function<UUID, Player> lookup) {
        this.id = id;
        this.name = name;
        this.lookup = lookup;
    }

    public ResourceId id() { return id; }

    /** 클라이언트가 보스바를 구분하는 UUID(저장하지 않는다 — 바닐라도 생성 때마다 새로 만든다). */
    public UUID uuid() { return uuid; }

    public Text name() { return name; }

    public Color color() { return color; }

    public Overlay overlay() { return overlay; }

    public float progress() { return progress; }

    public boolean isVisible() { return visible; }

    public boolean darkenScreen() { return darkenScreen; }

    public boolean playBossMusic() { return playBossMusic; }

    public boolean createWorldFog() { return createWorldFog; }

    public int value() { return value; }

    public int max() { return max; }

    /** 저장되는 대상 UUID(오프라인 포함). */
    public synchronized Set<UUID> playerIds() { return new LinkedHashSet<>(players); }

    /** 지금 접속한 대상. */
    public synchronized List<Player> players() { return new ArrayList<>(online); }

    /** 바닐라 getDisplayName — "[이름]" 에 보스바 색·호버(id)·삽입(id)을 입힌다. */
    public Text displayName() {
        String text = id.toString();
        Text styled = name.style(name.style().color(color.formatting()).hoverEvent(HoverEvent.showText(Text.text(text))).insertion(text));
        return Text.translatable("chat.square_brackets", styled);
    }

    // ------------------------------------------------------------------ 패킷

    private int flags() { return (darkenScreen ? 1 : 0) | (playBossMusic ? 2 : 0) | (createWorldFog ? 4 : 0); }

    private BossEvent addPacket() {
        return BossEvent.add(uuid, name, progress, color.ordinal(), overlay.ordinal(), flags());
    }

    private void broadcast(BossEvent pkt) {
        for (Player p : online) p.send(pkt);
    }

    // ------------------------------------------------------------------ 변경(ServerBossEvent 와 같은 변경 감지·전송)

    public synchronized void setName(Text n) {
        if (Objects.equals(n, name)) return;
        name = n;
        broadcast(BossEvent.name(uuid, n));
    }

    public synchronized void setColor(Color c) {
        if (c == color) return;
        color = c;
        broadcast(BossEvent.style(uuid, color.ordinal(), overlay.ordinal()));
    }

    public synchronized void setOverlay(Overlay o) {
        if (o == overlay) return;
        overlay = o;
        broadcast(BossEvent.style(uuid, color.ordinal(), overlay.ordinal()));
    }

    public synchronized void setDarkenScreen(boolean v) {
        if (v == darkenScreen) return;
        darkenScreen = v;
        broadcast(BossEvent.properties(uuid, flags()));
    }

    public synchronized void setPlayBossMusic(boolean v) {
        if (v == playBossMusic) return;
        playBossMusic = v;
        broadcast(BossEvent.properties(uuid, flags()));
    }

    public synchronized void setCreateWorldFog(boolean v) {
        if (v == createWorldFog) return;
        createWorldFog = v;
        broadcast(BossEvent.properties(uuid, flags()));
    }

    private void setProgress(float p) {
        if (p == progress) return;
        progress = p;
        broadcast(BossEvent.progress(uuid, p));
    }

    /** 바닐라 setValue: 진행도 = clamp(값/최대, 0, 1). */
    public synchronized void setValue(int v) {
        value = v;
        setProgress(clamp((float) v / (float) max));
    }

    public synchronized void setMax(int m) {
        max = m;
        setProgress(clamp((float) value / (float) m));
    }

    private static float clamp(float f) { return f < 0.0F ? 0.0F : Math.min(f, 1.0F); }

    public synchronized void setVisible(boolean v) {
        if (v == visible) return;
        visible = v;
        BossEvent pkt = v ? addPacket() : BossEvent.remove(uuid);
        for (Player p : online) p.send(pkt);
    }

    // ------------------------------------------------------------------ 플레이어

    /** 바닐라 CustomBossEvent.addPlayer: 접속자 집합에 넣고(보이면 추가 패킷) 대상 UUID 에도 적는다. */
    public synchronized void addPlayer(Player p) {
        if (online.add(p) && visible) p.send(addPacket());
        players.add(p.uuid());
    }

    public synchronized void removePlayer(Player p) {
        if (online.remove(p) && visible) p.send(BossEvent.remove(uuid));
        players.remove(p.uuid());
    }

    /** 저장에서 읽은 오프라인 대상(바닐라 addOfflinePlayer). */
    public synchronized void addOfflinePlayer(UUID u) { players.add(u); }

    public synchronized void removeAllPlayers() {
        for (Player p : new ArrayList<>(online)) removePlayer(p);
        players.clear();
    }

    /** 바닐라 setPlayers: 목록에 없는 대상은 빼고 새 대상은 넣는다. 바뀐 것이 있으면 true. */
    public synchronized boolean setPlayers(java.util.Collection<Player> wanted) {
        Set<UUID> wantedIds = new LinkedHashSet<>();
        for (Player p : wanted) wantedIds.add(p.uuid());
        List<UUID> toRemove = new ArrayList<>();
        for (UUID u : players) if (!wantedIds.contains(u)) toRemove.add(u);
        List<Player> toAdd = new ArrayList<>();
        for (Player p : wanted) if (!players.contains(p.uuid())) toAdd.add(p);
        for (UUID u : toRemove) {
            Player p = lookup.apply(u);
            if (p != null) removePlayer(p);
            else players.remove(u);
        }
        for (Player p : toAdd) addPlayer(p);
        return !toRemove.isEmpty() || !toAdd.isEmpty();
    }

    /** 접속: 대상 목록에 있으면 보스바에 넣는다. */
    public synchronized void onPlayerConnect(Player p) {
        if (players.contains(p.uuid())) addPlayer(p);
    }

    /** 접속 종료: 패킷만 정리하고 대상 UUID 는 남긴다(다시 접속하면 복귀). */
    public synchronized void onPlayerDisconnect(Player p) {
        if (online.remove(p) && visible) p.send(BossEvent.remove(uuid));
    }
}
