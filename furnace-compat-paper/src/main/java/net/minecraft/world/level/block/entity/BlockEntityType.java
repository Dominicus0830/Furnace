package net.minecraft.world.level.block.entity;

import net.minecraft.resources.ResourceLocation;

/**
 * 블록 엔티티 종류 shim(Mojang 이름, T-182). 지금은 레지스트리 값으로만 쓴다 — 청크 패킷의 블록 엔티티 항목(ClientboundLevelChunkPacketData.BlockEntityInfo)과
 * {@code BuiltInRegistries.BLOCK_ENTITY_TYPE}. 종류 상수(CHEST 등)·블록 엔티티 만들기는 아직 없다.
 */
public final class BlockEntityType<T> {

    private final ResourceLocation id;

    /**
     * 바닐라(Paper 는 공개): 이 종류의 블록 엔티티를 갖는 블록들. Furnace 는 서버 블록 표에서 처음 읽을 때 채운다(블록 레지스트리 차례 — Paper 는 종류를
     * 정의할 때 적은 차례다). 바닐라 ImmutableSet 처럼 고칠 수 없다.
     */
    public final java.util.Set<net.minecraft.world.level.block.Block> validBlocks = new ValidBlocks();

    /** 이 모듈 전용(레지스트리가 만든다). */
    public BlockEntityType(ResourceLocation id) { this.id = id; }

    private final class ValidBlocks extends java.util.AbstractSet<net.minecraft.world.level.block.Block> {
        private volatile java.util.Set<net.minecraft.world.level.block.Block> blocks;

        private java.util.Set<net.minecraft.world.level.block.Block> blocks() {
            var b = blocks;
            if (b == null) {
                var out = new java.util.LinkedHashSet<net.minecraft.world.level.block.Block>();
                String key = id.toString();
                for (net.minecraft.world.level.block.Block block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
                    if (key.equals(org.domi.furnace.compat.nms.NmsBlockStates.bridge().blockEntityType(block.defaultBlockState().furnaceStateId()))) out.add(block);
                }
                blocks = b = java.util.Collections.unmodifiableSet(out);
            }
            return b;
        }

        @Override
        public java.util.Iterator<net.minecraft.world.level.block.Block> iterator() { return blocks().iterator(); }

        @Override
        public int size() { return blocks().size(); }

        @Override
        public boolean contains(Object o) { return blocks().contains(o); }
    }

    /** 바닐라: 레지스트리 이름(없으면 null). */
    public static ResourceLocation getKey(BlockEntityType<?> type) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);
    }

    /** 이 모듈 전용: 이름("minecraft:chest"). */
    public ResourceLocation furnaceId() { return id; }

    /** 호환 모듈 전용(N2b-6): 키("minecraft:chest")의 레지스트리 값. 모르는 키면 IllegalArgumentException. */
    public static BlockEntityType<?> furnaceOf(String key) {
        BlockEntityType<?> t = net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.get(ResourceLocation.parse(key));
        if (t == null) throw new IllegalArgumentException("unknown block entity type: " + key);
        return t;
    }

    /** 바닐라 isValid: 그 블록 상태의 블록이 이 종류의 블록인가(서버 표 기준, N2b-6). */
    public boolean isValid(net.minecraft.world.level.block.state.BlockState state) {
        return id.toString().equals(org.domi.furnace.compat.nms.NmsBlockStates.bridge().blockEntityType(state.furnaceStateId()));
    }

    @Override
    public String toString() { return "BlockEntityType[" + id + "]"; }
}
