// 바닐라 DyeColor: 번호·이름. (양털 색 등에 쓴다)
package org.domi.furnace.server.entity;

public enum DyeColor {
    WHITE("white"), ORANGE("orange"), MAGENTA("magenta"), LIGHT_BLUE("light_blue"), YELLOW("yellow"), LIME("lime"), PINK("pink"), GRAY("gray"),
    LIGHT_GRAY("light_gray"), CYAN("cyan"), PURPLE("purple"), BLUE("blue"), BROWN("brown"), GREEN("green"), RED("red"), BLACK("black");

    private final String key;

    DyeColor(String key) { this.key = key; }

    public int id() { return ordinal(); }

    public String key() { return key; }

    /** DyeColor.byId: 범위를 넘으면 흰색(ByIdMap.OutOfBoundsStrategy.ZERO). */
    public static DyeColor byId(int id) { return id >= 0 && id < 16 ? values()[id] : WHITE; }
}
