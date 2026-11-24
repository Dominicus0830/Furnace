package net.minecraft.core.particles;

import net.minecraft.world.level.block.state.BlockState;

/** 블록 상태를 갖는 입자(Mojang 이름 shim): block·block_marker·falling_dust·dust_pillar. */
public class BlockParticleOption implements ParticleOptions {

    private final ParticleType<BlockParticleOption> type;
    private final BlockState state;

    public BlockParticleOption(ParticleType<BlockParticleOption> type, BlockState state) {
        this.type = type;
        this.state = state;
    }

    @Override
    public ParticleType<BlockParticleOption> getType() { return type; }

    public BlockState getState() { return state; }
}
