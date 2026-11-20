package org.bukkit.craftbukkit.map;

import java.util.List;

/**
 * CraftBukkit 지도 보기 shim(Paper 이름). 지도 데이터({@code MapItemSavedData#mapView})가 들고 있다. 지금은 "그리기 도구 목록"만 있다 —
 * Bukkit {@code MapView} 인터페이스·플러그인 그리기 도구 등록은 없다(없는 멤버 — 호출 위치에서 실패). 목록은 바닐라 그리기 표시 하나로 고정이다.
 */
public final class CraftMapView {

    private static final List<CraftMapRenderer> RENDERERS = List.of(new CraftMapRenderer());

    private final int id;

    /** 호환 모듈 전용. */
    public CraftMapView(int id) { this.id = id; }

    /** 이 보기의 지도 번호(Furnace). */
    public int furnaceId() { return id; }

    /** 그리기 도구 목록 — 바닐라 {@link CraftMapRenderer} 하나(수정 불가). Paper 는 {@code List<MapRenderer>}. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public List getRenderers() { return RENDERERS; }
}
