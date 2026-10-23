// end_spike — 바닐라 EndSpikeFeature(+SpikeCacheLoader 시드 파생 스파이크 목록) 이식: 흑요석 기둥, 철창 우리, 엔드 수정 엔티티, 기반암+불
package org.domi.furnace.server.world.gen.feature;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.gen.carver.LegacyRandom;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class EndSpikeFeature implements ConfiguredFeature {

    /** EndSpikeFeature.EndSpike(centerX, centerZ, radius, height, guarded). */
    public record Spike(int centerX, int centerZ, int radius, int height, boolean guarded) {
        /** isCenterInChunk(pos) — 섹션 좌표 비교. */
        boolean isCenterInChunk(int x, int z) {
            return (x >> 4) == (centerX >> 4) && (z >> 4) == (centerZ >> 4);
        }
    }

    // SpikeCacheLoader 는 키(시드의 nextLong & 0xFFFF)별로 캐시 — 계산이 가벼워 만료 없이 보관한다.
    private static final Map<Long, List<Spike>> CACHE = new ConcurrentHashMap<>();

    /** EndSpikeFeature.getSpikesForLevel(level): LegacyRandom(worldSeed).nextLong() & 65535 를 키로 스파이크 10개를 만든다. */
    public static List<Spike> spikesForSeed(long worldSeed) {
        long key = new LegacyRandom(worldSeed).nextLong() & 65535L;
        return CACHE.computeIfAbsent(key, EndSpikeFeature::loadSpikes);
    }

    /** SpikeCacheLoader.load(key). */
    static List<Spike> loadSpikes(long key) {
        // Util.shuffle(IntStream.range(0, 10), RandomSource.create(key)) — i = size..2, swap(i-1, nextInt(i)).
        LegacyRandom random = new LegacyRandom(key);
        int[] order = new int[10];
        for (int i = 0; i < 10; i++) order[i] = i;
        for (int i = order.length; i > 1; i--) {
            int j = random.nextInt(i);
            int tmp = order[j];
            order[j] = order[i - 1];
            order[i - 1] = tmp;
        }
        List<Spike> out = new ArrayList<>(10);
        for (int i = 0; i < 10; i++) {
            int x = (int) Math.floor(42.0 * Math.cos(2.0 * (-Math.PI + (Math.PI / 10) * (double) i)));
            int z = (int) Math.floor(42.0 * Math.sin(2.0 * (-Math.PI + (Math.PI / 10) * (double) i)));
            int o = order[i];
            int radius = 2 + o / 3;
            int height = 76 + o * 3;
            boolean guarded = o == 1 || o == 2;
            out.add(new Spike(x, z, radius, height, guarded));
        }
        return out;
    }

    private final int obsidian, air, bedrock, fire;
    /** iron_bars 16 상태: 비트 0 north, 1 east, 2 south, 3 west. */
    private final int[] bars = new int[16];
    private final boolean crystalInvulnerable;
    private final List<Spike> configuredSpikes;
    private final int[] crystalBeamTarget;

    public EndSpikeFeature(BlockRegistry registry, boolean crystalInvulnerable, List<Spike> spikes, int[] crystalBeamTarget) {
        this.obsidian = registry.defaultStateId("minecraft:obsidian");
        this.air = registry.defaultStateId("minecraft:air");
        this.bedrock = registry.defaultStateId("minecraft:bedrock");
        // BaseFireBlock.getState(level, crystalPos): 아래가 방금 놓은 기반암(soul_fire_base_blocks 아님, 윗면 단단함)이라
        // FireBlock.getStateForPlacement 가 속성이 모두 기본값인 fire 를 돌려준다.
        this.fire = registry.defaultStateId("minecraft:fire");
        for (int bits = 0; bits < 16; bits++) {
            Map<String, String> p = new java.util.HashMap<>();
            p.put("north", String.valueOf((bits & 1) != 0));
            p.put("east", String.valueOf((bits & 2) != 0));
            p.put("south", String.valueOf((bits & 4) != 0));
            p.put("west", String.valueOf((bits & 8) != 0));
            p.put("waterlogged", "false");
            int id = registry.exactStateId(org.domi.furnace.server.world.BlockStateKey.of("minecraft:iron_bars", p));
            if (id < 0) throw new IllegalStateException("iron_bars 상태를 찾지 못함: " + p);
            bars[bits] = id;
        }
        this.crystalInvulnerable = crystalInvulnerable;
        this.configuredSpikes = spikes;
        this.crystalBeamTarget = crystalBeamTarget;
    }

    @Override
    public boolean place(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
        List<Spike> spikes = configuredSpikes.isEmpty() ? spikesForSeed(view.worldSeed()) : configuredSpikes;
        for (Spike spike : spikes) {
            if (spike.isCenterInChunk(x, z)) placeSpike(view, random, spike);
        }
        return true;
    }

    private void placeSpike(FeatureWorldView view, WorldgenRandom random, Spike spike) {
        int r = spike.radius();
        int cx = spike.centerX(), cz = spike.centerZ(), height = spike.height();

        // BlockPos.betweenClosed((cx-r, minY, cz-r), (cx+r, height+10, cz+r)) — x 안쪽, y, z 바깥.
        for (int z = cz - r; z <= cz + r; z++) {
            for (int y = view.minY(); y <= height + 10; y++) {
                for (int x = cx - r; x <= cx + r; x++) {
                    // Vec3i.distToLowCornerSqr(cx, y, cz) — 높이 차이는 0 이라 수평 거리만 남는다.
                    double dx = x - (double) cx, dz = z - (double) cz;
                    if (dx * dx + 0.0 + dz * dz <= (double) (r * r + 1) && y < height) {
                        view.setBlock(x, y, z, obsidian);
                    } else if (y > 65) {
                        view.setBlock(x, y, z, air);
                    }
                }
            }
        }

        if (spike.guarded()) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dy = 0; dy <= 3; dy++) {
                        boolean xEdge = Math.abs(dx) == 2;
                        boolean zEdge = Math.abs(dz) == 2;
                        boolean top = dy == 3;
                        if (xEdge || zEdge || top) {
                            boolean flag = dx == -2 || dx == 2 || top;
                            boolean flag1 = dz == -2 || dz == 2 || top;
                            int bits = (flag && dz != -2 ? 1 : 0)      // north
                                | (flag1 && dx != 2 ? 2 : 0)           // east
                                | (flag && dz != 2 ? 4 : 0)            // south
                                | (flag1 && dx != -2 ? 8 : 0);         // west
                            view.setBlock(cx + dx, height + dy, cz + dz, bars[bits]);
                        }
                    }
                }
            }
        }

        // EndCrystal 생성: setBeamTarget → setInvulnerable → moveTo(x+0.5, height+1, z+0.5, random.nextFloat()*360, 0) → addFreshEntity
        float yaw = random.nextFloat() * 360.0F;
        NbtCompound crystal = view.addEntity("minecraft:end_crystal", cx + 0.5, height + 1, cz + 0.5, yaw, 0.0F);
        // Entity.saveWithoutId 의 Invulnerable + EndCrystal.addAdditionalSaveData(beam_target, ShowBottom=기본 true)
        crystal.putByte("Invulnerable", (byte) (crystalInvulnerable ? 1 : 0));
        if (crystalBeamTarget != null) crystal.put("beam_target", new int[]{crystalBeamTarget[0], crystalBeamTarget[1], crystalBeamTarget[2]});
        crystal.putByte("ShowBottom", (byte) 1);
        // 엔티티 blockPosition() = (cx, height+1, cz): 아래에 기반암, 그 자리에 불.
        view.setBlock(cx, height, cz, bedrock);
        view.setBlock(cx, height + 1, cz, fire);
    }
}
