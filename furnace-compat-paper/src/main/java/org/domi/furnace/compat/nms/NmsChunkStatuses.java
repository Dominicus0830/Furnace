package org.domi.furnace.compat.nms;

import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** 청크 생성 단계 레지스트리 shim: {@link ChunkStatus#getStatusList()} 순서 그대로, 기본값 minecraft:empty(바닐라 DefaultedMappedRegistry). */
public final class NmsChunkStatuses {

    private NmsChunkStatuses() {}

    private static final NmsDefaultedRegistry<ChunkStatus> REGISTRY = build();

    public static DefaultedRegistry<ChunkStatus> registry() { return REGISTRY; }

    private static NmsDefaultedRegistry<ChunkStatus> build() {
        NmsDefaultedRegistry<ChunkStatus> r = new NmsDefaultedRegistry<>(Registries.CHUNK_STATUS, ResourceLocation.withDefaultNamespace("empty"));
        for (ChunkStatus s : ChunkStatus.getStatusList()) r.add(ResourceLocation.parse(s.getName()), s);
        return r;
    }
}
