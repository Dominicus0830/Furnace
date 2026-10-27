// 바닐라 DisplaySlot — 점수판 표시 자리. id 는 set_display_objective 의 VarInt, key 는 명령·저장(DisplaySlots) 이름.
package org.domi.furnace.server.scores;

public enum DisplaySlot {
    LIST(0, "list"),
    SIDEBAR(1, "sidebar"),
    BELOW_NAME(2, "below_name"),
    TEAM_BLACK(3, "sidebar.team.black"),
    TEAM_DARK_BLUE(4, "sidebar.team.dark_blue"),
    TEAM_DARK_GREEN(5, "sidebar.team.dark_green"),
    TEAM_DARK_AQUA(6, "sidebar.team.dark_aqua"),
    TEAM_DARK_RED(7, "sidebar.team.dark_red"),
    TEAM_DARK_PURPLE(8, "sidebar.team.dark_purple"),
    TEAM_GOLD(9, "sidebar.team.gold"),
    TEAM_GRAY(10, "sidebar.team.gray"),
    TEAM_DARK_GRAY(11, "sidebar.team.dark_gray"),
    TEAM_BLUE(12, "sidebar.team.blue"),
    TEAM_GREEN(13, "sidebar.team.green"),
    TEAM_AQUA(14, "sidebar.team.aqua"),
    TEAM_RED(15, "sidebar.team.red"),
    TEAM_LIGHT_PURPLE(16, "sidebar.team.light_purple"),
    TEAM_YELLOW(17, "sidebar.team.yellow"),
    TEAM_WHITE(18, "sidebar.team.white");

    private final int id;
    private final String key;

    DisplaySlot(int id, String key) {
        this.id = id;
        this.key = key;
    }

    public int id() { return id; }

    public String key() { return key; }

    /** 이름으로(없으면 null). */
    public static DisplaySlot byName(String s) {
        for (DisplaySlot d : values()) if (d.key.equals(s)) return d;
        return null;
    }

    /** 바닐라 teamColorToSlot: 색이면 그 팀 사이드바, 아니면 null. */
    public static DisplaySlot teamColorToSlot(ChatFormatting f) {
        return f.isColor() ? values()[3 + f.id()] : null;
    }
}
