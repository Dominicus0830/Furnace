package com.destroystokyo.paper.profile;

import org.domi.furnace.compat.paper.ProfileValue;

/**
 * 두 CraftPlayerProfile(Bukkit 의 {@code org.bukkit.craftbukkit.profile} 것과 Paper 의 이 패키지 것)가 함께 구현하는 내부 인터페이스.
 * 머리 블록·텍스처가 어느 쪽이든 같은 방식으로 읽고 쓴다. Paper 의 같은 이름 인터페이스와 달리 authlib 형은 쓰지 않는다.
 */
public interface SharedPlayerProfile {

    java.util.UUID getUniqueId();

    String getName();

    /** 같은 이름의 첫 속성(없으면 null). */
    ProfileProperty getProperty(String propertyName);

    /** 같은 이름의 속성을 모두 지우고 property 를 넣는다(null 이면 지우기만). */
    void setProperty(String propertyName, ProfileProperty property);

    boolean removeProperty(String propertyName);

    /** 머리에 저장할 값(바닐라 ResolvableProfile 에 해당). 값 복사본이다. */
    ProfileValue buildValue();
}
