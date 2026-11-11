package net.minecraft.world.level.block.state.properties;

import net.minecraft.util.StringRepresentable;

/** 블록 상태 속성 값(Mojang 이름 shim, N0). 상수 이름·순서·직렬화 이름은 바닐라 1.21.1 바이트코드의 정적 초기화와 같다. toString 은 바닐라처럼 덮어쓰지 않는다(상수 이름). */
public enum NoteBlockInstrument implements StringRepresentable {
    HARP("harp"),
    BASEDRUM("basedrum"),
    SNARE("snare"),
    HAT("hat"),
    BASS("bass"),
    FLUTE("flute"),
    BELL("bell"),
    GUITAR("guitar"),
    CHIME("chime"),
    XYLOPHONE("xylophone"),
    IRON_XYLOPHONE("iron_xylophone"),
    COW_BELL("cow_bell"),
    DIDGERIDOO("didgeridoo"),
    BIT("bit"),
    BANJO("banjo"),
    PLING("pling"),
    ZOMBIE("zombie"),
    SKELETON("skeleton"),
    CREEPER("creeper"),
    DRAGON("dragon"),
    WITHER_SKELETON("wither_skeleton"),
    PIGLIN("piglin"),
    CUSTOM_HEAD("custom_head");

    private final String name;

    NoteBlockInstrument(String name) { this.name = name; }

    @Override
    public String getSerializedName() { return name; }
}
