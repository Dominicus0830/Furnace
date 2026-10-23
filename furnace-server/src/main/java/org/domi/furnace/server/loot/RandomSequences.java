// 바닐라 RandomSequences — 전리품 표의 random_sequence 마다 영속 Xoroshiro 난수 하나(서버 전체가 공유).
// 시드: (월드 시드 ^ salt) 를 128비트로 늘린 값(섞기 전) ^ MD5(id 문자열), 그다음 Stafford13 섞기.
package org.domi.furnace.server.loot;

import java.util.HashMap;
import java.util.Map;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.world.gen.noise.NoiseRandom;
import org.domi.furnace.server.world.gen.noise.RandomSupport;
import org.domi.furnace.server.world.gen.noise.XoroshiroRandomSource;

public final class RandomSequences {

    private final long worldSeed;
    private int salt;
    private boolean includeWorldSeed = true;
    private boolean includeSequenceId = true;
    private final Map<ResourceId, NoiseRandom> sequences = new HashMap<>();

    public RandomSequences(long worldSeed) { this.worldSeed = worldSeed; }

    public synchronized NoiseRandom get(ResourceId id) { return sequences.computeIfAbsent(id, this::create); }

    private NoiseRandom create(ResourceId id) {
        long seed = (includeWorldSeed ? worldSeed : 0L) ^ (long) salt;
        RandomSupport.Seed128bit s = RandomSupport.upgradeSeedTo128bitUnmixed(seed);
        if (includeSequenceId) {
            RandomSupport.Seed128bit h = RandomSupport.seedFromHashOf(id.toString());
            s = s.xor(h.seedLo(), h.seedHi());
        }
        return new XoroshiroRandomSource(s.mixed());
    }

    /** /random reset * */
    public synchronized void clear() { sequences.clear(); }

    /** /random reset <id> */
    public synchronized void reset(ResourceId id) { sequences.put(id, create(id)); }
}
