// 바닐라 Structure 서브클래스(type)의 추상형 — findGenerationPoint + afterPlace.
package org.domi.furnace.server.world.gen.structure;

import java.util.Optional;
import java.util.function.Consumer;

public abstract class StructureType {

    /** Structure.GenerationStub: 위치(바이옴 검사용) + 지연 생성되는 조각. */
    public record Stub(int x, int y, int z, Consumer<PiecesBuilder> generator) {}

    public abstract Optional<Stub> findGenerationPoint(GenContext c);

    /** Structure.afterPlace — 기본은 아무 것도 하지 않는다. */
    public void afterPlace(PostCtx c, StructureStart start) {}

    /** 바닐라 Structure.onTopOfChunkCenter. */
    protected static Optional<Stub> onTopOfChunkCenter(GenContext c, int heightmap, Consumer<PiecesBuilder> gen) {
        int x = c.middleBlockX();
        int z = c.middleBlockZ();
        int y = c.terrain.firstOccupiedHeight(heightmap, x, z);
        return Optional.of(new Stub(x, y, z, gen));
    }
}
