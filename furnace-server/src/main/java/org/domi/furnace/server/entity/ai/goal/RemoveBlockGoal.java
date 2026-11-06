// 바닐라 RemoveBlockGoal: 가까운 특정 블록(위 두 칸이 비어 있는) 위로 가서 몇 초 동안 밟다가 없앤다(mobGriefing 필요).
package org.domi.furnace.server.entity.ai.goal;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.PathfinderMob;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.protocol.play.ParticleOptions;

public class RemoveBlockGoal extends MoveToBlockGoal {
    private static final int WAIT_AFTER_BLOCK_FOUND = 20;
    private final String blockToRemove;
    private final Mob removerMob;
    private int ticksSinceReachedGoal;

    public RemoveBlockGoal(String block, PathfinderMob mob, double speed, int verticalRange) {
        super(mob, speed, 24, verticalRange);
        this.blockToRemove = block;
        this.removerMob = mob;
    }

    @Override
    public boolean canUse() {
        if (!removerMob.level().gameRule("mobGriefing")) return false;
        if (nextStartTick > 0) {
            nextStartTick--;
            return false;
        }
        if (findNearestBlock()) {
            nextStartTick = reducedTickDelay(WAIT_AFTER_BLOCK_FOUND);
            return true;
        }
        nextStartTick = nextStartTick(mob);
        return false;
    }

    @Override
    public void stop() {
        super.stop();
        removerMob.fallDistance = 1.0F;
    }

    @Override
    public void start() {
        super.start();
        ticksSinceReachedGoal = 0;
    }

    /** 밟는 동안 6틱마다 나는 소리. */
    public void playDestroyProgressSound(ChunkLevel level, Pos pos) {}

    /** 없앤 뒤의 소리. */
    public void playBreakSound(ChunkLevel level, Pos pos) {}

    @Override
    public void tick() {
        super.tick();
        ChunkLevel level = removerMob.level();
        Pos pos = getPosWithBlock(removerMob.blockPosition(), level);
        RandomSource random = removerMob.random();
        if (isReachedTarget() && pos != null) {
            if (ticksSinceReachedGoal > 0) {
                removerMob.setDeltaMovement(removerMob.vx(), 0.3, removerMob.vz());
                level.sendParticles(new ParticleOptions.Item(new ItemStack(ItemContext.id("minecraft:item", "minecraft:egg"), 1)),
                        pos.x() + 0.5, pos.y() + 0.7, pos.z() + 0.5, 3,
                        (random.nextFloat() - 0.5) * 0.08, (random.nextFloat() - 0.5) * 0.08, (random.nextFloat() - 0.5) * 0.08, 0.15F);
            }
            if (ticksSinceReachedGoal % 2 == 0) {
                removerMob.setDeltaMovement(removerMob.vx(), -0.3, removerMob.vz());
                if (ticksSinceReachedGoal % 6 == 0) playDestroyProgressSound(level, blockPos);
            }
            if (ticksSinceReachedGoal > 60) {
                level.removeBlock(pos, false);
                for (int i = 0; i < 20; i++) {
                    double dx = random.nextGaussian() * 0.02, dy = random.nextGaussian() * 0.02, dz = random.nextGaussian() * 0.02;
                    level.sendParticles(ParticleOptions.simple("poof"), pos.x() + 0.5, pos.y(), pos.z() + 0.5, 1, dx, dy, dz, 0.15F);
                }
                playBreakSound(level, pos);
            }
            ticksSinceReachedGoal++;
        }
    }

    /** 발밑 칸, 아래, 서·동·북·남, 두 칸 아래 순으로 그 블록을 찾는다. */
    private Pos getPosWithBlock(Pos pos, BlockLevel level) {
        if (B.is(level.getBlockState(pos), blockToRemove)) return pos;
        Pos[] around = {pos.below(), pos.west(), pos.east(), pos.north(), pos.south(), pos.below().below()};
        for (Pos p : around) if (B.is(level.getBlockState(p), blockToRemove)) return p;
        return null;
    }

    @Override
    protected boolean isValidTarget(BlockLevel level, Pos pos) {
        // 바닐라: getChunk(FULL, false) 가 없으면 거짓
        if (!level.hasChunkAt(pos)) return false;
        return B.is(level.getBlockState(pos), blockToRemove) && B.isAir(level.getBlockState(pos.above())) && B.isAir(level.getBlockState(pos.above().above()));
    }
}
