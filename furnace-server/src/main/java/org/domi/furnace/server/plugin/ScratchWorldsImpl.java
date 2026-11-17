// 임시 월드 다리(T-203) 서버 구현 — 월드 관리자의 createScratch 위에 손잡이를 씌운다. 손잡이는 PluginHost 의 차원 표에 넣지 않는다(목록에 안 보인다).
package org.domi.furnace.server.plugin;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.domi.furnace.api.internal.ScratchWorlds;
import org.domi.furnace.server.world.World;

final class ScratchWorldsImpl implements ScratchWorlds {

    private final Supplier<PluginHost> host;
    @Override
    public String generatorKind(org.domi.furnace.api.world.World world) {
        if (!(world instanceof WorldHandle wh)) throw new IllegalArgumentException("not a world of this server: " + world);
        var src = wh.handle().worldSource();
        if (src instanceof org.domi.furnace.server.world.gen.AnvilWorldSource a) src = a.base();
        if (src instanceof org.domi.furnace.server.world.gen.ScratchWorldSource t) src = t.base();
        if (src instanceof org.domi.furnace.server.world.gen.NoiseWorldSource n) return n.isFlat() ? "flat" : "noise";
        if (src instanceof org.domi.furnace.server.world.gen.SuperflatWorldSource) return "flat";
        if (src instanceof org.domi.furnace.server.world.gen.DebugWorldSource) return "debug";
        return "other";
    }

    /** 살아 있는 임시 월드의 손잡이(서버 월드 → 손잡이). 닫으면 뺀다. */
    private final ConcurrentHashMap<World, WorldHandle> open = new ConcurrentHashMap<>();

    ScratchWorldsImpl(Supplier<PluginHost> host) { this.host = host; }

    @Override
    public org.domi.furnace.api.world.World create(org.domi.furnace.api.world.World original, long seed, String name,
                                                   org.domi.furnace.api.worldgen.BiomeProvider biomes) {
        if (!(original instanceof WorldHandle o)) throw new IllegalArgumentException("not a world of this server: " + original);
        PluginHost h = host.get();
        var m = h.core().worldManager();
        if (m == null) throw new UnsupportedOperationException("multiple worlds are not enabled on this server");
        World w = m.createScratch(o.handle(), seed, name == null ? o.name() : name, biomes);
        WorldHandle wh = new WorldHandle(h, w);
        open.put(w, wh);
        return wh;
    }

    @Override
    public String defaultGameType() {
        return host.get().core().levelData().gameType.name().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public boolean isScratch(org.domi.furnace.api.world.World world) {
        return world instanceof WorldHandle wh && wh.handle().isScratch();
    }

    @Override
    public void close(org.domi.furnace.api.world.World scratch) {
        if (!isScratch(scratch)) throw new IllegalArgumentException("not a scratch world: " + scratch);
        World w = ((WorldHandle) scratch).handle();
        open.remove(w);
        var m = host.get().core().worldManager();
        if (m != null) m.closeScratch(w);
    }

    /** 살아 있는 임시 월드의 손잡이(아니면 null) — Internals#wrapWorld. */
    WorldHandle handleOf(World w) { return open.get(w); }
}
