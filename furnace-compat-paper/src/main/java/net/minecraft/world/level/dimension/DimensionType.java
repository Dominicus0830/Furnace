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

    private static final float[] MOON_BRIGHTNESS_PER_PHASE = {1.0F, 0.75F, 0.5F, 0.25F, 0.0F, 0.25F, 0.5F, 0.75F};

    /** 바닐라 DimensionType#moonPhase. */
    public int moonPhase(long time) { return (int) (time / 24000L % 8L + 8L) % 8; }

    /** 바닐라 DimensionType#moonBrightness. */
    public float moonBrightness(long time) { return MOON_BRIGHTNESS_PER_PHASE[moonPhase(time)]; }

    /** 바닐라 DimensionType#getStorageFolder: 차원 키의 저장 폴더(오버월드 = 월드 폴더, 엔드 = DIM1, 네더 = DIM-1, 그 밖은 dimensions/<이름공간>/<경로>). */
    public static java.nio.file.Path getStorageFolder(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimensionKey, java.nio.file.Path levelFolder) {
        if (dimensionKey == net.minecraft.world.level.Level.OVERWORLD) return levelFolder;
        if (dimensionKey == net.minecraft.world.level.Level.END) return levelFolder.resolve("DIM1");
        if (dimensionKey == net.minecraft.world.level.Level.NETHER) return levelFolder.resolve("DIM-1");
        return levelFolder.resolve("dimensions").resolve(dimensionKey.location().getNamespace()).resolve(dimensionKey.location().getPath());
    }

    public boolean hasCeiling() { return org.domi.furnace.compat.nms.NmsChunks.bridge().hasCeiling(world); }

    public int minY() { return org.domi.furnace.compat.nms.NmsChunks.bridge().minY(world); }

    public int height() { return org.domi.furnace.compat.nms.NmsChunks.bridge().height(world); }

    public boolean hasSkyLight() { return org.domi.furnace.compat.nms.NmsChunks.bridge().hasSkyLight(world); }

    public boolean ultraWarm() { return org.domi.furnace.compat.nms.NmsChunks.bridge().ultraWarm(world); }
}
