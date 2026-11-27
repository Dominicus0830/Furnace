package net.minecraft.world.entity.player;

/** 채팅 보이기 설정(Mojang 이름 shim, 철자도 바닐라 그대로 Visiblity). 상수(인자 포함)와 순서는 명세가 정한다(FULL 0, SYSTEM 1, HIDDEN 2 — 바닐라 1.21.1). */
enum ChatVisiblity {

    private final int id;
    private final String key;

    ChatVisiblity(int id, String key) {
        this.id = id;
        this.key = key;
    }

    public int getId() { return id; }

    public String getKey() { return key; }

    public static ChatVisiblity byId(int id) { return values()[Math.floorMod(id, values().length)]; }
}
