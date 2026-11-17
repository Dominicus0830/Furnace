package net.minecraft.world.level;

/**
 * 레벨의 구조물 관리자(Mojang 이름 shim, T-211 2단계) — {@code ServerLevel#structureManager()}. 값은 그 월드의 구조물 시작·참조 표를 가리키는 손잡이다:
 * {@code StructureStart#placeInChunk} 가 받은 관리자가 그 월드 것인지 확인하는 데 쓴다. 구조물 찾기(getStructureAt·startsForStructure)는 아직 없다.
 */
public class StructureManager {

    private final org.domi.furnace.api.world.World world;

    /** 호환 모듈 전용. */
    public StructureManager(org.domi.furnace.api.world.World world) { this.world = world; }

    /** 이 관리자를 가진 월드(호환 모듈 전용). */
    public final org.domi.furnace.api.world.World furnaceWorld() { return world; }
}
