package org.domi.furnace.server.world.gen.pipeline;

import java.util.List;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.structure.StructureStart;

final class ProtoColumn {
    final int cx;
    final int cz;
    volatile GenStatus status = GenStatus.EMPTY;
    ChunkColumn col;
    int[] biomeIds;
    List<StructureStart> starts = List.of();

    ProtoColumn(int cx, int cz) {
        this.cx = cx;
        this.cz = cz;
    }
}
