package net.minecraft.world.item.component;

import com.destroystokyo.paper.profile.CraftPlayerProfile;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;

/**
 * 머리 아이템·머리 블록이 들고 다니는 프로필(Mojang 이름 shim, 효과·프로필·속성 묶음). 바닐라 1.21.1 레코드와 같은 칸(이름·UUID·속성·게임 프로필)이다.
 * 이름이나 UUID 중 하나만 있는 "풀기 전" 상태에서 {@link #resolve()} 가 스킨(속성)을 채운 새 값을 비동기로 준다. 조회는 Bukkit {@code PlayerProfile#update()} 와
 * 같은 조회 풀·캐시를 쓴다(못 찾으면 바닐라처럼 UUID 없는 이름만의 프로필, 이름 없는 UUID 만의 프로필).
 * {@code CODEC}·{@code FULL_CODEC}·{@code STREAM_CODEC} 은 두지 않았다(부르면 없는 멤버).
 */
public record ResolvableProfile(Optional<String> name, Optional<UUID> id, PropertyMap properties, GameProfile gameProfile) {

    /** 한 사람의 게임 프로필로(이름·UUID·속성 모두 그 프로필의 것). */
    public ResolvableProfile(GameProfile profile) {
        this(Optional.of(profile.getName()), Optional.of(profile.getId()), profile.getProperties(), profile);
    }

    public ResolvableProfile(Optional<String> name, Optional<UUID> id, PropertyMap properties) {
        this(name, id, properties, createProfile(name, id, properties));
    }

    private static GameProfile createProfile(Optional<String> name, Optional<UUID> id, PropertyMap properties) {
        GameProfile profile = new GameProfile(id.orElse(Util.NIL_UUID), name.orElse(""));
        for (Property p : properties.values()) profile.getProperties().put(p.name(), p);
        return profile;
    }

    /** 바닐라: 속성(스킨)이 있거나, UUID 와 이름이 함께 있거나 함께 없으면 더 풀 것이 없다. */
    public boolean isResolved() { return !properties.isEmpty() || id.isPresent() == name.isPresent(); }

    /**
     * 바닐라: 이미 풀렸으면 자기 자신, UUID 가 있으면 UUID 로, 아니면 이름으로 찾는다. 찾으면 그 프로필, 못 찾으면 (UUID, 이름 또는 빈 문자열) /
     * (UUID 없음 → nil UUID, 이름) 로 만든다.
     */
    public CompletableFuture<ResolvableProfile> resolve() {
        if (isResolved()) return CompletableFuture.completedFuture(this);
        if (id.isPresent()) {
            UUID uuid = id.get();
            return fetch(uuid, null).thenApply(found -> new ResolvableProfile(found.orElseGet(() -> new GameProfile(uuid, name.orElse("")))));
        }
        String lookup = name.orElseThrow();
        return fetch(null, lookup).thenApply(found -> new ResolvableProfile(found.orElseGet(() -> new GameProfile(Util.NIL_UUID, lookup))));
    }

    /** 조회 풀로 찾는다. 찾지 못했거나(그대로) 조회가 실패하면 비어 있다. */
    private static CompletableFuture<Optional<GameProfile>> fetch(UUID id, String name) {
        CraftPlayerProfile query = new CraftPlayerProfile(id, name);
        var before = query.buildValue();
        CompletableFuture<PlayerProfile> updated;
        try {
            updated = query.update();
        } catch (RuntimeException e) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return updated.handle((found, error) -> {
            if (error != null || !(found instanceof CraftPlayerProfile p)) return Optional.<GameProfile>empty();
            var value = p.buildValue();
            if (value.equals(before) || value.id() == null || value.name() == null) return Optional.<GameProfile>empty();
            GameProfile out = new GameProfile(value.id(), value.name());
            for (var prop : value.properties()) out.getProperties().put(prop.getName(), new Property(prop.getName(), prop.getValue(), prop.getSignature()));
            return Optional.of(out);
        });
    }
}
