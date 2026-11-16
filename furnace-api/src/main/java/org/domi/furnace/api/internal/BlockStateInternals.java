// 블록 상태 속성 다리(N2b-6). 호환 계층의 NMS BlockState shim 이 바닐라 BlockStateBase 의 캐시값(빛 막는 정도, 모양, 마찰, 저항 등)을 읽는 길이다.
// 값은 서버가 바닐라 1.21.1 에서 뽑아 둔 표 그대로다. 상태 번호는 BlockTypes.stateId 와 같다. 불안정한 내부 다리다(호환 계층 전용).
package org.domi.furnace.api.internal;

/** 상태 번호 단위 블록 속성 표. 모든 값이 고정 표라서 아무 스레드에서 읽을 수 있다. */
public interface BlockStateInternals {

    /** {@link Table#flags} 비트. */
    int CAN_OCCLUDE = 1, SOLID_RENDER = 2, REPLACEABLE = 4, BLOCKS_MOTION = 8, SIGNAL_SOURCE = 16, HAS_BLOCK_ENTITY = 32,
            /** 바닐라 hasAnalogOutputSignal(비교기가 읽는 블록, T-204). */
            ANALOG_OUTPUT = 64;

    /**
     * 상태 번호를 인덱스로 하는 표다(배열 길이는 상태 수).
     * pushReaction 은 바닐라 PushReaction 서수다(NORMAL, DESTROY, BLOCK, IGNORE, PUSH_ONLY).
     * mapColorCols 는 바닐라 MapColor 번호별 RGB 다(번호가 인덱스).
     * destroySpeed·explosionResistance·friction 은 상태가 속한 블록의 값이다.
     * mapColor 는 상태별 RGB 다(바닐라 properties.mapColor.apply(state)).
     */
    record Table(byte[] lightBlock, byte[] lightEmission, byte[] pushReaction, short[] flags, float[] destroySpeed,
                 float[] explosionResistance, float[] friction, int[] mapColor, int[] mapColorCols) {}

    /** 전체 상태 표(처음 한 번 만들어 둔다). */
    Table table();

    /** 그 상태의 바닐라 getShape(빈 세계, 원점) 상자 목록이다. 상자마다 minX,minY,minZ,maxX,maxY,maxZ 를 담는다. 빈 모양이거나 모르는 번호면 길이 0. */
    double[] outlineBoxes(int stateId);

    /** 그 상태의 블록이 바닐라 EntityBlock 이면 블록 엔티티 종류(예 "minecraft:chest"), 아니면 null. 움직이는 피스톤은 종류는 있으나 newBlockEntity 가 null 이다. */
    String blockEntityType(int stateId);

    /**
     * 바닐라 {@code EntityBlock.newBlockEntity(BlockPos.ZERO, state)} 로 갓 만든 블록 엔티티의 saveWithId 꼴이다(종류별 필드와 "id", x·y·z 는 없음).
     * 블록 엔티티를 만들지 않는 블록(EntityBlock 이 아님, 움직이는 피스톤)이면 null. 새 객체. 아무 스레드(월드에 닿지 않는다).
     */
    org.domi.furnace.api.nbt.NbtCompound defaultBlockEntityNbt(int stateId);
}
