package org.bukkit.block.data;

import org.bukkit.Material;

/**
 * 블록 종류 + 속성(상태). 사본 값이다. 종류별 하위 인터페이스(Directional·Waterlogged 등)는 아직 없고, 문자열 상태로 다룬다.
 */
public interface BlockData extends Cloneable {

    Material getMaterial();

    /** 모든 속성을 적은 문자열(minecraft:oak_stairs[facing=north,half=bottom,…], 속성 이름순). */
    String getAsString();

    /** hideUnspecified 가 참이면 문자열에서 만들 때 적지 않은 속성은 뺀다(세상에서 읽은 것은 모두 적혀 있다). */
    String getAsString(boolean hideUnspecified);

    /** other 의 적힌 속성이 모두 이 상태와 같고 종류가 같으면 참. */
    boolean matches(BlockData other);

    BlockData clone();
}
