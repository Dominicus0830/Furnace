// state id → FluidKind 표. 바닐라 BlockState.getFluidState(): 물/용암 블록은 level 0이 source(그 외 flowing),
// waterlogged=true 이거나 항상 물을 품는 블록(kelp, seagrass, bubble_column 등)은 물 source.
package org.domi.furnace.server.world.gen.feature;

import java.util.Set;
import org.domi.furnace.server.world.BlockRegistry;

public final class FluidKinds {

    private static final Set<String> ALWAYS_WATER = Set.of(
        "minecraft:kelp", "minecraft:kelp_plant", "minecraft:seagrass", "minecraft:tall_seagrass", "minecraft:bubble_column");

    private final byte[] kinds;

    private static volatile FluidKinds shared;

    /** 기본 블록 레지스트리(blocks/1.21.1.txt — 서버 전체가 같은 상태 번호)의 표. */
    public static FluidKinds shared() {
        FluidKinds k = shared;
        if (k == null) {
            synchronized (FluidKinds.class) {
                k = shared;
                if (k == null) shared = k = new FluidKinds(BlockRegistry.load("blocks/1.21.1.txt"));
            }
        }
        return k;
    }

    public FluidKinds(BlockRegistry registry) {
        int n = registry.totalStateCount();
        kinds = new byte[n];
        for (int id = 0; id < n; id++) {
            String key = registry.canonicalKeyOf(id);
            if (key == null) continue;
            int semi = key.indexOf(';');
            String name = semi < 0 ? key : key.substring(0, semi);
            String props = semi < 0 ? "" : key.substring(semi + 1);
            FluidKind k = FluidKind.EMPTY;
            if (name.equals("minecraft:water") || name.equals("minecraft:lava")) {
                boolean source = props.isEmpty() || props.contains("level=0");
                k = name.equals("minecraft:water") ? (source ? FluidKind.WATER : FluidKind.FLOWING_WATER)
                                                   : (source ? FluidKind.LAVA : FluidKind.FLOWING_LAVA);
            } else if (props.contains("waterlogged=true") || ALWAYS_WATER.contains(name)) {
                k = FluidKind.WATER;
            }
            kinds[id] = (byte) k.ordinal();
        }
    }

    public FluidKind of(int stateId) {
        return stateId >= 0 && stateId < kinds.length ? FluidKind.values()[kinds[stateId]] : FluidKind.EMPTY;
    }
}
