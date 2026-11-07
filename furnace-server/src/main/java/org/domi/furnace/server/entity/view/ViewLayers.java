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

    /** 개인 보더의 중심 나눗셈을 그 차원의 좌표 비에 맞춘다(바닐라 네더 보더와 같게: 중심만 비로 나누고 크기는 그대로). */
    public void syncBorderScale(String dimension) {
        var b = border;
        var f = scaleOf;
        if (b != null && f != null && dimension != null) b.setCoordinateScale(f.applyAsDouble(dimension));
    }

    public WorldBorder enforcedBorder() { return borderEnforced ? border : null; }
}
