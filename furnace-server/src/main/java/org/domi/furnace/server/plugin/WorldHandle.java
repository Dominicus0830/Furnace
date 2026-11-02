// 플러그인이 보는 월드 — 서버 World 를 감싼다. 블록 접근은 모두 그 칸을 가진 region 스레드에서 실행된다.
package org.domi.furnace.server.plugin;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.domi.furnace.api.concurrent.ThreadOwner;
import org.domi.furnace.api.concurrent.ThreadOwnershipException;
import org.domi.furnace.api.sched.TaskScheduler;
import org.domi.furnace.api.world.Block;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.world.GameRules;
import org.domi.furnace.server.world.Region;
import org.domi.furnace.server.world.World;

public final class WorldHandle implements org.domi.furnace.api.world.World {

    private final PluginHost host;
    private final World world;

    WorldHandle(PluginHost host, World world) {
        this.host = host;
        this.world = world;
    }

    public World handle() { return world; }

    private ServerCore core() { return host.core(); }

    @Override
    public String name() {
        String base = core().levelData().levelName;
        return switch (world.dimension()) {
            case "minecraft:overworld" -> base;
            case "minecraft:the_nether" -> base + "_nether";
            case "minecraft:the_end" -> base + "_the_end";
            default -> world.dimension();
        };
    }

    @Override
    public String dimension() { return world.dimension(); }

    @Override
    public long seed() { return core().levelData().seed; }

    @Override
    public String gameRule(String name) {
        GameRules.Rule r = GameRules.RULES.get(name);
        return r == null ? null : GameRules.serialize(r, core().gameRules().raw(name));
    }

    @Override
    public int loadedChunkCount() { return world.loadedChunkCount(); }

    private boolean inHeight(int y) { return y >= world.dimensionType().minY() && y < world.dimensionType().maxY(); }

    private Optional<Block> read(Region r, BlockPos pos) {
        ChunkPos cp = new ChunkPos(pos.chunkX(), pos.chunkZ());
        if (!inHeight(pos.y()) || !r.chunkLoaded(cp)) return Optional.empty();
        int state = r.getBlock(pos.x(), pos.y(), pos.z());
        return Optional.of(new Block(world.dimension(), pos, ApiConvert.block(state, world.blockRegistry())));
    }

    @Override
    public CompletableFuture<Optional<Block>> blockAt(BlockPos pos) {
        CompletableFuture<Optional<Block>> f = world.submitAtOwnedChunk(new ChunkPos(pos.chunkX(), pos.chunkZ()), r -> read(r, pos));
        return RegionFuture.wrap(f != null ? f : CompletableFuture.completedFuture(Optional.empty()));
    }

    @Override
    public CompletableFuture<Boolean> setBlock(BlockPos pos, BlockData data) {
        int state;
        try {
            state = ApiConvert.stateId(data, world.blockRegistry());
        } catch (IllegalArgumentException e) {
            return RegionFuture.wrap(CompletableFuture.failedFuture(e));
        }
        CompletableFuture<Boolean> f = world.submitAtOwnedChunk(new ChunkPos(pos.chunkX(), pos.chunkZ()), r -> {
            if (!inHeight(pos.y()) || !r.chunkLoaded(new ChunkPos(pos.chunkX(), pos.chunkZ()))) return false;
            var level = r.level();
            boolean ok = level.setBlock(new Pos(pos.x(), pos.y(), pos.z()), state, 11);
            level.broadcastChanges();
            return ok;
        });
        return RegionFuture.wrap(f != null ? f : CompletableFuture.completedFuture(false));
    }

    @Override
    public Optional<Block> blockNow(BlockPos pos) {
        ThreadOwner owner = (ThreadOwner) world.chunkScheduler(pos.chunkX(), pos.chunkZ());
        if (!owner.isOwnerThread()) {
            throw new ThreadOwnershipException("blockNow(" + pos + ") 는 그 칸을 가진 region 스레드에서만 부를 수 있다 (현재 " + Thread.currentThread().getName() + ") — blockAt 을 쓴다");
        }
        Region r = world.regionizer().ownerOf(pos.chunkX(), pos.chunkZ());
        return r == null ? Optional.empty() : read(r, pos);
    }

    @Override
    public TaskScheduler scheduler(BlockPos pos) { return world.chunkScheduler(pos.chunkX(), pos.chunkZ()); }
}
