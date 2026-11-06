// 바닐라 DynamicGameEventListener: 엔티티가 가진 리스너. 리스너 위치가 있는 구역에 등록하고, 엔티티 구역이 바뀌면 옮긴다.
package org.domi.furnace.server.world.gameevent;

import java.util.Optional;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Vec3;
import org.domi.furnace.server.world.ChunkColumn;

public final class DynamicGameEventListener<T extends GameEventListener> {

    private final T listener;
    /** 등록한 구역 {청크 x, 구역 y, 청크 z}(없으면 null). */
    private int[] lastSection;
    private ChunkColumn lastColumn;

    public DynamicGameEventListener(T listener) { this.listener = listener; }

    public T getListener() { return listener; }

    public void add(ChunkLevel level) { move(level); }

    public void move(ChunkLevel level) {
        Optional<Vec3> p = listener.getListenerSource().getPosition(level);
        if (p.isEmpty()) return;
        Vec3 v = p.get();
        int sx = (int) Math.floor(v.x()) >> 4, sy = (int) Math.floor(v.y()) >> 4, sz = (int) Math.floor(v.z()) >> 4;
        if (lastSection != null && lastSection[0] == sx && lastSection[1] == sy && lastSection[2] == sz) return;
        unregister();
        ChunkColumn col = level.listenerChunk(sx, sz);
        lastSection = new int[] {sx, sy, sz};
        lastColumn = col;
        if (col != null) col.listenerRegistry(sy, true).register(listener);   // ifChunkExists
    }

    public void remove(ChunkLevel level) {
        unregister();
        lastSection = null;
        lastColumn = null;
    }

    private void unregister() {
        if (lastSection == null || lastColumn == null) return;
        GameEventListenerRegistry reg = lastColumn.listenerRegistry(lastSection[1], false);
        if (reg != null) reg.unregister(listener);
    }
}
