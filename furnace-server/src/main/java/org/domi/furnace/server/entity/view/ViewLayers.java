// 한 플레이어(연결)의 화면 덮어쓰기 묶음: 가상 블록·바이옴 층, 장비 덮어쓰기, 개인 월드 보더. 접속이 끝나면 함께 사라진다.
// 하나도 없으면 active 가 거짓이라 ViewRewriter 는 패킷마다 volatile 읽기 하나로 끝낸다.
package org.domi.furnace.server.entity.view;

import org.domi.furnace.server.world.WorldBorder;

public final class ViewLayers {

    public final BlockLayer blocks = new BlockLayer();
    public final EquipmentLayer equipment = new EquipmentLayer();

    private volatile WorldBorder border;
    private volatile boolean borderEnforced;
    private volatile Object borderOwner;

    public boolean active() { return blocks.active() || equipment.active() || border != null; }

    /** 개인 보더(없으면 null). */
    public WorldBorder border() { return border; }

    public boolean borderEnforced() { return borderEnforced; }

    public Object borderOwner() { return borderOwner; }

    public void border(WorldBorder b, Object owner) { this.border = b; this.borderOwner = owner; if (b == null) borderEnforced = false; }

    public void borderOwner(Object owner) { this.borderOwner = owner; }

    public void borderEnforced(boolean enforced) { this.borderEnforced = enforced; }

    /** 서버 판정(보더 밖 피해·상호작용)에 쓸 보더: 개인 보더가 있고 enforce 면 그것, 아니면 null. */
    private volatile java.util.function.ToDoubleFunction<String> scaleOf;

    /** 차원 키 → 좌표 비(PlayerViews 가 개인 보더를 만들 때 서버의 차원 표를 넘긴다). */
    public void scaleOf(java.util.function.ToDoubleFunction<String> f) { this.scaleOf = f; }

    /** 그 차원 기준의 개인 보더(바닐라 네더 보더처럼 중심만 좌표 비로 나눈다, 크기는 그대로). 공유 상태를 바꾸지 않는 복사본이라 스레드 간에 부딪히지 않는다. */
    public WorldBorder borderFor(String dimension) {
        var b = border;
        var f = scaleOf;
        if (b == null || f == null || dimension == null) return b;
        return b.withCoordinateScale(f.applyAsDouble(dimension));
    }

    /** 서버 판정용(enforce 켠 경우만) — 그 차원 기준. */
    public WorldBorder enforcedBorder(String dimension) { return borderEnforced ? borderFor(dimension) : null; }

    public WorldBorder enforcedBorder() { return borderEnforced ? border : null; }
}
