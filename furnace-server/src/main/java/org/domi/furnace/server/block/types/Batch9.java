// 이식 묶음 9 의 블록 종류 등록(묶음마다 파일을 나눠 병렬 이식이 서로 부딪히지 않게).
package org.domi.furnace.server.block.types;

final class Batch9 {
    private Batch9() {}

    static void register() {
        Reg.type("minecraft:chest", ChestBlock::new);
    }
}
