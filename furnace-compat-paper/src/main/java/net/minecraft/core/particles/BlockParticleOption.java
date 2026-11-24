package net.minecraft.core.particles;

import net.minecraft.world.level.block.state.BlockState;

/** 블록 상태를 갖는 입자(Mojang 이름 shim): block·block_marker·falling_dust·dust_pillar. */
public class BlockParticleOption implements ParticleOptions {

    private final ParticleType<BlockParticleOption> kind;
    private final BlockState block;

    public BlockParticleOption(ParticleType<BlockParticleOption> type, BlockState state) {
        this.kind = type;
        this.block = state;
    }

    @Override
    public ParticleType<BlockParticleOption> getType() { return kind; }

    public BlockState getState() { return block; }
}
