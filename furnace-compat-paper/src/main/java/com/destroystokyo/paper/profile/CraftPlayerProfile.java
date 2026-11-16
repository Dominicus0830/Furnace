package com.destroystokyo.paper.profile;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.profile.CraftPlayerTextures;
import org.bukkit.craftbukkit.profile.CraftProfileProperty;
import org.bukkit.profile.PlayerTextures;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.api.entity.ProfileData;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.compat.paper.ProfileValue;

/**
 * Paper 의 프로필 구현({@code Bukkit.createProfile}·머리 {@code getPlayerProfile} 가 준다). UUID·이름은 비어 있을 수 있고(null),
 * 속성은 순서가 있는 목록이다. 서버의 프로필 객체와 이어져 있지 않다 — 접속 중인 플레이어로 만들어도 속성은 복사본이다
 * (Paper 는 그 플레이어의 프로필 속성 객체를 그대로 비춘다).
 */
@SerializableAs("PlayerProfile")
public class CraftPlayerProfile implements PlayerProfile, SharedPlayerProfile {

    /** 프로필 조회가 서버 스레드를 막지 않게 따로 돌리는 스레드(Paper: Util.PROFILE_EXECUTOR). */
    private static final java.util.concurrent.Executor PROFILE_EXECUTOR = org.domi.furnace.compat.paper.ProfileExecutor.get();

    private UUID id;       // null: 비어 있음
    private String name;   // null: 비어 있음("" 는 비어 있지 않은 빈 이름)
    private final List<ProfileProperty> properties = new ArrayList<>();
    private final PropertySet propertySet = new PropertySet();

    public CraftPlayerProfile(CraftPlayer player) {
        var handle = player.furnaceHandle();
        this.id = handle.uniqueId();
        this.name = handle.name();
        for (var p : handle.profileProperties()) properties.add(new ProfileProperty(p.name(), p.value(), p.signature()));
    }

    public CraftPlayerProfile(UUID id, String name) {
        if (name != null && name.length() > 16) throw new IllegalArgumentException("Name cannot be longer than 16 characters");
        this.id = id;
        this.name = name;
    }

    /** 저장된 프로필 값으로(머리 블록 getPlayerProfile). */
    public CraftPlayerProfile(ProfileValue value) {
        this(value.id(), value.name());
        properties.addAll(value.properties());
    }

    /** 서버가 찾은 프로필의 복사본(Paper asBukkitCopy). */
    public static PlayerProfile asBukkitCopy(ProfileData data) {
        CraftPlayerProfile profile = new CraftPlayerProfile(data.id(), data.name());
        profile.properties.addAll(ProfileValue.fromApi(data.properties()));
        return profile;
    }

    // ------------------------------------------------------------------ 값

    @Override
    public UUID getId() { return id; }

    @Override
    public UUID getUniqueId() { return id; }

    @Override
    @Deprecated(forRemoval = true)
    public UUID setId(UUID uuid) {
        UUID previous = id;
        id = uuid;
        return previous;
    }

    @Override
    public String getName() { return name; }

    @Override
    @Deprecated(forRemoval = true)
    public String setName(String name) {
        if (name != null && name.length() > 16) throw new IllegalArgumentException("Name cannot be longer than 16 characters");
        String previous = this.name == null ? "" : this.name;   // Paper 는 이전 프로필의 이름(비어 있으면 "")을 돌려준다
        this.name = name;
        return previous;
    }

    @Override
    public boolean isComplete() { return id != null && name != null && !name.isBlank(); }

    // ------------------------------------------------------------------ 속성

    @Override
    public Set<ProfileProperty> getProperties() { return propertySet; }

    @Override
    public boolean hasProperty(String property) { return properties.stream().anyMatch(p -> p.getName().equals(property)); }

    @Override
    public void setProperty(ProfileProperty property) {
        properties.removeIf(p -> p.getName().equals(property.getName()));
        if (properties.size() >= 16) throw new IllegalArgumentException("Cannot add more than 16 properties to a profile");
        properties.add(property);
    }

    @Override
    public void setProperties(Collection<ProfileProperty> properties) { properties.forEach(this::setProperty); }

    @Override
    public void clearProperties() { properties.clear(); }

    @Override
    public boolean removeProperty(String property) { return properties.removeIf(p -> p.getName().equals(property)); }

    @Override
    public ProfileProperty getProperty(String propertyName) {
        for (ProfileProperty p : properties) if (p.getName().equals(propertyName)) return p;
        return null;
    }

    @Override
    public void setProperty(String propertyName, ProfileProperty property) {
        if (property != null) setProperty(new ProfileProperty(propertyName, property.getValue(), property.getSignature()));
        else removeProperty(propertyName);
    }

    @Override
    public CraftPlayerTextures getTextures() { return new CraftPlayerTextures(this); }

    @Override
    public void setTextures(PlayerTextures textures) {
        if (textures == null) {
            removeProperty("textures");
        } else {
            CraftPlayerTextures craft = new CraftPlayerTextures(this);
            craft.copyFrom(textures);
            craft.rebuildPropertyIfDirty();
        }
    }

    @Override
    public ProfileValue buildValue() { return new ProfileValue(id, name, properties); }

    // ------------------------------------------------------------------ 채우기

    @Override
    public CompletableFuture<PlayerProfile> update() {
        return CompletableFuture.supplyAsync(() -> {
            CraftPlayerProfile clone = clone();
            clone.complete(true);
            return clone;
        }, PROFILE_EXECUTOR);
    }

    @Override
    public boolean completeFromCache() { return completeFromCache(false, Compat.furnace().profileLookups()); }

    @Override
    public boolean completeFromCache(boolean onlineMode) { return completeFromCache(false, onlineMode); }

    @Override
    public boolean completeFromCache(boolean lookupUUID, boolean onlineMode) {
        Furnace server = Compat.furnace();
        String currentName = name == null ? "" : name;
        if (id == null && !currentName.isEmpty()) {
            ProfileData found;
            if (onlineMode) {
                found = lookupUUID ? server.fetchProfileByName(currentName).join().orElse(null) : cachedOnly(server, currentName);   // 캐시에 없으면 인증 서버에 묻는다(막힐 수 있다)
            } else {
                // 이름만 있고 프로필이 없으니 오프라인 모드 UUID 로 만든다
                found = new ProfileData(java.util.UUID.nameUUIDFromBytes(("OfflinePlayer:" + currentName).getBytes(java.nio.charset.StandardCharsets.UTF_8)), currentName);
            }
            if (found != null) {
                mergeInto(found.properties());
                id = found.id();
                name = found.name();
            }
        }
        if ((currentName().isEmpty() || !hasTextures()) && id != null) {
            ProfileData found = server.profileById(id).orElse(null);
            if (found != null) {
                if (currentName().isEmpty()) {
                    mergeInto(found.properties());
                    name = found.name();
                } else {
                    // 있는 값을 우선하되 속성은 찾은 것으로 덮는다(Paper copyProfileProperties(found → this))
                    for (var p : found.properties()) setProperty(new ProfileProperty(p.name(), p.value(), p.signature()));
                }
            }
        }
        return isComplete();
    }

    private static ProfileData cachedOnly(Furnace server, String name) {
        var cached = server.offlinePlayerCached(name);
        return cached == null || cached.name() == null ? null : new ProfileData(cached.uniqueId(), cached.name());
    }

    private String currentName() { return name == null ? "" : name; }

    /** 찾은 프로필로 바꾸되 이 프로필이 가진 속성이 같은 이름의 것을 이긴다(Paper: 옛 값이 더 새롭다고 본다). */
    private void mergeInto(List<org.domi.furnace.api.entity.ProfileProperty> foundProperties) {
        List<ProfileProperty> mine = new ArrayList<>(properties);
        properties.clear();
        for (var p : foundProperties) properties.add(new ProfileProperty(p.name(), p.value(), p.signature()));
        for (ProfileProperty p : mine) {
            properties.removeIf(x -> x.getName().equals(p.getName()));
            properties.add(p);
        }
    }

    @Override
    public boolean complete(boolean textures) { return complete(textures, Compat.furnace().profileLookups()); }

    /**
     * 온라인(Mojang 조회)이면 이 메서드는 부른 스레드를 막는다 — 이름 조회와 세션 서버 조회가 각각 최대 10초라 최악에 약 20초.
     * Paper 와 같다(region·서버 스레드에서는 부르지 말고 {@link #update()} 를 쓴다). 기본 플래그 {@code onlineMode} 는 서버가
     * 온라인 모드이거나 BungeeCord·Velocity 포워딩일 때 참(Paper usesAuthentication).
     */
    @Override
    public boolean complete(boolean textures, boolean onlineMode) {
        if (isComplete() && (!textures || hasTextures())) return true;   // 이미 다 있으면 묻지 않는다
        boolean completeFromCache = completeFromCache(true, onlineMode);
        if (onlineMode && (!completeFromCache || (textures && !hasTextures()))) {
            if (id != null) {
                // 세션 서버가 준 속성이 이 프로필의 속성을 통째로 바꾼다(이름·UUID 는 그대로)
                Compat.furnace().fetchProfile(id).join().ifPresent(found -> {
                    properties.clear();
                    for (var p : found.properties()) properties.add(new ProfileProperty(p.name(), p.value(), p.signature()));
                });
            }
        }
        return isComplete() && (!onlineMode || !textures || hasTextures());
    }

    // ------------------------------------------------------------------ 복사·같음·직렬화

    @Override
    public CraftPlayerProfile clone() {
        CraftPlayerProfile clone = new CraftPlayerProfile(id, name);
        clone.properties.addAll(properties);
        return clone;
    }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        if (id != null) map.put("uniqueId", id.toString());
        if (name != null) map.put("name", name);
        if (!properties.isEmpty()) {
            List<Object> data = new ArrayList<>();
            for (ProfileProperty p : properties) data.add(CraftProfileProperty.serialize(p));
            map.put("properties", data);
        }
        return map;
    }

    public static CraftPlayerProfile deserialize(Map<String, Object> map) {
        UUID uniqueId = uuidOf(map);
        Object n = map.get("name");
        if (n != null && !(n instanceof String)) throw new IllegalArgumentException("Expected a string value for key 'name', got " + n.getClass().getName());
        CraftPlayerProfile profile = new CraftPlayerProfile(uniqueId, (String) n);
        if (map.containsKey("properties")) {
            for (Object data : (List<?>) map.get("properties")) {
                if (!(data instanceof Map<?, ?> m)) throw new IllegalArgumentException("Property data (" + data + ") is not a valid Map");
                profile.properties.add(CraftProfileProperty.deserialize(m));
            }
        }
        return profile;
    }

    private static UUID uuidOf(Map<String, Object> map) {
        Object v = map.get("uniqueId");
        if (v == null) return null;
        if (!(v instanceof String s)) throw new IllegalArgumentException("Expected a string value for key 'uniqueId', got " + v.getClass().getName());
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid uuid for key 'uniqueId': " + s, e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CraftPlayerProfile that = (CraftPlayerProfile) o;
        return Objects.equals(id, that.id) && Objects.equals(name, that.name)
                && properties.size() == that.properties.size() && new java.util.HashSet<>(properties).equals(new java.util.HashSet<>(that.properties));
    }

    @Override
    public int hashCode() { return Objects.hash(id, name, new java.util.HashSet<>(properties)); }

    @Override
    public String toString() {
        return "CraftPlayerProfile [uniqueId=" + id + ", name=" + name + ", properties=" + CraftProfileProperty.toString(properties) + "]";
    }

    /** 속성 목록의 집합 보기(지우기·넣기가 프로필에 반영된다). */
    private final class PropertySet extends AbstractSet<ProfileProperty> {
        @Override
        public Iterator<ProfileProperty> iterator() { return properties.iterator(); }

        @Override
        public int size() { return properties.size(); }

        @Override
        public boolean add(ProfileProperty property) {
            setProperty(property);
            return true;
        }

        @Override
        public boolean addAll(Collection<? extends ProfileProperty> c) {
            c.forEach(CraftPlayerProfile.this::setProperty);
            return true;
        }

        @Override
        public boolean contains(Object o) { return o instanceof ProfileProperty p && hasProperty(p.getName()); }
    }
}
