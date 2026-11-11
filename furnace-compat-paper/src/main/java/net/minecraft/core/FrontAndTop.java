package net.minecraft.core;

import net.minecraft.util.StringRepresentable;

/** 블록 상태 속성 값(Mojang 이름 shim, N0). 상수 이름·순서·직렬화 이름은 바닐라 1.21.1 바이트코드의 정적 초기화와 같다. toString 은 바닐라처럼 덮어쓰지 않는다(상수 이름). */
public enum FrontAndTop implements StringRepresentable {
    DOWN_EAST("down_east"),
    DOWN_NORTH("down_north"),
    DOWN_SOUTH("down_south"),
    DOWN_WEST("down_west"),
    UP_EAST("up_east"),
    UP_NORTH("up_north"),
    UP_SOUTH("up_south"),
    UP_WEST("up_west"),
    WEST_UP("west_up"),
    EAST_UP("east_up"),
    NORTH_UP("north_up"),
    SOUTH_UP("south_up");

    private final String name;

    FrontAndTop(String name) { this.name = name; }

    @Override
    public String getSerializedName() { return name; }
}
