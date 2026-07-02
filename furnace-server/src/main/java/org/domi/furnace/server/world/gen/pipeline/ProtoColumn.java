package org.domi.furnace.server.world.gen.pipeline;

import org.domi.furnace.server.world.ChunkColumn;

final class ProtoColumn {
    final int cx;
    final int cz;
    volatile GenStatus status = GenStatus.EMPTY;
    ChunkColumn col;
    int[] biomeIds;

    ProtoColumn(int cx, int cz) {
        this.cx = cx;
        this.cz = cz;
    }
}
