package net.minecraft.world.level.block;

import org.domi.furnace.api.world.BlockType;
import org.domi.furnace.api.world.BlockTypes;

/**
 * 불 블록의 부모(바닐라는 추상 클래스이고 FireBlock·SoulFireBlock 이 상속한다). 이 shim 은 불(minecraft:fire)·영혼 불(minecraft:soul_fire)에 이 객체를 주어
 * {@code instanceof BaseFireBlock} 이 바닐라와 같게 한다. 불이 퍼지는 동작은 서버 안에 있다({@link Block} 와 같다).
 */
public class BaseFireBlock extends Block {

    /** 호환 모듈 전용 — 블록 shim 표가 만든다. */
    public BaseFireBlock(BlockType type, BlockTypes types) { super(type, types); }
}
