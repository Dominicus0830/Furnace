package org.domi.furnace.server.world.block;

/** BlockShapes가 이웃 블록을 읽는 최소 인터페이스(월드젠 뷰·테스트용 가짜 월드가 구현). */
public interface ShapeLevel {
    int getBlock(int x, int y, int z);
}
