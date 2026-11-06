// 바닐라 RandomLookAround: 시선 목표가 없을 때 가끔 몸 방향 둘레의 무작위 방향을 바라보는 LOOK_TARGET 을 둔다.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.Map;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.brain.BlockPosTracker;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.MemoryStatus;
import org.domi.furnace.server.entity.ai.control.Mth;

public class RandomLookAround extends Behavior<Mob> {
    private final UniformInt interval;
    private final float maxYaw;
    private final float minPitch;
    private final float pitchRange;

    public RandomLookAround(UniformInt interval, float maxYaw, float minPitch, float maxPitch) {
        super(Map.of(MemoryModuleType.LOOK_TARGET, MemoryStatus.VALUE_ABSENT, MemoryModuleType.GAZE_COOLDOWN_TICKS, MemoryStatus.VALUE_ABSENT));
        if (minPitch > maxPitch) throw new IllegalArgumentException("Minimum pitch is larger than maximum pitch! " + minPitch + " > " + maxPitch);
        this.interval = interval;
        this.maxYaw = maxYaw;
        this.minPitch = minPitch;
        this.pitchRange = maxPitch - minPitch;
    }

    @Override
    protected void start(ChunkLevel level, Mob mob, long gameTime) {
        var random = mob.random();
        float pitch = Math.clamp(random.nextFloat() * pitchRange + minPitch, -90.0F, 90.0F);
        float yaw = Mth.wrapDegrees(mob.yRot() + 2.0F * random.nextFloat() * maxYaw - maxYaw);
        double[] v = org.domi.furnace.server.block.Clip.viewVector(pitch, yaw);
        double[] eye = {mob.x(), mob.eyeY(), mob.z()};
        mob.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(eye[0] + v[0], eye[1] + v[1], eye[2] + v[2]));
        mob.getBrain().setMemory(MemoryModuleType.GAZE_COOLDOWN_TICKS, interval.sample(random));
    }
}
