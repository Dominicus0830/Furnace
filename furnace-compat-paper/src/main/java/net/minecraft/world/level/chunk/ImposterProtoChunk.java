package net.minecraft.world.level.chunk;

/**
 * 바닐라 ImposterProtoChunk(Mojang 이름 shim): 다 만든 청크를 생성 중 청크처럼 감싸는 덮개. 바닐라는 ProtoChunk 를 잇지만 shim 은 생성 중 청크가
 * 없어 {@link ChunkAccess} 를 잇는다. Furnace 는 이것을 만들어 주지 않는다 — 서버가 돌려주는 청크는 늘 {@link LevelChunk} 라
 * 플러그인의 {@code instanceof ImposterProtoChunk} 는 늘 거짓이다. 플러그인이 직접 만들어 감싸는 것은 된다(보기만).
 */
public class ImposterProtoChunk extends ChunkAccess {

    private final LevelChunk wrapped;
    private final boolean allowWrites;

    public ImposterProtoChunk(LevelChunk wrapped, boolean allowWrites) {
        super(wrapped.furnaceWorld(), wrapped.getPos().x, wrapped.getPos().z);
        this.wrapped = wrapped;
        this.allowWrites = allowWrites;
    }

    public LevelChunk getWrapped() { return wrapped; }

    public boolean furnaceAllowWrites() { return allowWrites; }

    @Override
    public net.minecraft.world.level.block.state.BlockState getBlockState(net.minecraft.core.BlockPos pos) { return wrapped.getBlockState(pos); }

    @Override
    public net.minecraft.world.level.material.FluidState getFluidState(net.minecraft.core.BlockPos pos) { return wrapped.getFluidState(pos); }
}
