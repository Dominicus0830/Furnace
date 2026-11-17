package net.minecraft.world.entity.player;

/** 채팅 보이기 설정(Mojang 이름 shim, 철자도 바닐라 그대로 Visiblity). 상수 순서·번호는 바닐라 1.21.1 과 같다(FULL 0, SYSTEM 1, HIDDEN 2). */
public enum ChatVisiblity {
    FULL(0, "options.chat.visibility.full"),
    SYSTEM(1, "options.chat.visibility.system"),
    HIDDEN(2, "options.chat.visibility.hidden");

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
