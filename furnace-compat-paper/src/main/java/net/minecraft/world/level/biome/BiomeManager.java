package net.minecraft.world.level.biome;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;

/**
 * 바닐라 바이옴 확대 shim(Mojang 이름, T-221): 블록 칸의 바이옴을 시드 지터로 고른 이웃 바이옴 칸에서 읽는다. 서버 레벨의 것만 있다 —
 * 그 월드 서버가 답한다(그 칸을 가진 region 스레드에서만, 청크가 없으면 IllegalStateException).
 */
public final class BiomeManager {

    private final org.domi.furnace.api.world.World world;

    /** 호환 모듈 전용 — 서버 레벨이 만든다. */
    public BiomeManager(org.domi.furnace.api.world.World world) { this.world = java.util.Objects.requireNonNull(world, "world"); }

    /** 바닐라 BiomeManager#getBiome. */
    public Holder<Biome> getBiome(BlockPos pos) {
        String key = org.domi.furnace.api.Furnace.server().internals().features().biomeAtNow(world, pos.getX(), pos.getY(), pos.getZ());
        String k = key.indexOf(':') < 0 ? "minecraft:" + key : key;
        Holder<Biome> h = org.domi.furnace.compat.nms.NmsChunks.biome(k);
        if (h == null) {
            org.domi.furnace.compat.nms.NmsChunks.biomeHolders();   // 데이터 팩 바이옴이 늘었으면 표를 다시 만든다
            h = org.domi.furnace.compat.nms.NmsChunks.biome(k);
        }
        if (h == null) throw new IllegalStateException("unknown biome from the server: " + k);
        return h;
    }
}
