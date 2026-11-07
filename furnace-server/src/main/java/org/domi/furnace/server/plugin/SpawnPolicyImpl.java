// World.spawnPolicy() 의 구현 — 서버 World 의 WorldSpawnPolicy 를 API 타입으로 감싼다. 필터는 주인 플러그인이 꺼지면 스폰 루프가 스스로 뗀다.
package org.domi.furnace.server.plugin;

import java.util.UUID;
import org.domi.furnace.api.event.EventSubscription;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.world.SpawnCandidate;
import org.domi.furnace.api.world.SpawnFilter;
import org.domi.furnace.api.world.SpawnPolicy;
import org.domi.furnace.api.worldgen.SpawnCategory;
import org.domi.furnace.server.entity.spawn.MobCategory;
import org.domi.furnace.server.entity.spawn.SpawnPolicyState;
import org.domi.furnace.server.entity.spawn.WorldSpawnPolicy;

final class SpawnPolicyImpl implements SpawnPolicy {

    private final WorldSpawnPolicy policy;

    SpawnPolicyImpl(WorldSpawnPolicy policy) { this.policy = policy; }

    private static MobCategory server(SpawnCategory c) {
        if (c == null) throw new NullPointerException("category");
        return MobCategory.byKey(c.key());
    }

    private static MobCategory spawning(SpawnCategory c) {
        MobCategory m = server(c);
        if (m == MobCategory.MISC) throw new IllegalArgumentException("자연 스폰이 없는 종류: " + c);
        return m;
    }

    private static SpawnCategory api(MobCategory c) {
        for (SpawnCategory s : SpawnCategory.values()) if (s.key().equals(c.key())) return s;
        return SpawnCategory.MISC;
    }

    @Override
    public int vanillaCap(SpawnCategory category) { return spawning(category).max(); }

    @Override
    public int categoryCap(SpawnCategory category) { return policy.state().cap(spawning(category)); }

    @Override
    public void setCategoryCap(SpawnCategory category, int cap) { policy.setCap(spawning(category), cap); }

    @Override
    public void resetCategoryCap(SpawnCategory category) { policy.resetCap(spawning(category)); }

    @Override
    public int playerCap(SpawnCategory category) { return policy.state().playerCap(spawning(category)); }

    @Override
    public void setPlayerCap(SpawnCategory category, int cap) { policy.setPlayerCap(spawning(category), cap); }

    @Override
    public void resetPlayerCap(SpawnCategory category) { policy.resetPlayerCap(spawning(category)); }

    @Override
    public int playerCap(UUID player, SpawnCategory category) {
        if (player == null) throw new NullPointerException("player");
        return policy.state().playerCap(player, spawning(category));
    }

    @Override
    public void setPlayerCap(UUID player, SpawnCategory category, int cap) { policy.setPlayerCap(player, spawning(category), cap); }

    @Override
    public void resetPlayerCap(UUID player, SpawnCategory category) { policy.resetPlayerCap(player, spawning(category)); }

    @Override
    public EventSubscription addFilter(FurnacePlugin plugin, SpawnFilter filter) {
        if (plugin == null) throw new NullPointerException("plugin");
        if (filter == null) throw new NullPointerException("filter");
        SpawnPolicyState.FilterFn fn = (cat, type, dim, x, y, z, biome, nearest) ->
                filter.allow(new SpawnCandidate(type, api(cat), dim, x, y, z, biome, nearest));
        Runnable remove = policy.addFilter(plugin, plugin::isEnabled, fn);
        return remove::run;
    }

    @Override
    public int filterCount() { return policy.filterCount(); }
}
