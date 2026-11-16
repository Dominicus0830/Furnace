package com.destroystokyo.paper.profile;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.profile.PlayerTextures;

/**
 * Paper 의 플레이어 프로필: 속성(스킨 등)을 직접 다루고, 비어 있는 UUID·이름·텍스처를 서버가 아는 값으로 채울 수 있다.
 * {@link #update()}·{@link #complete} 는 서버의 사용자 캐시를 보고, 온라인 모드면 세션 서버에도 묻는다 — 막힐 수 있으니 서버 스레드 밖에서 부르는 것이 좋다.
 */
public interface PlayerProfile extends org.bukkit.profile.PlayerProfile {

    @Override
    String getName();

    /** 이름을 바꾼다. 옛 이름을 돌려준다. */
    @Deprecated(forRemoval = true, since = "1.18.1")
    String setName(String name);

    UUID getId();

    /** UUID 를 바꾼다. 옛 UUID 를 돌려준다. */
    @Deprecated(forRemoval = true, since = "1.18.1")
    UUID setId(UUID uuid);

    @Override
    PlayerTextures getTextures();

    @Override
    void setTextures(PlayerTextures textures);

    /** 속성들(이 집합에서 지우거나 넣으면 프로필에 반영된다). */
    Set<ProfileProperty> getProperties();

    boolean hasProperty(String property);

    /** 같은 이름의 속성을 바꾸거나 넣는다(최대 16개). */
    void setProperty(ProfileProperty property);

    void setProperties(Collection<ProfileProperty> properties);

    boolean removeProperty(String property);

    default boolean removeProperty(ProfileProperty property) { return removeProperty(property.getName()); }

    default boolean removeProperties(Collection<ProfileProperty> properties) {
        boolean removed = false;
        for (ProfileProperty property : properties) {
            if (removeProperty(property)) removed = true;
        }
        return removed;
    }

    void clearProperties();

    @Override
    boolean isComplete();

    /** 사용자 캐시만 보고 채운다(네트워크 없음). 완성되면 참. */
    boolean completeFromCache();

    boolean completeFromCache(boolean onlineMode);

    boolean completeFromCache(boolean lookupUUID, boolean onlineMode);

    /** {@code complete(true)}. */
    default boolean complete() { return complete(true); }

    /** 캐시와(온라인 모드면) 세션 서버로 채운다. textures 가 거짓이면 텍스처 속성은 꼭 필요하지 않다. */
    boolean complete(boolean textures);

    boolean complete(boolean textures, boolean onlineMode);

    @Override
    CompletableFuture<PlayerProfile> update();

    default boolean hasTextures() { return hasProperty("textures"); }
}
