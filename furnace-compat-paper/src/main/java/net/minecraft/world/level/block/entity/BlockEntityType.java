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

    @Override
    public String toString() { return "BlockEntityType[" + id + "]"; }
}
