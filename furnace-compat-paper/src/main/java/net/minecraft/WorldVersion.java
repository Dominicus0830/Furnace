package net.minecraft;

import net.minecraft.world.level.storage.DataVersion;

/**
 * 게임 판 정보(Mojang 이름 shim, N2b-6). {@link SharedConstants#getCurrentVersion()} 이 준다. FAWE 어댑터가 데이터 판(3955)으로 지원 판을 고른다.
 * 바닐라의 {@code getPackVersion(PackType)} 은 PackType shim 이 없어 두지 않았다(없는 멤버 — 호출 위치에서 실패).
 */
public interface WorldVersion {

    DataVersion getDataVersion();

    String getId();

    String getName();

    int getProtocolVersion();

    java.util.Date getBuildTime();

    boolean isStable();
}
