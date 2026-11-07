// 한 엔티티가 어떤 플레이어에게 "다른 것으로" 보이는 모습(변장). 서버 내부 표현 — 플러그인 층이 API Disguise 에서 만든다.
// 메타데이터 덮어쓰기·가짜 프로필 패킷은 버전 변환 계층(패킷 API 코덱)이 만든 것을 함수로 받아, 이 층은 와이어 형식을 모른다.
package org.domi.furnace.server.entity.view;

import java.util.Arrays;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.IntFunction;
import org.domi.furnace.server.net.protocol.Packet;

public final class Appearance {

    /** 플레이어별 예외: 전역 변장이 있어도 이 플레이어에게는 진짜 모습을 보인다. */
    public static final Appearance REAL = new Appearance(-1, null, 0, new int[0], null, null, null);

    /**
     * 가짜 플레이어 프로필(변장 종류가 minecraft:player 일 때). infoAdd 는 프로필 uuid → 탭에 올리지 않는 player_info_update.
     * nameTagVisible 이 false 면 숨김 팀으로 이름표를 가린다.
     */
    public record Profile(String name, Function<UUID, Packet> infoAdd, boolean nameTagVisible) {}

    private final int typeId;
    private final String typeName;
    private final int kind;
    private final int spawnData;
    private final int[] overridden;
    private final IntFunction<Packet> metadata;
    private final Profile profile;
    private final Object source;

    /**
     * @param overridden 덮어쓰는 칸 번호(진짜 엔티티의 같은 칸 값은 보내지 않는다)
     * @param metadata   엔티티 번호 → 덮어쓰는 칸만 담은 set_entity_data(덮어쓰기가 없으면 null)
     * @param source     API 쪽 원래 값(조회용)
     */
    public Appearance(int typeId, String typeName, int spawnData, int[] overridden, IntFunction<Packet> metadata, Profile profile, Object source) {
        this.typeId = typeId;
        this.typeName = typeName;
        this.kind = typeName == null ? EntityKinds.NON_LIVING : EntityKinds.kindOf(typeName);
        this.spawnData = spawnData;
        int[] o = overridden.clone();
        Arrays.sort(o);
        this.overridden = o;
        this.metadata = metadata;
        this.profile = profile;
        this.source = source;
        if (typeName != null && (kind == EntityKinds.PLAYER) != (profile != null)) {
            throw new IllegalArgumentException("플레이어 변장에는 프로필이 있어야 하고, 다른 종류에는 없어야 한다");
        }
    }

    public boolean isReal() { return this == REAL; }

    public int typeId() { return typeId; }

    public String typeName() { return typeName; }

    public int kind() { return kind; }

    public int spawnData() { return spawnData; }

    public boolean overrides(int index) { return overridden.length > 0 && Arrays.binarySearch(overridden, index) >= 0; }

    public IntFunction<Packet> metadata() { return metadata; }

    public Profile profile() { return profile; }

    public Object source() { return source; }

    @Override
    public String toString() { return isReal() ? "Appearance{REAL}" : "Appearance{" + typeName + "}"; }
}
