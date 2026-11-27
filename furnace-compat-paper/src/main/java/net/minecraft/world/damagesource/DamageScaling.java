package net.minecraft.world.damagesource;

import net.minecraft.util.StringRepresentable;

/** 난이도에 따라 피해가 커지는가(바닐라 DamageScaling, 데이터 팩 이름 그대로). */
public enum DamageScaling implements StringRepresentable {
    NEVER("never"),
    WHEN_CAUSED_BY_LIVING_NON_PLAYER("when_caused_by_living_non_player"),
    ALWAYS("always");

    private final String id;

    DamageScaling(String id) { this.id = id; }

    @Override
    public String getSerializedName() { return id; }
}
