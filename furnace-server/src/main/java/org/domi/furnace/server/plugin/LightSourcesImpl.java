// 가짜 광원 API 구현: 월드마다 VirtualLights 표에 주인별 밝기를 두고, 그 칸을 가진 region 스레드에서 빛 엔진에 검사를 건다.
package org.domi.furnace.server.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.domi.furnace.api.light.LightLevels;
import org.domi.furnace.api.light.LightSources;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.server.light.ChunkLight;
import org.domi.furnace.server.light.LightEngine;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.world.World;

public final class LightSourcesImpl implements LightSources {

    private final PluginHost host;

    LightSourcesImpl(PluginHost host) { this.host = host; }

    private static World server(org.domi.furnace.api.world.World w) {
        if (w instanceof WorldHandle h) return h.handle();
        throw new IllegalArgumentException("이 서버의 월드가 아니다: " + w);
    }

    /** 주인 키는 플러그인 객체 자체(핫 리로드로 새 객체가 되면 옛 몫은 옛 객체가 꺼질 때 지워진다). */
    private static Object ownerKey(FurnacePlugin owner) {
        if (owner == null) throw new NullPointerException("owner");
        return owner;
    }

    @Override
    public CompletableFuture<Boolean> set(FurnacePlugin owner, org.domi.furnace.api.world.World world, BlockPos pos, int level) {
        if (level < 0 || level > 15) throw new IllegalArgumentException("빛 밝기는 0..15: " + level);
        Object key = ownerKey(owner);
        World w = server(world);
        ChunkPos cp = new ChunkPos(pos.chunkX(), pos.chunkZ());
        if (rejected(owner)) return RegionFuture.wrap(CompletableFuture.completedFuture(false));
        CompletableFuture<Boolean> f = w.submitAtOwnedChunk(cp, r -> {
            if (!r.chunkLoaded(cp) || level > 0 && rejected(owner)) return false;
            var lvl = r.level();
            boolean changed = lvl.setVirtualLight(key, pos.x(), pos.y(), pos.z(), level);
            if (changed) lvl.broadcastChanges();
            return changed;
        });
        return RegionFuture.wrap(f != null ? f : CompletableFuture.completedFuture(false));
    }

    @Override
    public int level(org.domi.furnace.api.world.World world, BlockPos pos) {
        return server(world).virtualLights().emission(pos.x(), pos.y(), pos.z());
    }

    @Override
    public int levelOf(FurnacePlugin owner, org.domi.furnace.api.world.World world, BlockPos pos) {
        return server(world).virtualLights().ownLevel(ownerKey(owner), pos.x(), pos.y(), pos.z());
    }

    @Override
    public List<BlockPos> sources(FurnacePlugin owner, org.domi.furnace.api.world.World world) {
        List<BlockPos> out = new ArrayList<>();
        for (long p : server(world).virtualLights().sourcesOf(ownerKey(owner))) out.add(new BlockPos(LightEngine.px(p), LightEngine.py(p), LightEngine.pz(p)));
        return out;
    }

    @Override
    public CompletableFuture<Void> removeAll(FurnacePlugin owner) {
        Object key = ownerKey(owner);
        if (!owner.isEnabled()) cleared.add(key);   // 꺼진 뒤 늦게 도착한 set 은 받지 않는다
        return removeAllOf(key);
    }

    /** 꺼지며 정리한 주인(다시 켜지면 isEnabled 로 풀린다). 같음 비교 = 객체 자체. */
    private final java.util.Set<Object> cleared = java.util.Collections.synchronizedSet(java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>()));

    private boolean rejected(FurnacePlugin owner) {
        if (owner.isEnabled()) {
            cleared.remove(owner);
            return false;
        }
        return cleared.contains(owner);
    }

    /**
     * 그 주인 몫을 모든 월드에서 지운다. 로드된 칸은 소유 스레드에서 빛을 다시 계산한다. 로드되지 않은 칸은 RETIRED 로 넘겨 두고
     * 그 청크가 다시 올라올 때 지운다(이웃에 번진 빛까지 정리하려면 그 청크가 있어야 한다).
     */
    private CompletableFuture<Void> removeAllOf(Object key) {
        List<CompletableFuture<?>> all = new ArrayList<>();
        for (World w : host.core().worlds()) {
            var vl = w.virtualLights();
            for (long p : vl.sourcesOf(key)) {
                int x = LightEngine.px(p), y = LightEngine.py(p), z = LightEngine.pz(p);
                ChunkPos cp = new ChunkPos(x >> 4, z >> 4);
                CompletableFuture<Boolean> f;
                try {
                    f = w.submitAtOwnedChunk(cp, r -> {
                        if (!r.chunkLoaded(cp)) return vl.retire(key, x, y, z);
                        var lvl = r.level();
                        boolean changed = lvl.setVirtualLight(key, x, y, z, 0);
                        if (changed) lvl.broadcastChanges();
                        return changed;
                    });
                } catch (RuntimeException e) {
                    f = null;
                }
                if (f == null) {
                    vl.retire(key, x, y, z);   // 주인 region 이 없다 = 로드되지 않은 칸
                } else {
                    all.add(f.exceptionally(t -> vl.retire(key, x, y, z)));
                }
            }
        }
        return RegionFuture.wrap(CompletableFuture.allOf(all.toArray(new CompletableFuture[0])));
    }

    @Override
    public CompletableFuture<Optional<LightLevels>> lightAt(org.domi.furnace.api.world.World world, BlockPos pos) {
        World w = server(world);
        ChunkPos cp = new ChunkPos(pos.chunkX(), pos.chunkZ());
        CompletableFuture<Optional<LightLevels>> f = w.submitAtOwnedChunk(cp, r -> {
            if (!r.chunkLoaded(cp) || pos.y() < ChunkLight.MIN_Y || pos.y() >= ChunkLight.MAX_Y) return Optional.<LightLevels>empty();
            if (!r.level().hasLight(pos.chunkX(), pos.chunkZ())) return Optional.<LightLevels>empty();
            var le = r.level().lightEngine();
            return Optional.of(new LightLevels(le.blockLight(pos.x(), pos.y(), pos.z()), le.skyLight(pos.x(), pos.y(), pos.z())));
        });
        return RegionFuture.wrap(f != null ? f : CompletableFuture.completedFuture(Optional.empty()));
    }
}
