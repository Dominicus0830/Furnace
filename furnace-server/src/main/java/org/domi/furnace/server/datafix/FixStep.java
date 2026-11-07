// 데이터 픽서 한 단계: 바닐라 DataFixers 의 스키마 하나(버전·하위 버전)에 걸린 고침들 중 구조물 템플릿에 닿는 것.
// 각 손잡이는 해당 종류의 compound 를 받아 고친 compound(같은 것이어도 된다)를 돌려준다. null 이면 그 종류는 건드리지 않는다.
package org.domi.furnace.server.datafix;

import java.util.function.UnaryOperator;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

final class FixStep {

    /** DataFixUtils.makeKey: 버전 * 10 + 하위 버전. 파일 DataVersion v 는 키 v*10 까지 끝난 것으로 본다. */
    final int key;
    final String name;
    UnaryOperator<NbtCompound> blockState;
    UnaryOperator<NbtCompound> blockEntity;
    UnaryOperator<NbtCompound> entity;
    UnaryOperator<NbtCompound> item;

    FixStep(int version, int sub, String name) {
        this.key = version * 10 + sub;
        this.name = name;
    }

    FixStep blockState(UnaryOperator<NbtCompound> f) { blockState = chain(blockState, f); return this; }
    FixStep blockEntity(UnaryOperator<NbtCompound> f) { blockEntity = chain(blockEntity, f); return this; }
    FixStep entity(UnaryOperator<NbtCompound> f) { entity = chain(entity, f); return this; }
    FixStep item(UnaryOperator<NbtCompound> f) { item = chain(item, f); return this; }

    /** 같은 스키마에 고침이 여럿이면 바닐라 addFixer 순서대로 잇는다. */
    private static UnaryOperator<NbtCompound> chain(UnaryOperator<NbtCompound> a, UnaryOperator<NbtCompound> b) {
        if (a == null) return b;
        return c -> b.apply(a.apply(c));
    }

    @Override
    public String toString() { return (key / 10) + (key % 10 == 0 ? "" : "_" + key % 10) + " " + name; }
}
