// 플러그인이 보는 살아 있는 블록 칸 — 위치만 들고 있고, 읽을 때마다 소유 region 에서 지금 상태를 본다.
package org.domi.furnace.server.plugin;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.domi.furnace.api.world.Block;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.api.world.BlockSnapshot;

final class BlockHandle implements Block {

    private final WorldHandle world;
    private final BlockPos pos;

    BlockHandle(WorldHandle world, BlockPos pos) {
        this.world = world;
        this.pos = pos;
    }

    @Override public String world() { return world.dimension(); }

    @Override public BlockPos pos() { return pos; }

    @Override public WorldHandle worldHandle() { return world; }

    @Override
    public BlockData data() {
        return snapshot().orElseThrow(() -> new IllegalStateException("청크가 로드돼 있지 않다: " + pos)).data();
    }

    @Override public Optional<BlockSnapshot> snapshot() { return world.blockNow(pos); }

    @Override public CompletableFuture<Optional<BlockSnapshot>> snapshotAsync() { return world.blockAt(pos); }

    @Override public boolean set(BlockData data) { return world.setNow(pos, data); }

    @Override public CompletableFuture<Boolean> setAsync(BlockData data) { return world.setBlock(pos, data); }

    @Override public Block relative(int dx, int dy, int dz) { return new BlockHandle(world, pos.offset(dx, dy, dz)); }

    @Override public boolean equals(Object o) { return o instanceof BlockHandle b && b.world == world && b.pos.equals(pos); }

    @Override public int hashCode() { return pos.hashCode() * 31 + world.dimension().hashCode(); }

    @Override public String toString() { return "Block{" + world.dimension() + " " + pos + "}"; }
}
