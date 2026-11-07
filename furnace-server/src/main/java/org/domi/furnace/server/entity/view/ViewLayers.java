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
    public WorldBorder enforcedBorder() { return borderEnforced ? border : null; }
}
