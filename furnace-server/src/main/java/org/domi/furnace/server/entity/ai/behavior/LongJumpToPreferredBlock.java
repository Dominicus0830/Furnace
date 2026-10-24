// 바닐라 LongJumpToPreferredBlock: 긴 점프 후보 중 선호 블록(태그) 위 칸을 확률(preferredBlockChance)로 먼저 고른다.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.PathfinderMob;

public class LongJumpToPreferredBlock<E extends PathfinderMob> extends LongJumpToRandomPos<E> {
    private final String preferredBlockTag;
    private final float preferredBlocksChance;
    private final List<PossibleJump> notPrefferedJumpCandidates = new ArrayList<>();
    private boolean currentlyWantingPreferredOnes;

    public LongJumpToPreferredBlock(UniformInt time, int height, int width, float vel, Function<E, String> sound, String preferredTag, float chance, BiPredicate<E, org.domi.furnace.server.block.Pos> acceptable) {
        super(time, height, width, vel, sound, acceptable);
        this.preferredBlockTag = preferredTag;
        this.preferredBlocksChance = chance;
    }

    @Override
    protected void start(ChunkLevel level, E mob, long gameTime) {
        super.start(level, mob, gameTime);
        notPrefferedJumpCandidates.clear();
        currentlyWantingPreferredOnes = mob.random().nextFloat() < preferredBlocksChance;
    }

    @Override
    protected Optional<PossibleJump> getJumpCandidate(ChunkLevel level) {
        if (!currentlyWantingPreferredOnes) return super.getJumpCandidate(level);
        while (!jumpCandidates.isEmpty()) {
            Optional<PossibleJump> c = super.getJumpCandidate(level);
            if (c.isPresent()) {
                PossibleJump j = c.get();
                if (B.isTag(level.getBlockState(j.pos().below()), preferredBlockTag)) return c;
                notPrefferedJumpCandidates.add(j);
            }
        }
        return !notPrefferedJumpCandidates.isEmpty() ? Optional.of(notPrefferedJumpCandidates.remove(0)) : Optional.empty();
    }
}
