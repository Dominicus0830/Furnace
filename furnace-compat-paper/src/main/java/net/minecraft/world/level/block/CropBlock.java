package net.minecraft.world.level.block;

import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.domi.furnace.api.world.BlockType;
import org.domi.furnace.api.world.BlockTypes;

/**
 * 작물 블록(Mojang 이름 shim): 밀·당근·감자·비트·횃불꽃 작물의 클래스. 공통 {@code AGE}(0~7)와 {@code MAX_AGE} 상수는 바닐라와 같다
 * (비트·횃불꽃 작물은 자기 나이 속성을 쓰는 점도 바닐라와 같다 — 그 속성은 블록 상태가 가진다). 성장·부식 규칙은 서버 쪽에 있다.
 */
public class CropBlock extends Block {

    public static final int MAX_AGE = 7;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_7;

    /** 호환 모듈 전용 — 블록 shim 표가 만든다. */
    public CropBlock(BlockType type, BlockTypes types) { super(type, types); }
}
