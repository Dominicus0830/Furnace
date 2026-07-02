// structure_set 하나의 파싱 결과 — set id·구조물 목록·placement 파라미터.
package org.domi.furnace.server.world.gen.structure;

import java.util.List;

public record StructureSetPlacement(
    String setId,
    List<String> structureIds,
    RandomSpreadStructurePlacement placement
) {
}
