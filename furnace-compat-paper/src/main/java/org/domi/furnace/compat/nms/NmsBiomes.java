package org.domi.furnace.compat.nms;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;

/**
 * 바이옴 레지스트리 shim(N2b-6). 번호·값은 청크 shim 의 바이옴 표({@link NmsChunks#biomeHolders()})와 같다 — 같은 손잡이·같은 {@link Biome} 객체라
 * 청크 섹션이 주는 바이옴과 {@code ==} 로 맞고, 데이터 팩·플러그인 바이옴도 서버 번호 그대로 들어간다.
 * 서버 바이옴 목록이 바뀌면(플러그인 등록) 다음 물음에서 다시 만든다. 태그(#minecraft:is_ocean 등)는 서버의 바이옴 태그다.
 */
public final class NmsBiomes {

    private NmsBiomes() {}

    private record Cached(org.domi.furnace.api.Furnace server, java.util.List<net.minecraft.core.Holder.Reference<Biome>> holders, Registry<Biome> registry) {}

    private static volatile Cached cached;

    public static Registry<Biome> registry() {
        var server = org.domi.furnace.api.Furnace.server();
        var holders = NmsChunks.biomeHolders();
        Cached c = cached;
        if (c != null && c.server == server && c.holders.equals(holders)) return c.registry;
        synchronized (NmsBiomes.class) {
            c = cached;
            if (c != null && c.server == server && c.holders.equals(holders)) return c.registry;
            NmsMappedRegistry<Biome> r = new NmsMappedRegistry<>(Registries.BIOME);
            for (var h : holders) r.addHolder(h);
            cached = c = new Cached(server, holders, r);
            return r;
        }
    }
}
