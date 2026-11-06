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

    private NoiseRandom create(ResourceId id) { return create(id, salt, includeWorldSeed, includeSequenceId); }

    private NoiseRandom create(ResourceId id, int salt, boolean includeWorldSeed, boolean includeSequenceId) {
        long seed = (includeWorldSeed ? worldSeed : 0L) ^ (long) salt;
        RandomSupport.Seed128bit s = RandomSupport.upgradeSeedTo128bitUnmixed(seed);
        if (includeSequenceId) {
            RandomSupport.Seed128bit h = RandomSupport.seedFromHashOf(id.toString());
            s = s.xor(h.seedLo(), h.seedHi());
        }
        return new XoroshiroRandomSource(s.mixed());
    }

    /** /random reset * — 지운 시퀀스 수를 돌려준다. */
    public synchronized int clear() {
        int n = sequences.size();
        sequences.clear();
        return n;
    }

    /** /random reset * <seed> [includeWorldSeed] [includeSequenceId] — 앞으로 만들 시퀀스의 기본 시드 규칙(지우는 일은 clear). */
    public synchronized void setSeedDefaults(int salt, boolean includeWorldSeed, boolean includeSequenceId) {
        this.salt = salt;
        this.includeWorldSeed = includeWorldSeed;
        this.includeSequenceId = includeSequenceId;
    }

    /** /random reset <id> <seed> [includeWorldSeed] [includeSequenceId]. */
    public synchronized void reset(ResourceId id, int salt, boolean includeWorldSeed, boolean includeSequenceId) {
        sequences.put(id, create(id, salt, includeWorldSeed, includeSequenceId));
    }

    /** 지금 만들어져 있는 시퀀스 이름(자동완성용). */
    public synchronized java.util.List<ResourceId> ids() { return new java.util.ArrayList<>(sequences.keySet()); }

    /** /random reset <id> */
    public synchronized void reset(ResourceId id) { sequences.put(id, create(id)); }
}
