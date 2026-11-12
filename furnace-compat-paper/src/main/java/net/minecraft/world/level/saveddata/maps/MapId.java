package net.minecraft.world.level.saveddata.maps;

/** 지도 번호(Mojang 이름 shim, easy-nms E4c-2). 바닐라 1.21.1 과 같은 record 다. 코덱 필드는 두지 않았다. */
public record MapId(int id) {

    /** 저장 이름("map_" + 번호). */
    public String key() { return "map_" + id; }
}
