package org.bukkit.craftbukkit.profile;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.destroystokyo.paper.profile.SharedPlayerProfile;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.api.entity.ProfileData;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.compat.paper.ProfileValue;

/**
 * Bukkit(Spigot)의 프로필 구현({@code Server.createPlayerProfile}·머리 {@code getOwnerProfile}). UUID 와 이름 가운데 하나는 꼭 있어야 하고
 * (머리에서 읽어 온 것은 둘 다 비어 있어도 된다), 값은 만든 뒤 바뀌지 않는다 — 속성은 {@link #getTextures()} 로만 고친다.
 * Paper 는 이 클래스 대신 {@code com.destroystokyo.paper.profile.CraftPlayerProfile} 을 {@code Bukkit.createProfile} 로 준다.
 */
@SerializableAs("PlayerProfile")
public final class CraftPlayerProfile implements PlayerProfile, SharedPlayerProfile {

    private static final UUID NIL_UUID = new UUID(0L, 0L);

    /** 머리에 두려는 프로필은 이름이 있거나, UUID 와 텍스처가 둘 다 있어야 한다(CraftBukkit 주석의 규칙). */
    public static ProfileValue validateSkullProfile(ProfileValue value) {
        boolean valid = value.name() != null
                || (value.id() != null && value.properties().stream().anyMatch(p -> p.getName().equals(CraftPlayerTextures.PROPERTY_NAME)));
        if (!valid) throw new IllegalArgumentException("The skull profile is missing a name or textures!");
        return value;
    }

    private final UUID uniqueId;
    private final String name;
    private final List<ProfileProperty> properties = new ArrayList<>();
    private final CraftPlayerTextures textures = new CraftPlayerTextures(this);

    private CraftPlayerProfile(UUID uniqueId, String name, boolean applyPreconditions) {
        if (applyPreconditions && uniqueId == null && (name == null || name.isBlank())) throw new IllegalArgumentException("uniqueId is null or name is blank");
        this.uniqueId = uniqueId;
        this.name = name;
    }

    public CraftPlayerProfile(UUID uniqueId, String name) { this(uniqueId, name, true); }

    /** 저장된 프로필 값으로(머리 블록 getOwnerProfile) — 조건 검사 없이. */
    public CraftPlayerProfile(ProfileValue value) {
        this(value.id(), value.name(), false);
        properties.addAll(value.properties());
    }

    private CraftPlayerProfile(CraftPlayerProfile other) {
        this(other.uniqueId, other.name);
        properties.addAll(other.properties);
        textures.copyFrom(other.textures);
    }

    @Override
    public UUID getUniqueId() { return Objects.equals(uniqueId, NIL_UUID) ? null : uniqueId; }

    @Override
    public String getName() { return name == null || name.isBlank() ? null : name; }

    @Override
    public ProfileProperty getProperty(String propertyName) {
        for (ProfileProperty p : properties) if (p.getName().equals(propertyName)) return p;
        return null;
    }

    @Override
    public void setProperty(String propertyName, ProfileProperty property) {
        removeProperty(propertyName);
        if (property != null) properties.add(property);
    }

    @Override
    public boolean removeProperty(String propertyName) { return properties.removeIf(p -> p.getName().equals(propertyName)); }

    @Override
    public CraftPlayerTextures getTextures() { return textures; }

    @Override
    public void setTextures(PlayerTextures textures) {
        if (textures == null) this.textures.clear();
        else this.textures.copyFrom(textures);
    }

    @Override
    public boolean isComplete() { return getUniqueId() != null && getName() != null && !textures.isEmpty(); }

    /** 이름으로 UUID 를, UUID 로 스킨 같은 속성을 서버가 아는 값(사용자 캐시)과 세션 서버에서 채운 새 프로필. */
    @Override
    public CompletableFuture<PlayerProfile> update() {
        return CompletableFuture.supplyAsync(this::updated, UPDATE_EXECUTOR);
    }

    private static final java.util.concurrent.Executor UPDATE_EXECUTOR = java.util.concurrent.Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "Profile Lookup Executor");
        t.setDaemon(true);
        return t;
    });

    private CraftPlayerProfile updated() {
        Furnace server = Compat.furnace();
        textures.rebuildPropertyIfDirty();
        ProfileValue current = buildValue();
        UUID id = current.id() == null ? NIL_UUID : current.id();
        String currentName = current.name() == null ? "" : current.name();
        ProfileValue result = new ProfileValue(id, currentName, current.properties());
        // 모르는 UUID 는 이름으로 찾는다
        if (id.equals(NIL_UUID) && !currentName.isEmpty()) {
            ProfileData found = server.fetchProfileByName(currentName).join().orElse(null);
            if (found != null) result = new ProfileValue(found.id(), found.name(), toProps(found));
        }
        // 속성(텍스처)은 세션 서버에서 받는다(인증 모드가 아니면 fetchProfile 이 비어 있게 돌려준다)
        if (!result.id().equals(NIL_UUID)) {
            try {
                ProfileData fetched = server.fetchProfile(result.id()).join().orElse(null);
                if (fetched != null) result = new ProfileValue(fetched.id(), fetched.name(), toProps(fetched));
            } catch (RuntimeException ignored) {
                // 못 받으면 있는 값 그대로
            }
        }
        return new CraftPlayerProfile(result);
    }

    private static List<ProfileProperty> toProps(ProfileData d) {
        return ProfileValue.fromApi(d.properties());
    }

    /** 머리에 저장할 값(텍스처를 고쳤으면 속성을 먼저 새로 만든다). 값 복사본이다. */
    @Override
    public ProfileValue buildValue() {
        textures.rebuildPropertyIfDirty();
        return new ProfileValue(uniqueId, name, properties);
    }

    @Override
    public CraftPlayerProfile clone() { return new CraftPlayerProfile(this); }

    @Override
    public String toString() {
        textures.rebuildPropertyIfDirty();
        return "CraftPlayerProfile [uniqueId=" + uniqueId + ", name=" + name + ", properties=" + CraftProfileProperty.toString(properties) + "]";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof CraftPlayerProfile other)) return false;
        if (!Objects.equals(uniqueId, other.uniqueId) || !Objects.equals(name, other.name)) return false;
        textures.rebuildPropertyIfDirty();
        other.textures.rebuildPropertyIfDirty();
        // 속성 순서도 같아야 같다(저장 꼴에서도 순서가 의미를 가진다)
        if (properties.size() != other.properties.size()) return false;
        for (int i = 0; i < properties.size(); i++) if (!CraftProfileProperty.equals(properties.get(i), other.properties.get(i))) return false;
        return true;
    }

    @Override
    public int hashCode() {
        textures.rebuildPropertyIfDirty();
        int result = 1;
        result = 31 * result + Objects.hashCode(uniqueId);
        result = 31 * result + Objects.hashCode(name);
        int props = 1;
        for (ProfileProperty p : properties) props = 31 * props + CraftProfileProperty.hashCode(p);
        return 31 * result + props;
    }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        if (uniqueId != null) map.put("uniqueId", uniqueId.toString());
        if (name != null) map.put("name", getName());
        textures.rebuildPropertyIfDirty();
        if (!properties.isEmpty()) {
            List<Object> data = new ArrayList<>();
            for (ProfileProperty p : properties) data.add(CraftProfileProperty.serialize(p));
            map.put("properties", data);
        }
        return map;
    }

    public static CraftPlayerProfile deserialize(Map<String, Object> map) {
        UUID uniqueId = CraftProfileProperty.uuid(map, "uniqueId", true);
        String name = CraftProfileProperty.string(map, "name", true);
        CraftPlayerProfile profile = new CraftPlayerProfile(uniqueId, name);   // UUID 와 이름이 둘 다 없으면 여기서 IllegalArgumentException
        if (map.containsKey("properties")) {
            for (Object data : (List<?>) map.get("properties")) {
                if (!(data instanceof Map<?, ?> m)) throw new IllegalArgumentException("Property data (" + data + ") is not a valid Map");
                profile.properties.add(CraftProfileProperty.deserialize(m));
            }
        }
        return profile;
    }
}
