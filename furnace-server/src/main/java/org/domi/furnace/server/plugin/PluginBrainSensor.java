// 플러그인 BrainSensor 를 서버 센서 자리에 끼우는 어댑터. 브레인 틱(소유 region 스레드)에서 scanRate 틱마다 불린다.
// 플러그인이 꺼졌거나 예외를 던지면 다시 부르지 않고 스스로 빠진다(예외는 로그 한 번).
package org.domi.furnace.server.plugin;

import java.util.HashSet;
import java.util.Set;
import org.domi.furnace.api.entity.ai.brain.BrainSensor;
import org.domi.furnace.api.entity.ai.brain.MemoryKey;
import org.domi.furnace.api.entity.ai.brain.SensorKey;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.Sensor;
import org.slf4j.LoggerFactory;

final class PluginBrainSensor extends Sensor<Mob> {

    final FurnacePlugin owner;
    final BrainSensor sensor;
    private final SensorKey key;
    private final MobBrainImpl brain;
    private final MobAiImpl ai;
    private final Set<MemoryModuleType<?>> requires = new HashSet<>();
    private boolean broken;

    PluginBrainSensor(FurnacePlugin owner, BrainSensor sensor, SensorKey key, int scanRate, MobBrainImpl brain) {
        super(scanRate);
        this.owner = owner;
        this.sensor = sensor;
        this.key = key;
        this.brain = brain;
        this.ai = new MobAiImpl(brain.host(), brain.mob);
        for (MemoryKey<?> m : sensor.memories()) {
            MemoryModuleType<?> t = MobBrainImpl.vanilla(m);
            if (t != null) requires.add(t);
        }
    }

    @Override
    protected void doTick(ChunkLevel level, Mob entity) {
        if (broken || !owner.isEnabled()) {
            entity.getBrain().removeSensor(key.toString());
            return;
        }
        try {
            sensor.sense(ai, brain);
        } catch (Throwable t) {
            broken = true;
            LoggerFactory.getLogger(PluginBrainSensor.class).warn("플러그인 {} 의 두뇌 센서 {} 가 예외를 던져 뗀다", owner, key, t);
            entity.getBrain().removeSensor(key.toString());
        }
    }

    @Override
    public Set<MemoryModuleType<?>> requires() { return requires; }

    @Override
    public String toString() { return "PluginBrainSensor[" + key + "]"; }
}
