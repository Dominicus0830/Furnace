// 바닐라 WeatheringCopper + ChangeOverTimeBlock: 구리 블록 묶음의 산화 단계 사슬과 무작위 틱 산화(onRandomTick).
package org.domi.furnace.server.block.types;

import java.util.HashMap;
import java.util.Map;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;

public final class WeatheringCopper {
    private WeatheringCopper() {}

    private static final String[][] CHAINS = {
        {"copper_block", "exposed_copper", "weathered_copper", "oxidized_copper"},
        {"cut_copper", "exposed_cut_copper", "weathered_cut_copper", "oxidized_cut_copper"},
        {"chiseled_copper", "exposed_chiseled_copper", "weathered_chiseled_copper", "oxidized_chiseled_copper"},
        {"copper_grate", "exposed_copper_grate", "weathered_copper_grate", "oxidized_copper_grate"},
        {"cut_copper_stairs", "exposed_cut_copper_stairs", "weathered_cut_copper_stairs", "oxidized_cut_copper_stairs"},
        {"cut_copper_slab", "exposed_cut_copper_slab", "weathered_cut_copper_slab", "oxidized_cut_copper_slab"},
        {"copper_door", "exposed_copper_door", "weathered_copper_door", "oxidized_copper_door"},
        {"copper_trapdoor", "exposed_copper_trapdoor", "weathered_copper_trapdoor", "oxidized_copper_trapdoor"},
        {"copper_bulb", "exposed_copper_bulb", "weathered_copper_bulb", "oxidized_copper_bulb"},
    };
    /** 블록 번호 → 산화 단계(0 unaffected … 3 oxidized). 산화 구리가 아니면 없음. */
    private static final Map<Integer, Integer> AGE = new HashMap<>();
    /** 블록 번호 → 다음 단계 블록. */
    private static final Map<Integer, Integer> NEXT = new HashMap<>();
    /** 블록 번호 → 앞 단계 블록(도끼로 긁기 — WeatheringCopper.getPrevious). */
    private static final Map<Integer, Integer> PREVIOUS = new HashMap<>();

    static {
        for (String[] c : CHAINS) {
            for (int i = 0; i < 4; i++) {
                int b = B.id(c[i]);
                AGE.put(b, i);
                if (i < 3) NEXT.put(b, B.id(c[i + 1]));
                if (i > 0) PREVIOUS.put(b, B.id(c[i - 1]));
            }
        }
    }

    /** 산화 단계(getAge). 구리 사슬 블록이 아니면 -1. */
    public static int age(int block) { return AGE.getOrDefault(block, -1); }

    /** getPrevious(state): 앞 단계 블록의 같은 속성 상태, 없으면 -1. */
    public static int previous(int state) {
        Integer b = PREVIOUS.get(B.block(state));
        return b == null ? -1 : B.withPropertiesOf(b, state);
    }

    /** isRandomlyTicking: 다음 단계가 있으면. */
    public static boolean hasNext(int block) { return NEXT.containsKey(block); }

    /** ChangeOverTimeBlock.changeOverTime(onRandomTick): 1/약 17.6 확률로 주변(맨해튼 4) 단계 비율에 따라 다음 단계로. */
    public static void randomTick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (random.nextFloat() < 0.05688889F) {
            int next = getNextState(state, level, pos, random);
            if (next >= 0) level.setBlockAndUpdate(pos, next);
        }
    }

    private static int getNextState(int state, BlockLevel level, Pos pos, RandomSource random) {
        int age = age(B.block(state));
        int same = 0, older = 0;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dz = -4; dz <= 4; dz++) {
                    if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) > 4 || dx == 0 && dy == 0 && dz == 0) continue;
                    int a = age(B.block(level.getBlockState(pos.offset(dx, dy, dz))));
                    if (a < 0) continue;
                    if (a < age) return -1;
                    if (a > age) older++;
                    else same++;
                }
            }
        }
        float f = (float) (older + 1) / (float) (older + same + 1);
        float chance = f * f * (age == 0 ? 0.75F : 1.0F);
        if (random.nextFloat() < chance) {
            Integer n = NEXT.get(B.block(state));
            return n == null ? -1 : B.withPropertiesOf(n, state);
        }
        return -1;
    }
}
