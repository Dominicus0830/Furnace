package net.minecraft;

import net.minecraft.world.level.storage.DataVersion;

/**
 * 이 서버의 판(Mojang 이름 shim, N2b-6). 바닐라는 jar 안 version.json 을 읽지만 Furnace 는 1.21.1 고정이라 값을 그대로 둔다
 * (빌드 시각만 바닐라 version.json 과 다르다 — Furnace 빌드가 아니라 1.21.1 출시 시각을 준다).
 */
public class DetectedVersion implements WorldVersion {

    public static final WorldVersion BUILT_IN = new DetectedVersion();

    private static final java.util.Date BUILD_TIME = java.util.Date.from(java.time.Instant.parse("2024-08-08T12:24:45Z"));

    private DetectedVersion() {}

    @Override
    public DataVersion getDataVersion() { return new DataVersion(SharedConstants.WORLD_VERSION, SharedConstants.SERIES); }

    @Override
    public String getId() { return SharedConstants.VERSION_STRING; }

    @Override
    public String getName() { return SharedConstants.VERSION_STRING; }

    @Override
    public int getProtocolVersion() { return SharedConstants.getProtocolVersion(); }

    @Override
    public java.util.Date getBuildTime() { return BUILD_TIME; }

    @Override
    public boolean isStable() { return true; }

    public static WorldVersion tryDetectVersion() { return BUILT_IN; }
}
