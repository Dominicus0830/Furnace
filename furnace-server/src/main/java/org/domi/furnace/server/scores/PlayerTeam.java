// 바닐라 PlayerTeam(+ Team 의 Visibility·CollisionRule). 멤버는 점수판 이름(플레이어 이름·엔티티 UUID 문자열)이다.
// 멤버 목록은 바닐라처럼 HashSet(같은 삽입이면 같은 순서 — 저장·패킷 순서).
package org.domi.furnace.server.scores;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.domi.furnace.api.text.HoverEvent;
import org.domi.furnace.api.text.Style;
import org.domi.furnace.api.text.Text;

public final class PlayerTeam {

    /** 바닐라 Team.Visibility(이름표·사망 메시지). */
    public enum Visibility {
        ALWAYS("always", 0),
        NEVER("never", 1),
        HIDE_FOR_OTHER_TEAMS("hideForOtherTeams", 2),
        HIDE_FOR_OWN_TEAM("hideForOwnTeam", 3);

        public final String name;
        public final int id;

        Visibility(String name, int id) {
            this.name = name;
            this.id = id;
        }

        public static Visibility byName(String s) {
            for (Visibility v : values()) if (v.name.equals(s)) return v;
            return null;
        }

        public Text displayName() { return Text.translatable("team.visibility." + name); }
    }

    /** 바닐라 Team.CollisionRule. */
    public enum CollisionRule {
        ALWAYS("always", 0),
        NEVER("never", 1),
        PUSH_OTHER_TEAMS("pushOtherTeams", 2),
        PUSH_OWN_TEAM("pushOwnTeam", 3);

        public final String name;
        public final int id;

        CollisionRule(String name, int id) {
            this.name = name;
            this.id = id;
        }

        public static CollisionRule byName(String s) {
            for (CollisionRule v : values()) if (v.name.equals(s)) return v;
            return null;
        }

        public Text displayName() { return Text.translatable("team.collision." + name); }
    }

    private final Scoreboard scoreboard;
    private final String name;
    private final Set<String> players = new HashSet<>();
    private Text displayName;
    private Text playerPrefix = Text.empty();
    private Text playerSuffix = Text.empty();
    private boolean allowFriendlyFire = true;
    private boolean seeFriendlyInvisibles = true;
    private Visibility nameTagVisibility = Visibility.ALWAYS;
    private Visibility deathMessageVisibility = Visibility.ALWAYS;
    private ChatFormatting color = ChatFormatting.RESET;
    private CollisionRule collisionRule = CollisionRule.ALWAYS;
    private final Style displayNameStyle;

    PlayerTeam(Scoreboard scoreboard, String name) {
        this.scoreboard = scoreboard;
        this.name = name;
        this.displayName = Text.text(name);
        this.displayNameStyle = Style.empty().insertion(name).hoverEvent(HoverEvent.showText(Text.text(name)));
    }

    public Scoreboard scoreboard() { return scoreboard; }

    public String name() { return name; }

    public Text displayName() { return displayName; }

    /** 바닐라 getFormattedDisplayName: [표시 이름](삽입·호버), 색이 있으면 색. */
    public Text formattedDisplayName() {
        Text t = Text.translatable("chat.square_brackets", displayName.mergeStyle(displayNameStyle));
        ChatFormatting c = color;
        return c != ChatFormatting.RESET && c.isColor() ? t.color(c.textColor()) : t;
    }

    public void displayName(Text name) {
        if (name == null) throw new IllegalArgumentException("Name cannot be null");
        synchronized (scoreboard) {
            this.displayName = name;
            scoreboard.onTeamChanged(this);
        }
    }

    public void playerPrefix(Text prefix) {
        synchronized (scoreboard) {
            this.playerPrefix = prefix == null ? Text.empty() : prefix;
            scoreboard.onTeamChanged(this);
        }
    }

    public Text playerPrefix() { return playerPrefix; }

    public void playerSuffix(Text suffix) {
        synchronized (scoreboard) {
            this.playerSuffix = suffix == null ? Text.empty() : suffix;
            scoreboard.onTeamChanged(this);
        }
    }

    public Text playerSuffix() { return playerSuffix; }

    /** 멤버 목록(살아 있는 집합 — 점수판 잠금 안에서만 읽는다. 밖에서는 {@link #playersCopy()}). */
    Set<String> players() { return players; }

    public java.util.List<String> playersCopy() {
        synchronized (scoreboard) {
            return java.util.List.copyOf(players);
        }
    }

    public boolean hasPlayer(String name) {
        synchronized (scoreboard) {
            return players.contains(name);
        }
    }

    /** 바닐라 getFormattedName(이름): 빈 텍스트에 접두사·이름·접미사를 잇고 색을 입힌다. */
    public Text formatName(Text playerName) {
        Text t = Text.empty().append(playerPrefix, playerName, playerSuffix);
        ChatFormatting c = color;
        return c != ChatFormatting.RESET && c.isColor() ? t.color(c.textColor()) : t;
    }

    /** 바닐라 PlayerTeam.formatNameForTeam(팀 없으면 이름 그대로). */
    public static Text formatNameForTeam(PlayerTeam team, Text name) {
        return team == null ? name : team.formatName(name);
    }

    public boolean isAllowFriendlyFire() { return allowFriendlyFire; }

    public void allowFriendlyFire(boolean v) {
        synchronized (scoreboard) {
            this.allowFriendlyFire = v;
            scoreboard.onTeamChanged(this);
        }
    }

    public boolean canSeeFriendlyInvisibles() { return seeFriendlyInvisibles; }

    public void seeFriendlyInvisibles(boolean v) {
        synchronized (scoreboard) {
            this.seeFriendlyInvisibles = v;
            scoreboard.onTeamChanged(this);
        }
    }

    public Visibility nameTagVisibility() { return nameTagVisibility; }

    public void nameTagVisibility(Visibility v) {
        synchronized (scoreboard) {
            this.nameTagVisibility = v;
            scoreboard.onTeamChanged(this);
        }
    }

    public Visibility deathMessageVisibility() { return deathMessageVisibility; }

    public void deathMessageVisibility(Visibility v) {
        synchronized (scoreboard) {
            this.deathMessageVisibility = v;
            scoreboard.onTeamChanged(this);
        }
    }

    public CollisionRule collisionRule() { return collisionRule; }

    public void collisionRule(CollisionRule v) {
        synchronized (scoreboard) {
            this.collisionRule = v;
            scoreboard.onTeamChanged(this);
        }
    }

    /** 바닐라 packOptions: 1 = 아군 피해, 2 = 투명 아군 보기. */
    public int packOptions() {
        int o = 0;
        if (allowFriendlyFire) o |= 1;
        if (seeFriendlyInvisibles) o |= 2;
        return o;
    }

    public void unpackOptions(int o) {
        allowFriendlyFire((o & 1) > 0);
        seeFriendlyInvisibles((o & 2) > 0);
    }

    public ChatFormatting color() { return color; }

    public void color(ChatFormatting c) {
        synchronized (scoreboard) {
            this.color = c;
            scoreboard.onTeamChanged(this);
        }
    }

    /** 바닐라 Team.isAlliedTo: 같은 팀이면 참. */
    public boolean isAlliedTo(PlayerTeam other) { return other != null && this == other; }

    Collection<String> rawPlayers() { return players; }
}
