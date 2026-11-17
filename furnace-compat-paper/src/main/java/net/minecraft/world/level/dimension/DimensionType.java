package net.minecraft.world.level.dimension;

/**
 * 바닐라 차원 타입(Mojang 이름, T-203 — {@code ServerLevel#dimensionTypeRegistration()} 의 값). 높이·하늘빛·초고온만 그 월드에서 읽는다.
 * 같은 월드는 같은 객체다.
 */
public final class DimensionType {

    private final org.domi.furnace.api.world.World world;

    /** 호환 모듈 전용. */
    public DimensionType(org.domi.furnace.api.world.World world) { this.world = world; }

    /** 이 값의 월드(호환 모듈 전용). */
    public org.domi.furnace.api.world.World furnaceWorld() { return world; }

    /** 차원 타입 키(minecraft:overworld …). */
    public String furnaceTypeKey() { return world.dimensionType(); }

    public int minY() { return org.domi.furnace.compat.nms.NmsChunks.bridge().minY(world); }

    public int height() { return org.domi.furnace.compat.nms.NmsChunks.bridge().height(world); }

    public boolean hasSkyLight() { return org.domi.furnace.compat.nms.NmsChunks.bridge().hasSkyLight(world); }

    public boolean ultraWarm() { return org.domi.furnace.compat.nms.NmsChunks.bridge().ultraWarm(world); }
}
