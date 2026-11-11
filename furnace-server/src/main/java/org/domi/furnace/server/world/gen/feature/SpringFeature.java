package org.domi.furnace.server.world.gen.feature;

import java.util.Set;
import org.domi.furnace.server.world.ChunkSection;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class SpringFeature implements ConfiguredFeature {

    private final int stateId;
    private final boolean requiresBlockBelow;
    private final int rockCount;
    private final int holeCount;
    private final Set<Integer> validBlockIds;

    public SpringFeature(
            int stateId,
            boolean requiresBlockBelow,
            int rockCount,
            int holeCount,
            Set<Integer> validBlockIds) {
        this.stateId = stateId;
        this.requiresBlockBelow = requiresBlockBelow;
        this.rockCount = rockCount;
        this.holeCount = holeCount;
        this.validBlockIds = Set.copyOf(validBlockIds);
    }

    @Override
    public boolean place(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
        org.domi.furnace.server.world.BlockAttrs a = org.domi.furnace.server.world.BlockAttrs.get();
        if (!isValidBlock(view.getBlock(x, y + 1, z))) return false;
        if (requiresBlockBelow && !isValidBlock(view.getBlock(x, y - 1, z))) return false;
        int self = view.getBlock(x, y, z);
        if (!a.isAir(self) && !isValidBlock(self)) return false;

        // 바닐라 SpringFeature: 서·동·북·남·아래 5칸만 센다(위 제외). 구멍은 isEmptyBlock(=isAir, cave_air 포함). 둘 다 정확히 일치해야 한다.
        int rocks = 0;
        int holes = 0;
        int[][] offsets = {{-1, 0, 0}, {1, 0, 0}, {0, 0, -1}, {0, 0, 1}, {0, -1, 0}};
        for (int[] offset : offsets) {
            int state = view.getBlock(x + offset[0], y + offset[1], z + offset[2]);
            if (isValidBlock(state)) rocks++;
            if (a.isAir(state)) holes++;
        }
        if (rocks == rockCount && holes == holeCount) {
            view.trySetBlock(x, y, z, stateId);
            // 바닐라 SpringFeature.place: setBlock 뒤 scheduleTick(pos, 샘 유체, 0) — 틱 범위에 들어오면 바로 흐른다(A06-03)
            view.scheduleFluidTick(x, y, z, FeatureWorldView.fluidTypeOf(stateId), 0);
            return true;
        }
        return false;
    }

    private boolean isValidBlock(int stateId) {
        return validBlockIds.contains(stateId);
    }
}
