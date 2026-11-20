package net.minecraft;

import net.minecraft.world.level.storage.DataVersion;

/**
 * 게임 판 정보(Mojang 이름 shim, N2b-6). {@link SharedConstants#getCurrentVersion()} 이 준다. FAWE 어댑터가 데이터 판(3955)으로 지원 판을 고른다.
 * {@code getPackVersion(PackType)} 은 바닐라 version.json 의 pack_version(리소스 34·데이터 48)을 준다.
 */
public interface WorldVersion {

    DataVersion getDataVersion();

    String getId();

    String getName();

    int getProtocolVersion();

    java.util.Date getBuildTime();

    boolean isStable();

    /** 바닐라: 그 종류 팩의 형식 번호(리소스 34·데이터 48, 1.21.1 version.json). */
    int getPackVersion(net.minecraft.server.packs.PackType type);
}
