// 변장: 엔티티를 (어떤 플레이어에게) 다른 종류로 보이게 하는 모습. 불변 값 — with* 는 새 값을 돌려준다.
// 서버는 진짜 엔티티를 그대로 두고(AI·충돌·피해·저장은 진짜 종류), 보는 사람에게 가는 스폰·메타데이터만 바꾼다.
// 메타데이터는 두 종류에 공통인 칸만 넘긴다(모든 엔티티: 상태 비트·이름·자세 …, 둘 다 살아 있으면 체력·손 상태 …, 둘 다 몹이면 몹 비트).
// 같은 종류면 전부 넘긴다. 변장 종류에만 있는 칸은 withMetadata 로 정한다(칸 번호·값 종류는 마인크래프트 버전마다 다르다 — 틀리면 클라이언트가 끊긴다).
package org.domi.furnace.api.entity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.domi.furnace.api.packet.clientbound.ClientboundSetEntityDataPacket.Unsupported;
import org.domi.furnace.api.packet.clientbound.ClientboundSetEntityDataPacket.Value;
import org.domi.furnace.api.text.Text;

public final class Disguise {

    private static final Disguise NONE = new Disguise(null, null, List.of(), true, 0, Map.of());

    private final String entityType;
    private final String profileName;
    private final List<ProfileProperty> skin;
    private final boolean nameTagVisible;
    private final int spawnData;
    private final Map<Integer, Value> metadata;

    private Disguise(String entityType, String profileName, List<ProfileProperty> skin, boolean nameTagVisible, int spawnData, Map<Integer, Value> metadata) {
        this.entityType = entityType;
        this.profileName = profileName;
        this.skin = skin;
        this.nameTagVisible = nameTagVisible;
        this.spawnData = spawnData;
        this.metadata = metadata;
    }

    /** 다른 엔티티 종류로("minecraft:creeper", 이름공간을 빼면 minecraft). 플레이어 모습은 {@link #player}. */
    public static Disguise as(String entityType) {
        Objects.requireNonNull(entityType, "entityType");
        String t = entityType.indexOf(':') < 0 ? "minecraft:" + entityType : entityType;
        if (t.equals("minecraft:player")) throw new IllegalArgumentException("플레이어 모습은 Disguise.player 로 만든다");
        return new Disguise(t, null, List.of(), true, 0, Map.of());
    }

    /** 플레이어 모습(프로필 이름 1~16자, 스킨 "textures" 속성 — 비면 기본 스킨). 탭 목록에는 올리지 않는다. */
    public static Disguise player(String profileName, List<ProfileProperty> skin) {
        if (profileName == null || profileName.isEmpty() || profileName.length() > 16) throw new IllegalArgumentException("프로필 이름은 1~16자: " + profileName);
        return new Disguise("minecraft:player", profileName, skin == null ? List.of() : List.copyOf(skin), true, 0, Map.of());
    }

    /** 진짜 모습 — 플레이어별 예외에 쓴다(전역 변장이 있어도 그 사람에게는 진짜로 보인다). */
    public static Disguise none() { return NONE; }

    public boolean isNone() { return this == NONE; }

    /** 엔티티 종류 이름(none 이면 null). */
    public String entityType() { return entityType; }

    /** 플레이어 모습의 프로필 이름(다른 종류면 null). */
    public String profileName() { return profileName; }

    public List<ProfileProperty> skin() { return skin; }

    public boolean isNameTagVisible() { return nameTagVisible; }

    /** add_entity 의 data 칸(떨어지는 블록의 블록 상태 번호, 액자·그림의 방향 등). 기본 0. */
    public int spawnData() { return spawnData; }

    /** 덮어쓰는 메타데이터(칸 번호 → 값, 읽기 전용). */
    public Map<Integer, Value> metadata() { return metadata; }

    private void requireReal() {
        if (isNone()) throw new IllegalStateException("none() 은 고칠 수 없다");
    }

    /** 이 칸은 진짜 엔티티 값 대신 늘 이 값을 보낸다. */
    public Disguise withMetadata(int index, Value value) {
        requireReal();
        if (index < 0 || index > 254) throw new IllegalArgumentException("칸 번호 범위 밖: " + index);
        Objects.requireNonNull(value, "value");
        if (value instanceof Unsupported) throw new IllegalArgumentException("읽지 못한 값 종류는 정할 수 없다");
        Map<Integer, Value> m = new LinkedHashMap<>(metadata);
        m.put(index, value);
        return new Disguise(entityType, profileName, skin, nameTagVisible, spawnData, java.util.Collections.unmodifiableMap(m));
    }

    /** 머리 위 이름(모든 엔티티 칸 2). null 이면 이름 없음으로 덮는다. */
    public Disguise withCustomName(Text name) { return withMetadata(2, Value.ofOptionalText(name)); }

    /** 이름이 늘 보이는지(모든 엔티티 칸 3). */
    public Disguise withCustomNameVisible(boolean visible) { return withMetadata(3, Value.ofBoolean(visible)); }

    public Disguise withSpawnData(int data) {
        requireReal();
        return new Disguise(entityType, profileName, skin, nameTagVisible, data, metadata);
    }

    /** 플레이어 모습의 이름표(기본 보임). 숨기면 보는 사람에게만 숨김 팀을 보낸다. */
    public Disguise withNameTagVisible(boolean visible) {
        requireReal();
        if (profileName == null) throw new IllegalStateException("이름표 설정은 플레이어 모습에만 있다");
        return new Disguise(entityType, profileName, skin, visible, spawnData, metadata);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Disguise d && Objects.equals(entityType, d.entityType) && Objects.equals(profileName, d.profileName)
                && skin.equals(d.skin) && nameTagVisible == d.nameTagVisible && spawnData == d.spawnData && metadata.equals(d.metadata);
    }

    @Override
    public int hashCode() { return Objects.hash(entityType, profileName, skin, nameTagVisible, spawnData, metadata); }

    @Override
    public String toString() {
        if (isNone()) return "Disguise{none}";
        return "Disguise{" + entityType + (profileName != null ? " " + profileName : "") + (metadata.isEmpty() ? "" : " meta=" + metadata.keySet()) + "}";
    }
}
