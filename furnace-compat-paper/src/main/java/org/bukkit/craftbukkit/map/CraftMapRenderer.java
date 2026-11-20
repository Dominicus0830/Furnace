package org.bukkit.craftbukkit.map;

/**
 * CraftBukkit 이 지도마다 처음 두는 바닐라 그리기 표시(Paper 의 같은 이름 클래스). 플러그인이 이 클래스 이름으로 "바닐라 지도인가"를 가른다 —
 * Furnace 지도는 플러그인 그리기 도구를 받지 않으므로 {@link CraftMapView#getRenderers()} 는 늘 이것 하나뿐이다.
 */
public final class CraftMapRenderer {

    CraftMapRenderer() {}
}
