// T-018(P-O05-07): 블록 충돌 수집 재작성(상태별 충돌 표·구역 단위 읽기·공기 구역 건너뛰기·칸마다 모양 객체 없애기)이 옛 칸별 수집기
// (LegacyBlockCollisions·LegacyEntityCollide, 7b2d6b77 사본)와 결과가 비트 단위로 같은지 IdentityKit 차분으로 본다.
// 무작위 블록 배치(계단·울타리·담·가루눈·움직이는 피스톤·비계·눈 층·문 등 모양 블록 + 공기·동굴 공기·빈 공간 공기 + 아무 상태),
// 청크·구역 경계와 로드 안 된 청크 가장자리, 무작위 상자(정수 경계 ±1e-7, 얇은 상자 포함)·이동·문맥 엔티티(가루눈)·배·셜커 몸·월드 보더.
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import org.domi.furnace.server.block.entity.PistonMovingBlockEntity;
import org.domi.furnace.server.entity.AABB;
import org.domi.furnace.server.entity.Boat;
import org.domi.furnace.server.entity.Cow;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.LegacyEntityCollide;
import org.domi.furnace.server.entity.Rabbit;
import org.domi.furnace.server.entity.Shulker;
import org.domi.furnace.server.identity.Differential;
import org.domi.furnace.server.world.ChunkColumn;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CollisionScanParityTest {

    /** 블록 모양이 다양한 블록(상태 전부를 무작위로 고른다). */
    private static final String[] SHAPED = {
            "oak_stairs", "stone_slab", "oak_fence", "cobblestone_wall", "oak_fence_gate", "iron_bars", "glass_pane", "powder_snow",
            "moving_piston", "scaffolding", "snow", "oak_door", "oak_trapdoor", "red_bed", "chest", "hopper", "lantern", "bamboo",
            "pointed_dripstone", "shulker_box", "anvil", "cauldron", "soul_sand", "honey_block", "end_rod", "lectern", "grindstone",
            "piston_head", "piston", "ladder", "vine", "lily_pad", "cake", "composter", "chain", "lightning_rod", "candle",
            "sea_pickle", "turtle_egg", "flower_pot", "stonecutter", "bell", "campfire", "dirt_path", "farmland", "mud", "stone",
            "short_grass", "water", "lava", "oak_sign", "rail", "torch", "cobweb", "sweet_berry_bush", "big_dripleaf", "azalea"};

    private static ParityLevel level;
    private static Cow cow;
    private static Rabbit rabbit;
    private static Boat selfBoat;
    private static final List<int[]> SHAPED_STATES = new ArrayList<>();

    /** 평지 시험 레벨 + 보더 바꾸기. */
    static final class ParityLevel extends BlockPlacementTest.TestLevel {
        double[] border = {-2.9999984E7, -2.9999984E7, 2.9999984E7, 2.9999984E7};

        @Override
        protected LevelServices services() {
            return new LevelServices.Delegating(LevelServices.DEFAULT) {
                @Override public double[] borderBounds() { return border.clone(); }
            };
        }
    }

    @BeforeAll
    static void build() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
        Blocks blocks = Blocks.get();
        for (String n : SHAPED) {
            int b = blocks.block("minecraft:" + n);
            assertTrue(b >= 0, n);
            SHAPED_STATES.add(new int[] {blocks.firstState(b), blocks.stateCountOf(b)});
        }
        level = new ParityLevel();
        SplittableRandom r = new SplittableRandom(0x7018L);
        int air = B.defaultState("minecraft:air"), caveAir = B.defaultState("minecraft:cave_air"), voidAir = B.defaultState("minecraft:void_air");
        int piston = B.id("moving_piston");
        // 가운데(청크 0·1 경계, 구역 -64/-48/-32 경계)와 로드된 끝(x = -32 아래는 청크가 없다). y -48..-33 구역 일부는 공기뿐으로 남긴다.
        int[][] areas = {{-6, 22, -6, 22}, {-34, -25, -6, 6}};
        for (int[] a : areas) {
            for (int x = a[0]; x <= a[1]; x++) {
                for (int z = a[2]; z <= a[3]; z++) {
                    ChunkColumn col = level.chunk(x >> 4, z >> 4);
                    if (col == null) continue;
                    for (int y = -64; y <= -20; y++) {
                        if (y >= -48 && y < -32 && x >= 16) continue;   // 그 구역은 공기뿐(hasOnlyAir 건너뛰기)
                        int roll = r.nextInt(100);
                        int st;
                        if (roll < 40) st = air;
                        else if (roll < 50) st = caveAir;
                        else if (roll < 53) st = voidAir;
                        else if (roll < 85) {
                            int[] range = SHAPED_STATES.get(r.nextInt(SHAPED_STATES.size()));
                            st = range[0] + r.nextInt(range[1]);
                        } else {
                            st = r.nextInt(blocks.stateCount());
                        }
                        col.setBlock(x & 15, y, z & 15, st);
                        if (blocks.blockOf(st) == piston && r.nextBoolean()) {
                            int[] moved;
                            do moved = SHAPED_STATES.get(r.nextInt(SHAPED_STATES.size())); while (blocks.blockOf(moved[0]) == piston);   // 옮기는 블록이 움직이는 피스톤이면 끝없이 돈다
                            level.setBlockEntity(new PistonMovingBlockEntity(new Pos(x, y, z), st, moved[0] + r.nextInt(moved[1]),
                                    r.nextInt(6), r.nextBoolean(), r.nextBoolean()));
                        }
                    }
                }
            }
        }
        // 엔티티 충돌 모양(배·셜커 몸) — 블록 모양보다 목록 앞에 온다
        for (int i = 0; i < 25; i++) {
            Entity e = i % 3 == 0 ? new Shulker(level, level.nextEntityId()) : new Boat(level, level.nextEntityId());
            e.moveTo(-6 + r.nextDouble() * 28, -64 + r.nextDouble() * 40, -6 + r.nextDouble() * 28, 0, 0);
            level.addFreshEntity(e);
        }
        cow = new Cow(level, level.nextEntityId());
        rabbit = new Rabbit(level, level.nextEntityId());
        selfBoat = new Boat(level, level.nextEntityId());
    }

    /** 한 번의 질의. who: 0 없음, 1 소, 2 토끼(가루눈 위를 걷는다), 3 배(밀 수 있는 몹과도 부딪힌다). */
    record Query(int who, double ex, double ey, double ez, float fall, AABB box, double dx, double dy, double dz, boolean onGround, float step,
                 double[] border) {
        Entity self() {
            Entity e = switch (who) {
                case 1 -> cow;
                case 2 -> rabbit;
                case 3 -> selfBoat;
                default -> null;
            };
            if (e != null) {
                e.moveTo(ex, ey, ez, 0, 0);
                e.fallDistance = fall;
            }
            level.border = border.clone();
            return e;
        }

        String summary() {
            return "who=" + who + " at=(" + ex + "," + ey + "," + ez + ") fall=" + fall + " box=" + box + " d=(" + dx + "," + dy + "," + dz + ")"
                    + " ground=" + onGround + " step=" + step + " border=" + java.util.Arrays.toString(border);
        }
    }

    private static double coord(SplittableRandom r, double lo, double hi) {
        double v = lo + r.nextDouble() * (hi - lo);
        return switch (r.nextInt(6)) {
            case 0 -> Math.floor(v);                         // 정수 경계
            case 1 -> Math.floor(v) + (r.nextBoolean() ? 1.0E-7 : -1.0E-7);
            case 2 -> Math.floor(v) + r.nextInt(16) / 16.0;  // 1/16 격자(반 블록·계단 높이)
            default -> v;
        };
    }

    private static double move(SplittableRandom r) {
        return switch (r.nextInt(7)) {
            case 0 -> 0.0;
            case 1 -> (r.nextBoolean() ? 1 : -1) * r.nextDouble() * 1.0E-6;
            case 2 -> (r.nextBoolean() ? 1 : -1) * r.nextDouble() * 4.0;
            default -> (r.nextBoolean() ? 1 : -1) * r.nextDouble() * 0.6;
        };
    }

    static Query query(SplittableRandom r) {
        boolean far = r.nextInt(8) == 0;   // 로드된 끝(없는 청크 옆)
        double cx = far ? coord(r, -36, -24) : coord(r, -7, 23), cy = coord(r, -66, -18), cz = far ? coord(r, -7, 7) : coord(r, -7, 23);
        int who = r.nextInt(4);
        double w, h;
        switch (r.nextInt(5)) {
            case 0 -> { w = 0.9; h = 1.4; }                 // 소
            case 1 -> { w = 0.4; h = 0.5; }                 // 토끼
            case 2 -> { w = r.nextDouble() * 3.0; h = r.nextDouble() * 3.0; }
            case 3 -> { w = r.nextDouble() * 2.0E-7; h = r.nextDouble() * 2.0; }   // 아주 얇은 상자
            default -> { w = 0.6; h = 1.8; }
        }
        AABB box = new AABB(cx - w / 2, cy, cz - w / 2, cx + w / 2, cy + h, cz + w / 2);
        if (r.nextInt(4) == 0) box = new AABB(cx, cy, cz, cx + coord(r, 0, 2), cy + coord(r, 0, 2), cz + coord(r, 0, 2));
        double[] border = {-2.9999984E7, -2.9999984E7, 2.9999984E7, 2.9999984E7};
        if (r.nextInt(4) == 0) {   // 질의 근처를 지나는 보더
            double bx = cx + (r.nextDouble() - 0.5) * 6, bz = cz + (r.nextDouble() - 0.5) * 6;
            border = new double[] {bx - 30 * r.nextDouble(), bz - 30 * r.nextDouble(), bx, bz};
        }
        float fall = r.nextInt(3) == 0 ? 3.0F : 0.0F;
        float step = r.nextInt(3) == 0 ? 0.0F : r.nextBoolean() ? 0.6F : 1.0F;
        double ex = (box.minX() + box.maxX()) / 2, ez = (box.minZ() + box.maxZ()) / 2;
        return new Query(who, ex, box.minY() + (r.nextBoolean() ? 0.0 : r.nextDouble()), ez, fall, box, move(r), move(r), move(r), r.nextBoolean(), step, border);
    }

    /** 모양 목록을 비교할 문자열로: 상자 값(-0.0 구분)과 오르기 후보 격자. */
    private static List<String> describe(List<Shape> shapes) {
        List<String> out = new ArrayList<>(shapes.size());
        for (Shape s : shapes) {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < s.boxCount(); i++) for (double v : s.box(i)) b.append(Double.doubleToRawLongBits(v)).append(',');
            b.append('|').append(java.util.Arrays.toString(s.exactYCoords()));
            out.add(b.toString());
        }
        return out;
    }

    private static Differential<Query, Object> diff(String name) {
        return Differential.<Query, Object>of(name, CollisionScanParityTest::query).seed(0x7018_0001L).iterations(20000).describe(Query::summary);
    }

    @Test
    void blockCollisionListsMatchLegacy() {
        Differential<Query, Object> d = diff("t018-block-collisions");
        d.check(q -> describe(LegacyBlockCollisions.blockCollisions(level, q.self(), q.box())),
                q -> describe(level.blockCollisions(q.self(), q.box())));
        // 변이: 순서를 뒤집으면 잡아야 한다(1e-7 조기 반환이 순서에 달려 있다)
        d.assertDetects(q -> describe(LegacyBlockCollisions.blockCollisions(level, q.self(), q.box())),
                q -> describe(reversed(level.blockCollisions(q.self(), q.box()))));
    }

    @Test
    void noBlockCollisionAndSupportingBlockMatchLegacy() {
        diff("t018-no-block-collision").check(q -> LegacyBlockCollisions.noBlockCollision(level, q.self(), q.box()),
                q -> level.noBlockCollision(q.self(), q.box()));
        Differential<Query, Object> d = diff("t018-supporting-block");
        d.check(q -> LegacyBlockCollisions.findSupportingBlock(level, q.self(), q.box(), q.ex(), q.ey(), q.ez()),
                q -> level.findSupportingBlock(q.self(), q.box(), q.ex(), q.ey(), q.ez()));
        // 변이: 거리가 같을 때 고르는 쪽을 바꾸면(Vec3i.compareTo) 잡아야 한다 — 정수 경계 위 엔티티가 만든다
        d.assertDetects(q -> LegacyBlockCollisions.findSupportingBlock(level, q.self(), q.box(), q.ex(), q.ey(), q.ez()),
                q -> firstSupporting(q));
    }

    @Test
    void collideMoveMatchesLegacy() {
        Differential<Query, Object> d = diff("t018-collide-move");
        d.check(q -> LegacyEntityCollide.collideMove(level, q.self(), q.box(), q.onGround(), q.step(), q.dx(), q.dy(), q.dz()),
                q -> Entity.collideMove(level, q.self(), q.box(), q.onGround(), q.step(), q.dx(), q.dy(), q.dz()));
        diff("t018-collide-bounding-box").check(q -> LegacyEntityCollide.collideBoundingBox(level, q.self(), q.box(), q.dx(), q.dy(), q.dz()),
                q -> Entity.collideBoundingBox(level, q.self(), q.box(), q.dx(), q.dy(), q.dz()));
        // 변이: 오르기를 빼면 잡아야 한다
        d.assertDetects(q -> LegacyEntityCollide.collideMove(level, q.self(), q.box(), q.onGround(), q.step(), q.dx(), q.dy(), q.dz()),
                q -> Entity.collideMove(level, q.self(), q.box(), q.onGround(), 0.0F, q.dx(), q.dy(), q.dz()));
    }

    /** 상태별 표가 덮어쓴 동작(칸·문맥 모양)을 고른다 — 움직이는 피스톤·가루눈. 표에 새 동작이 빠지면 여기서 잡는다. */
    @Test
    void contextualBehaviorsAreDynamic() {
        assertTrue(ChunkLevel.CollisionTable.contextual(BlockBehaviors.ofBlock(B.id("moving_piston")).getClass()));
        assertTrue(ChunkLevel.CollisionTable.contextual(BlockBehaviors.ofBlock(B.id("powder_snow")).getClass()));
        assertFalse(ChunkLevel.CollisionTable.contextual(BlockBehaviors.ofBlock(B.id("oak_stairs")).getClass()));
        Blocks blocks = Blocks.get();
        int dynamic = 0;
        for (int b = 0; b < blocks.blockCount(); b++) if (ChunkLevel.CollisionTable.contextual(BlockBehaviors.ofBlock(b).getClass())) dynamic++;
        assertEquals(2, dynamic, "덮어쓴 동작이 늘었다 — 이 시험과 SHAPED 목록에 넣는다");
    }

    /** 생성기가 볼 만한 경우를 실제로 만드는지(빈 시험 막기): 충돌 있음·문맥 블록·엔티티 몸·보더·오르기·가장자리 청크. */
    @Test
    void generatorCoversInterestingCases() {
        int piston = B.id("moving_piston"), snow = B.id("powder_snow");
        int n = 20000, nonEmpty = 0, dynamicHits = 0, entityShapes = 0, borders = 0, stepped = 0, blocked = 0;
        for (int k = 0; k < n; k++) {
            Query q = query(Differential.randomFor(0x7018_0001L, k));
            Entity self = q.self();
            int[] dyn = {0};
            boolean[] any = {false};
            level.scanCollisions(q.box(), self, (x, y, z, sh) -> {
                any[0] = true;
                int b = Blocks.get().blockOf(level.getBlockState(new Pos(x, y, z)));
                if (b == piston || b == snow) dyn[0]++;
                return true;
            });
            if (any[0]) nonEmpty++;
            if (dyn[0] > 0) dynamicHits++;
            if (!level.entityCollisions(self, q.box().expandTowards(q.dx(), q.dy(), q.dz())).isEmpty()) entityShapes++;
            if (self != null && level.borderCollision(self.x(), self.z(), q.box()) != null) borders++;
            double[] with = Entity.collideMove(level, self, q.box(), q.onGround(), q.step(), q.dx(), q.dy(), q.dz());
            double[] without = Entity.collideMove(level, self, q.box(), q.onGround(), 0.0F, q.dx(), q.dy(), q.dz());
            if (!java.util.Arrays.equals(with, without)) stepped++;
            if (with[0] != q.dx() || with[1] != q.dy() || with[2] != q.dz()) blocked++;
        }
        System.out.printf("T018 coverage n=%d nonEmpty=%d dynamic=%d entityShapes=%d border=%d stepped=%d blocked=%d%n",
                n, nonEmpty, dynamicHits, entityShapes, borders, stepped, blocked);
        assertTrue(nonEmpty > n / 4 && dynamicHits > 50 && entityShapes > 50 && borders > 50 && stepped > 50 && blocked > n / 10,
                "생성기가 경우를 고루 만들지 못한다");
    }

    private static Object firstSupporting(Query q) {
        Pos[] first = {null};
        level.scanCollisions(q.box(), q.self(), (x, y, z, s) -> {
            if (first[0] == null) first[0] = new Pos(x, y, z);
            return true;
        });
        return first[0];
    }

    private static <T> List<T> reversed(List<T> l) {
        List<T> c = new ArrayList<>(l);
        java.util.Collections.reverse(c);
        return c;
    }
}
