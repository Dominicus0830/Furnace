// 정의 종류 → 바닐라 블록 클래스 이식 등록. 이식된 종류만 여기 적는다(나머지는 BlockBehavior 기본값).
package org.domi.furnace.server.block.types;

public final class Registry {
    private Registry() {}

    public static void registerAll() {
        Reg.all();
    }
}
