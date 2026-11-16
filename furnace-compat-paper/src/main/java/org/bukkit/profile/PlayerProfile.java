package org.bukkit.profile;

import java.util.concurrent.CompletableFuture;
import org.bukkit.configuration.serialization.ConfigurationSerializable;

/**
 * 플레이어 프로필(UUID·이름·텍스처). 머리 블록·아이템의 소유자 표시에 쓴다. 값 복사본이라 고쳐도 서버의 어느 프로필도 바뀌지 않는다.
 */
public interface PlayerProfile extends Cloneable, ConfigurationSerializable {

    /** UUID(모르면 null). */
    java.util.UUID getUniqueId();

    /** 이름(모르면 null). */
    String getName();

    /** 텍스처 보기(프로필의 "textures" 속성에 바로 비친다). */
    PlayerTextures getTextures();

    /** 텍스처를 복사해 온다. null 이면 지운다. */
    void setTextures(PlayerTextures textures);

    /** UUID·이름·텍스처가 모두 있으면 참. */
    boolean isComplete();

    /** 이름·UUID 로 서버가 아는 값(캐시)과 세션 서버에서 채운 새 프로필. 이 프로필은 바뀌지 않는다. */
    CompletableFuture<? extends PlayerProfile> update();

    PlayerProfile clone();
}
