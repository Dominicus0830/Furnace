package net.minecraft.world.damagesource;

import net.minecraft.util.StringRepresentable;

/** 죽음 메시지 꼴(바닐라 DeathMessageType, 데이터 팩 이름 그대로). */
public enum DeathMessageType implements StringRepresentable {
    DEFAULT("default"),
    FALL_VARIANTS("fall_variants"),
    INTENTIONAL_GAME_DESIGN("intentional_game_design");

    private final String id;

    DeathMessageType(String id) { this.id = id; }

    @Override
    public String getSerializedName() { return id; }
}
