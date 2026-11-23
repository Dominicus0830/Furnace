package net.minecraft.world;

import net.minecraft.network.chat.Component;

/** 이름을 가진 것(Mojang 이름 shim): 컨테이너·인벤토리의 화면 이름. 바닐라와 같이 표시 이름은 사용자 지정 이름이 있으면 그것이다. */
public interface Nameable {

    Component getName();

    default boolean hasCustomName() { return getCustomName() != null; }

    default Component getDisplayName() { return getName(); }

    default Component getCustomName() { return null; }
}
