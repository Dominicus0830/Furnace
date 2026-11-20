package net.minecraft.world.level.block;

import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.domi.furnace.api.world.BlockType;
import org.domi.furnace.api.world.BlockTypes;

/** 줄기 블록(Mojang 이름 shim): 호박·수박 줄기({@code AGE} 0~7, {@code MAX_AGE}). 열매에 붙은 줄기는 다른 클래스다. */
public class StemBlock extends Block {

    public static final int MAX_AGE = 7;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_7;

    /** 호환 모듈 전용 — 블록 shim 표가 만든다. */
    public StemBlock(BlockType type, BlockTypes types) { super(type, types); }
}
