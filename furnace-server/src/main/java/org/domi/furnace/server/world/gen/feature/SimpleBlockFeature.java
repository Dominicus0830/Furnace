// simple_block feature — provider가 고른 state를 생존 가능한 지면 위에 1칸 식재
package org.domi.furnace.server.world.gen.feature;

import java.util.Set;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class SimpleBlockFeature implements ConfiguredFeature {

    private final StateProvider provider;
    private final Set<Integer> validGround;
    // mushroom(brown/red)류에서만 사용 — null이면 기존 동작(validGround만으로 판정).
    private final Set<Integer> mushroomGrowGround;
    // 바닐라 BlockState.canSurvive 포팅(블록별 규칙) — null이면 위의 단순 validGround 판정(테스트 호환 경로).
    private final BlockSurvival survival;

    public SimpleBlockFeature(StateProvider provider, Set<Integer> validGround) {
        this(provider, validGround, null);
    }

    /**
     * vanilla MushroomBlock.canSurvive 근사 — mushroomGrowGround(mycelium/podzol/nylium)이면 항상 생존,
     * 그 외 지면(validGround)은 하늘이 열려있지 않을 때만 생존한다(광량 상시 15면 실패).
     */
    public SimpleBlockFeature(StateProvider provider, Set<Integer> validGround, Set<Integer> mushroomGrowGround) {
        this.provider = provider;
        this.validGround = Set.copyOf(validGround);
        this.mushroomGrowGround = mushroomGrowGround == null ? null : Set.copyOf(mushroomGrowGround);
        this.survival = null;
    }

    /** 바닐라 SimpleBlockFeature.place와 동일: canSurvive → (2칸 식물이면 위 칸이 비었는지 확인 후 아래·위 순서로 설치) → 단일 setBlock. */
    public SimpleBlockFeature(StateProvider provider, BlockSurvival survival) {
        this.provider = provider;
        this.validGround = Set.of();
        this.mushroomGrowGround = null;
        this.survival = survival;
    }

    /** slice1 호환 — 상수 state. 기존 테스트·호출부 0수정 유지. */
    public SimpleBlockFeature(int stateId, Set<Integer> validGround) {
        this(new SimpleStateProvider(stateId), validGround, null);
    }

    @Override
    public boolean place(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
        // 바닐라 SimpleBlockFeature.place: getState(random,pos)를 먼저 호출(provider별 random 소비) → 생존 판정 → 식재.
        // simple_state_provider는 0소비라 slice1과 동일. noise_threshold provider만 소비순서가 결정적.
        int stateId = provider.getState(random, x, y, z);
        if (survival != null) {
            if (!survival.canSurvive(view, stateId, x, y, z)) return false;
            if (survival.isLowerDoublePlant(stateId)) {
                // DoublePlantBlock.placeAt: 위 칸이 비어있어야 하며, 각 칸 waterlogged는 그 칸이 물일 때만 true
                if (!org.domi.furnace.server.world.BlockAttrs.get().isAir(view.getBlock(x, y + 1, z))) return false;
                int lower = survival.withWaterlogged(stateId, survival.isWaterAt(view, x, y, z));
                int upper = survival.withWaterlogged(survival.upperOf(stateId), survival.isWaterAt(view, x, y + 1, z));
                view.setBlock(x, y, z, lower);
                view.setBlock(x, y + 1, z, upper);
            } else {
                view.setBlock(x, y, z, stateId);
            }
            return true;
        }
        int below = view.getBlock(x, y - 1, z);
        boolean survives = (mushroomGrowGround != null)
            ? mushroomGrowGround.contains(below) || (!view.isOpenToSky(x, y, z) && validGround.contains(below))
            : validGround.contains(below);
        if (!survives) {
            return false;
        }
        view.setBlock(x, y, z, stateId);
        return true;
    }
}
