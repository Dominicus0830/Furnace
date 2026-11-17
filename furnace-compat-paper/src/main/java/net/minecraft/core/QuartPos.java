package net.minecraft.core;

/**
 * 바이옴 격자(블록 4칸 = 1 쿼트) 좌표 변환(Mojang 이름 shim, 2026-11-17, 등급 A). 바닐라 1.21.1 서버 jar 를 {@code javap -c} 로 읽어 맞췄다
 * (난독화 {@code jx}: 상수 BITS=2·SIZE=4·MASK=3, 변환은 시프트 둘뿐).
 */
public final class QuartPos {

    public static final int BITS = 2;
    public static final int SIZE = 4;
    public static final int MASK = 3;
    private static final int SECTION_TO_QUARTS_BITS = 2;

    private QuartPos() {}

    public static int fromBlock(int block) { return block >> 2; }

    public static int quartLocal(int quart) { return quart & 3; }

    public static int toBlock(int quart) { return quart << 2; }

    public static int fromSection(int section) { return section << 2; }

    public static int toSection(int quart) { return quart >> 2; }
}
