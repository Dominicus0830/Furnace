package net.minecraft.world.level.lighting;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 빛 엔진 한 층(Mojang 이름 shim, NMS shim 5d). Furnace 의 빛 계산은 서버가 region 마다 하므로 이 클래스로 만든 엔진은 없다 — 플러그인이 부르는 정적
 * 판정({@link #hasDifferentLightProperties})만 둔다(Axiom 이 섹션에 직접 쓴 뒤 빛을 다시 계산할지 고를 때).
 */
public abstract class LightEngine<M, S> implements LayerLightEventListener {

    // Furnace 는 광원을 서버가 청크마다 들고 있어 광원 엔진 객체를 만들지 않는다.
    private LightEngine() {}

    /**
     * 바닐라: 두 상태의 빛 성질이 다른가 — 같은 객체면 거짓, 아니면 빛 가림 정도나 빛 세기가 다르거나 어느 한쪽이 빛 가림에 모양을 쓰면 참.
     * 서버 빛 엔진의 같은 판정(LightTables.hasDifferentLightProperties)과 같은 표를 읽는다.
     */
    public static boolean hasDifferentLightProperties(BlockGetter level, BlockPos pos, BlockState oldState, BlockState newState) {
        if (oldState == newState) return false;
        return newState.getLightBlock(level, pos) != oldState.getLightBlock(level, pos) || newState.getLightEmission() != oldState.getLightEmission()
                || newState.useShapeForLightOcclusion() || oldState.useShapeForLightOcclusion();
    }
}
