package net.minecraft.world.flag;

/** 바닐라 FeatureFlagUniverse(Mojang 이름 shim, T-211 3단계). */
public class FeatureFlagUniverse {

    private final String id;

    public FeatureFlagUniverse(String name) { this.id = name; }

    @Override
    public String toString() { return id; }
}
