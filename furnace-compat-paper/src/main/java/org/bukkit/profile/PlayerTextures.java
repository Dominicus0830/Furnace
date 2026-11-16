package org.bukkit.profile;

import java.net.URL;

/**
 * 플레이어 프로필의 "textures" 속성(스킨·망토)을 URL 로 다루는 보기. 값은 프로필의 속성에 바로 비친다.
 * 설정하는 URL 은 {@code https://textures.minecraft.net/texture/…} 꼴이어야 한다.
 */
public interface PlayerTextures {

    /** 스킨 팔 모양: 기본(굵은 팔)과 얇은 팔. */
    enum SkinModel {
        CLASSIC, SLIM
    }

    /** 스킨도 망토도 없으면 참. */
    boolean isEmpty();

    /** 스킨·망토와 "textures" 속성을 모두 지운다. */
    void clear();

    /** 스킨 URL(없으면 null). */
    URL getSkin();

    /** 스킨을 CLASSIC 모양으로 정한다. null 이면 지운다. */
    void setSkin(URL skinUrl);

    /** 스킨과 팔 모양을 정한다(null 모양은 CLASSIC). */
    void setSkin(URL skinUrl, SkinModel skinModel);

    /** 스킨 팔 모양(스킨이 없으면 CLASSIC). */
    SkinModel getSkinModel();

    /** 망토 URL(없으면 null). */
    URL getCape();

    void setCape(URL capeUrl);

    /** Mojang 이 textures 값을 만든 시각(밀리초). 직접 고쳤거나 없으면 0. */
    long getTimestamp();

    /** Mojang 서명이 붙어 있고 맞으면 참. 직접 고친 뒤에는 거짓. */
    boolean isSigned();
}
