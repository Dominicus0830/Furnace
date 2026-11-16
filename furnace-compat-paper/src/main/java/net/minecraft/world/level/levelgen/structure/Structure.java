package net.minecraft.world.level.levelgen.structure;

/**
 * 구조물(Mojang 이름 shim, N2b-6). 레지스트리에 있는 구조물을 서로 구별하고 종류 이름을 읽는 용도다 —
 * 생성({@code generate}·{@code findGenerationPoint})·설정({@code biomes()}·{@code step()})은 없다(부르면 UnsupportedNmsCall).
 */
public abstract class Structure {

    private final String typeId;

    /** 호환 모듈 전용 — 구조물 종류 id(예: minecraft:jigsaw). */
    protected Structure(String typeId) { this.typeId = typeId; }

    /** Furnace: 이 구조물의 종류 id(JSON 의 type). */
    public final String furnaceTypeId() { return typeId; }

    @Override
    public String toString() { return "Structure[" + typeId + "]"; }
}
