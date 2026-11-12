// RandomSource 씨앗 이음매(RandomSeeds). 꺼짐(운영): 예측 불가·같은 시각에도 서로 다름. 켜짐(-Dfurnace.test.seeded=true): (열쇠, 용도)만으로 정해지고 호출 순서에 기대지 않음.
// 켜짐은 static final 이라 시험 JVM 에서 바꿀 수 없으므로 별도 JVM 을 띄워 같은 장면을 두 번 돌려 비교한다.
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.entity.DimensionTestLevel;
import org.domi.furnace.server.entity.Zombie;
import org.junit.jupiter.api.Test;

class RandomSeedsTest {

    @Test
    void deriveIsPureAndSensitiveToKeyAndPurpose() {
        assertEquals(RandomSeeds.derive(5, 1), RandomSeeds.derive(5, 1));
        assertNotEquals(RandomSeeds.derive(5, 1), RandomSeeds.derive(6, 1));
        assertNotEquals(RandomSeeds.derive(5, 1), RandomSeeds.derive(5, 2));
        assertNotEquals(RandomSeeds.derive(1, 2), RandomSeeds.derive(2, 1));
        // 이웃한 열쇠(엔티티 id 1, 2, 3…)가 서로 다른 첫 값을 낸다
        Set<Long> firsts = new HashSet<>();
        for (int id = 0; id < 10_000; id++) firsts.add(RandomSource.create(RandomSeeds.derive(id, RandomSeeds.Purpose.ENTITY)).nextLong());
        assertEquals(10_000, firsts.size());
    }

    @Test
    void keyAndCombineAreStable() {
        // 금 값: 바뀌면 시험 JVM 의 모든 난수열이 달라진다 — 의도한 변경일 때만 고친다.
        assertEquals(RandomSeeds.key("Sensor.RANDOM"), RandomSeeds.key("Sensor.RANDOM"));
        assertNotEquals(RandomSeeds.key("a"), RandomSeeds.key("b"));
        assertEquals(0xCBF29CE484222325L, RandomSeeds.key(""));
        assertNotEquals(RandomSeeds.combine(1, 2), RandomSeeds.combine(2, 1));
        assertNotEquals(RandomSeeds.world("minecraft:overworld", null), RandomSeeds.world("minecraft:the_nether", null));
        assertNotEquals(RandomSeeds.world("minecraft:overworld", null), RandomSeeds.world("minecraft:overworld", "extra"));
    }

    /** 꺼짐(운영): 같은 열쇠로 연달아 만들어도 다른 난수열(바닐라 RandomSource.create() 처럼). */
    @Test
    void offModeSeedsAreUnpredictable() {
        assumeFalse(RandomSeeds.SEEDED, "이 시험 JVM 은 seeded 켜짐");
        Set<Long> seeds = new HashSet<>();
        for (int i = 0; i < 10_000; i++) seeds.add(RandomSeeds.seed(1L, RandomSeeds.Purpose.ENTITY));   // nanoTime 해상도가 거칠어도 uniquifier 때문에 겹치지 않는다
        assertEquals(10_000, seeds.size());
        assertNotEquals(RandomSource.createFor(7, 1).nextLong(), RandomSource.createFor(7, 1).nextLong());
    }

    /** 켜짐: 난수 객체를 어떤 순서로·몇 개 더 만들어도 (열쇠, 용도)의 난수열은 같다(카운터 씨앗이 아니라는 증명). */
    @Test
    void seededStreamsDoNotDependOnCreationOrder() throws Exception {
        String a = run("order", "x");
        String b = run("order", "y");   // 두 번째는 사이에 난수를 더 만든다
        assertEquals(a.lines().filter(l -> l.startsWith("k=")).toList(), b.lines().filter(l -> l.startsWith("k=")).toList());
    }

    /** 켜짐: 같은 장면(레벨 + 엔티티)을 별도 JVM 두 번 → 엔티티 UUID·엔티티 난수·레벨 난수가 모두 같다. 꺼짐은 서로 다르다. */
    @Test
    void sameSceneTwiceGivesSameStreamsOnlyWhenSeeded() throws Exception {
        String s1 = run("scene", "seeded"), s2 = run("scene", "seeded");
        assertTrue(s1.contains("uuid="), s1);
        assertEquals(s1, s2);
        String u1 = run("scene", "unseeded"), u2 = run("scene", "unseeded");
        assertNotEquals(u1, u2);
    }

    private static String run(String mode, String variant) throws Exception {
        String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        boolean seeded = !variant.equals("unseeded");
        ProcessBuilder pb = new ProcessBuilder(java, "-ea", "-Dfurnace.test.seeded=" + seeded, "-cp", System.getProperty("java.class.path"),
                Probe.class.getName(), mode, variant);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes());
        assertTrue(p.waitFor(120, TimeUnit.SECONDS));
        assertEquals(0, p.exitValue(), out);
        return out.lines().filter(l -> l.startsWith("k=") || l.startsWith("uuid=") || l.startsWith("e=") || l.startsWith("lv=")).reduce("", (x, y) -> x + y + "\n");
    }

    /** 별도 JVM 에서 도는 장면. */
    public static final class Probe {
        public static void main(String[] args) {
            if (args[0].equals("order")) {
                if (args[1].equals("y")) for (int i = 0; i < 37; i++) RandomSource.createFor(1000 + i, RandomSeeds.Purpose.RAID).nextLong();   // 사이에 임시 난수를 더 만든다
                for (int i = 0; i < 5; i++) System.out.println("k=" + i + ":" + RandomSource.createFor(i, RandomSeeds.Purpose.ENTITY).nextLong());
                if (args[1].equals("x")) for (int i = 0; i < 37; i++) RandomSource.createFor(1000 + i, RandomSeeds.Purpose.RAID).nextLong();
                return;
            }
            DimensionTestLevel level = new DimensionTestLevel("minecraft:overworld");
            for (int i = 0; i < 3; i++) {
                Zombie z = new Zombie(level, level.nextEntityId());
                System.out.println("uuid=" + z.uuid());
                System.out.println("e=" + z.random().nextLong() + "," + z.random().nextInt(1000));
            }
            System.out.println("lv=" + level.random().nextLong());
        }
    }
}
