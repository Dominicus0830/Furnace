package net.minecraft.world.level.block.entity;

import net.minecraft.resources.ResourceLocation;

/**
 * 블록 엔티티 종류 shim(Mojang 이름, T-182). 지금은 레지스트리 값으로만 쓴다 — 청크 패킷의 블록 엔티티 항목(ClientboundLevelChunkPacketData.BlockEntityInfo)과
 * {@code BuiltInRegistries.BLOCK_ENTITY_TYPE}. 종류 상수(CHEST 등)·블록 엔티티 만들기는 아직 없다.
 */
public final class BlockEntityType<T> {

    private final ResourceLocation id;

    /** 이 모듈 전용(레지스트리가 만든다). */
    public BlockEntityType(ResourceLocation id) { this.id = id; }

    /** 바닐라: 레지스트리 이름(없으면 null). */
    public static ResourceLocation getKey(BlockEntityType<?> type) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);
    }

    /** 이 모듈 전용: 이름("minecraft:chest"). */
    public ResourceLocation furnaceId() { return id; }

    /** 호환 모듈 전용(N2b-6): 키("minecraft:chest")의 레지스트리 값. 모르는 키면 IllegalArgumentException. */
    public static BlockEntityType<?> furnaceOf(String key) {
        BlockEntityType<?> t = net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.get(ResourceLocation.parse(key));
        if (t == null) throw new IllegalArgumentException("모르는 블록 엔티티 종류: " + key);
        return t;
    }

    /** 바닐라 isValid: 그 블록 상태의 블록이 이 종류의 블록인가(서버 표 기준, N2b-6). */
    public boolean isValid(net.minecraft.world.level.block.state.BlockState state) {
        return id.toString().equals(org.domi.furnace.compat.nms.NmsBlockStates.bridge().blockEntityType(state.furnaceStateId()));
    }

    @Override
    public String toString() { return "BlockEntityType[" + id + "]"; }
}
