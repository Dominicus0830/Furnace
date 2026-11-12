package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.spawner.SpawnRule;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.BlockVector;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

/**
 * A15-01·A15-06 후속(서버 없이): Vector·Location 수학 멤버, BlockVector·BoundingBox·SpawnRule 의 동작과 설정 저장·되읽기.
 * 기대값은 Paper 1.21.1 본문의 식을 손으로 풀어 옮겼다(코드를 그대로 되돌려 부르지 않는다).
 */
class A15ValueTypesParityTest {

    private static final double EPS = 1e-9;

    private static void assertVec(double x, double y, double z, Vector v) {
        assertEquals(x, v.getX(), EPS, () -> "x: " + v);
        assertEquals(y, v.getY(), EPS, () -> "y: " + v);
        assertEquals(z, v.getZ(), EPS, () -> "z: " + v);
    }

    // ------------------------------------------------------------------ Vector

    @Test
    void rotationsFollowPaperFormulas() {
        // rotateAroundY: x' = cos·x + sin·z, z' = -sin·x + cos·z → (1,0,0) 을 90° 돌리면 (0,0,-1)
        assertVec(0, 0, -1, new Vector(1, 0, 0).rotateAroundY(Math.PI / 2));
        // rotateAroundX: y' = cos·y - sin·z, z' = sin·y + cos·z → (0,1,0) → (0,0,1)
        assertVec(0, 0, 1, new Vector(0, 1, 0).rotateAroundX(Math.PI / 2));
        // rotateAroundZ: x' = cos·x - sin·y, y' = sin·x + cos·y → (1,0,0) → (0,1,0)
        assertVec(0, 1, 0, new Vector(1, 0, 0).rotateAroundZ(Math.PI / 2));
        // 단위 축 둘레: Y 축이면 rotateAroundY 와 같다
        assertVec(0, 0, -1, new Vector(1, 0, 0).rotateAroundAxis(new Vector(0, 1, 0), Math.PI / 2));
        // 단위가 아닌 축은 정규화한 복사본으로 돌리고 원래 축은 그대로
        Vector axis = new Vector(0, 5, 0);
        assertVec(0, 0, -1, new Vector(1, 0, 0).rotateAroundAxis(axis, Math.PI / 2));
        assertVec(0, 5, 0, axis);
        // NonUnit 은 정규화하지 않는다: 길이 2 축이면 결과가 틀리다(Paper 와 같다) — 성분 y' 는 y2·dot·(1-cos) = 2·0·… = 0, z' = -y2·x·sin = -2
        assertVec(0, 0, -2, new Vector(1, 0, 0).rotateAroundNonUnitAxis(new Vector(0, 2, 0), Math.PI / 2));
        assertThrows(IllegalArgumentException.class, () -> new Vector(1, 0, 0).rotateAroundAxis(null, 1));
    }

    @Test
    void geometryHelpers() {
        assertVec(0, 0, 1, new Vector(1, 0, 0).getCrossProduct(new Vector(0, 1, 0)));
        Vector a = new Vector(1, 0, 0);
        a.getCrossProduct(new Vector(0, 1, 0));
        assertVec(1, 0, 0, a);   // getCrossProduct 는 자기를 바꾸지 않는다
        assertEquals(Math.PI / 2, new Vector(1, 0, 0).angle(new Vector(0, 1, 0)), 1e-6);
        assertEquals(0f, new Vector(1, 0, 0).angle(new Vector(2, 0, 0)), 1e-6);   // 코사인이 1 을 넘어도 잘린다
        assertVec(1.5, 2, 3, new Vector(1, 0, 0).getMidpoint(new Vector(2, 4, 6)));
        assertVec(1, 2, 3, new Vector(0, 0, 0).midpoint(new Vector(2, 4, 6)));
        assertTrue(new Vector(3, 4, 0).isInSphere(new Vector(0, 0, 0), 5), "경계(9+16 = 25)는 포함");
        assertFalse(new Vector(3, 4, 0).isInSphere(new Vector(0, 0, 0), 4.9));
        assertTrue(new Vector(1, 1, 1).isInAABB(new Vector(0, 0, 0), new Vector(1, 1, 1)), "경계는 포함");
        assertFalse(new Vector(1, 1, 1.01).isInAABB(new Vector(0, 0, 0), new Vector(1, 1, 1)));
        assertTrue(new Vector(0, 0, 1).isNormalized());
        assertFalse(new Vector(0, 0, 1.01).isNormalized());
        assertTrue(new Vector().isZero());
        assertFalse(new Vector(0, 0, 1e-12).isZero());
        assertVec(1, 2, 3, Vector.getMinimum(new Vector(1, 5, 3), new Vector(4, 2, 9)));
        assertVec(4, 5, 9, Vector.getMaximum(new Vector(1, 5, 3), new Vector(4, 2, 9)));
        assertEquals(0.000001, Vector.getEpsilon());
        assertVec(0.5, 1, 2, new Vector(1, 2, 4).divide(new Vector(2, 2, 2)));
        assertVec(7, 8, 9, new Vector().copy(new Vector(7, 8, 9)));
        assertVec(2, 4, 6, new Vector(1, 2, 3).multiply(2));       // int
        assertVec(0.5, 1, 1.5, new Vector(1, 2, 3).multiply(0.5f)); // float
        assertVec(3, 2, 1, new Vector(0, 0, 0).setX(3).setY(2.0f).setZ(1.0));
        assertThrows(IllegalArgumentException.class, () -> new Vector(Double.NaN, 0, 0).checkFinite());
        assertThrows(IllegalArgumentException.class, () -> new Vector(0, Double.POSITIVE_INFINITY, 0).checkFinite());
        Vector r = Vector.getRandom();
        assertTrue(r.getX() >= 0 && r.getX() < 1 && r.getY() >= 0 && r.getY() < 1 && r.getZ() >= 0 && r.getZ() < 1);
    }

    @Test
    void vectorEqualsRequiresSameClass() {
        assertEquals(new Vector(1, 2, 3), new Vector(1, 2, 3 + 1e-7), "epsilon 안");
        assertNotEquals(new Vector(1, 2, 3), new Vector(1, 2, 3 + 1e-5));
        assertNotEquals(new Vector(1, 2, 3), new BlockVector(1, 2, 3), "Paper: 클래스가 같아야 같다");
        assertNotEquals(new BlockVector(1, 2, 3), new Vector(1, 2, 3));
        assertInstanceOf(BlockVector.class, new Vector(1.5, 2, 3).toBlockVector());
    }

    @Test
    void blockVectorComparesTheIntegerPart() {
        assertEquals(new BlockVector(1.9, 2.1, 3), new BlockVector(1, 2, 3));
        assertEquals(new BlockVector(1.9, 2.1, 3).hashCode(), new BlockVector(1, 2, 3).hashCode());
        assertNotEquals(new BlockVector(1, 2, 3), new BlockVector(1, 2, 4));
        assertInstanceOf(BlockVector.class, new BlockVector(1, 2, 3).clone());
    }

    // ------------------------------------------------------------------ Location

    @Test
    void locationMathMatchesPaper() {
        Location l = new Location(null, 0, 0, 0);
        assertEquals(0f, l.setDirection(new Vector(0, 0, 1)).getYaw(), 1e-4);
        assertEquals(0f, l.getPitch(), 1e-4);
        l.setDirection(new Vector(1, 0, 0));
        assertEquals(270f, l.getYaw(), 1e-3, "+X 를 보면 yaw 270(= -90)");
        l.setDirection(new Vector(0, 0, -1));
        assertEquals(180f, l.getYaw(), 1e-3);
        l.setDirection(new Vector(0, 1, 0));
        assertEquals(-90f, l.getPitch(), "위를 보면 pitch -90, yaw 는 그대로");
        assertEquals(180f, l.getYaw(), 1e-3);
        l.setDirection(new Vector(0, -1, 0));
        assertEquals(90f, l.getPitch());
        l.setDirection(new Vector(1, -1, 0));   // 아래로 45°
        assertEquals(45f, l.getPitch(), 1e-3);

        assertEquals(-170f, Location.normalizeYaw(190f));
        assertEquals(170f, Location.normalizeYaw(-190f));
        assertEquals(-180f, Location.normalizeYaw(180f));
        assertEquals(0f, Location.normalizeYaw(720f));
        assertEquals(90f, Location.normalizePitch(100f));
        assertEquals(-90f, Location.normalizePitch(-100f));
        assertEquals(12.5f, Location.normalizePitch(12.5f));
    }

    @Test
    void locationCopiesAndAccessors() {
        Location l = new Location(null, 1.7, -2.2, 3.9, 30f, 10f);
        Location b = l.toBlockLocation();
        assertEquals(1.0, b.getX());
        assertEquals(-3.0, b.getY());
        assertEquals(3.0, b.getZ());
        assertEquals(30f, b.getYaw());
        assertEquals(1.7, l.getX(), "원본은 그대로");
        Location c = l.toCenterLocation();
        assertEquals(1.5, c.getX());
        assertEquals(-2.5, c.getY());
        assertEquals(3.5, c.getZ());
        assertEquals(10f, c.getPitch());
        assertEquals(l.getX(), l.x());
        assertEquals(l.getY(), l.y());
        assertEquals(l.getZ(), l.z());
        assertTrue(l.isFinite());
        assertFalse(new Location(null, 0, 0, 0, Float.NaN, 0).isFinite(), "yaw 도 본다");
        assertThrows(IllegalArgumentException.class, () -> new Location(null, 0, 0, 0, 0, Float.POSITIVE_INFINITY).checkFinite());
        assertFalse(l.isWorldLoaded(), "월드가 없으면 거짓");
        assertSame(l, l.set(5, 6, 7));
        assertEquals(6.0, l.getY());
        l.add(new Location(null, 1, 1, 1), 1, 2, 3);   // base + (1,2,3) 으로 정한다(자기 값에 더하지 않는다)
        assertEquals(2.0, l.getX());
        assertEquals(3.0, l.getY());
        assertEquals(4.0, l.getZ());
        l.subtract(new Location(null, 10, 10, 10), 1, 2, 3);
        assertEquals(9.0, l.getX());
        assertEquals(8.0, l.getY());
        assertEquals(7.0, l.getZ());
        l.multiply(2).zero();
        assertEquals(0.0, l.getX() + l.getY() + l.getZ());
        assertEquals(new Vector(0, 0, 0), l.toVector());
        Location w = new Location(null, 1, 2, 3, 4f, 5f).toLocation(null);
        assertEquals(4f, w.getYaw());
    }

    @Test
    void locationOperationsAcrossWorldsFail() {
        Location a = new Location(null, 1, 1, 1);
        assertEquals("Cannot measure distance to a null world", assertThrows(IllegalArgumentException.class, () -> a.distance(new Location(null, 0, 0, 0))).getMessage());
        assertEquals("Cannot measure distance to a null location", assertThrows(IllegalArgumentException.class, () -> a.distance(null)).getMessage());
        assertEquals("Cannot add Locations of differing worlds", assertThrows(IllegalArgumentException.class, () -> a.add((Location) null)).getMessage());
        assertEquals("Cannot add Locations of differing worlds", assertThrows(IllegalArgumentException.class, () -> a.subtract((Location) null)).getMessage());
        assertEquals("Location has no world", assertThrows(IllegalArgumentException.class, () -> a.getNearbyPlayers(5)).getMessage());
        assertEquals("Location has no world", assertThrows(IllegalArgumentException.class, () -> a.getNearbyEntities(1, 1, 1)).getMessage());
        assertEquals("Location has no world", assertThrows(IllegalArgumentException.class, () -> a.getNearbyLivingEntities(1, 2, 3)).getMessage());
        assertThrows(NullPointerException.class, a::asString, "Paper: 월드가 없으면 NullPointerException");
        // 같은 월드(없음) 끼리의 덧셈·뺄셈은 된다
        assertEquals(2.0, new Location(null, 1, 1, 1).add(new Location(null, 1, 0, 0)).getX());
        assertEquals(0.0, new Location(null, 1, 1, 1).subtract(new Location(null, 1, 0, 0)).getX());
    }

    @Test
    void locationIsAFinePositionAndNeedsAWorldForBlockAndChunk() {
        Location l = new Location(null, 1.5, 64.9, -0.5);
        io.papermc.paper.math.Position p = l;
        assertTrue(p.isFine());
        assertFalse(p.isBlock());
        assertEquals(1, p.blockX());
        assertEquals(64, p.blockY());
        assertEquals(-1, p.blockZ());
        assertEquals(io.papermc.paper.math.Position.block(1, 64, -1), p.toBlock());
        assertEquals(io.papermc.paper.math.Position.fine(1.5, 64.5, -0.5), p.toCenter());
        assertEquals(io.papermc.paper.math.Position.fine(2.5, 65.9, 0.5), l.offset(1.0, 1.0, 1.0));
        io.papermc.paper.math.BlockPosition b = io.papermc.paper.math.Position.block(1, 2, 3);
        assertTrue(b.isBlock());
        assertFalse(b.isFine());
        assertEquals(io.papermc.paper.math.Position.block(2, 2, 2), b.offset(1, 0, -1));
        assertEquals(1.0, b.x());
        assertEquals(io.papermc.paper.math.Position.fine(1.5, 2.5, 3.5), b.toCenter());
        assertThrows(NullPointerException.class, l::getChunk);
        assertThrows(NullPointerException.class, l::getBlock);
    }

    @Test
    void blockKeyPacksCoordinates() {
        long key = Block.getBlockKey(1, 2, 3);
        assertEquals(1L | (3L << 27) | (2L << 54), key);
        for (int[] c : new int[][] {{1, 2, 3}, {-5, -3, -7}, {29999999, 319, -29999999}, {-29999999, -64, 29999999}, {0, 0, 0}}) {
            long k = Block.getBlockKey(c[0], c[1], c[2]);
            assertEquals(c[0], Block.getBlockKeyX(k));
            assertEquals(c[1], Block.getBlockKeyY(k));
            assertEquals(c[2], Block.getBlockKeyZ(k));
        }
        assertEquals(Block.getBlockKey(1, 2, 3), new Location(null, 1.5, 2.5, 3.5).toBlockKey());
    }

    // ------------------------------------------------------------------ BoundingBox

    @Test
    void boundingBoxNormalisesAndMeasures() {
        BoundingBox b = BoundingBox.of(new Vector(4, 1, 6), new Vector(0, 3, 2));
        assertEquals(new Vector(0, 1, 2), b.getMin());
        assertEquals(new Vector(4, 3, 6), b.getMax());
        assertEquals(4.0, b.getWidthX());
        assertEquals(2.0, b.getHeight());
        assertEquals(4.0, b.getWidthZ());
        assertEquals(32.0, b.getVolume());
        assertEquals(new Vector(2, 2, 4), b.getCenter());
        assertEquals(new BoundingBox(0, 1, 2, 4, 3, 6), BoundingBox.of(new Vector(2, 2, 4), 2, 1, 2));
        assertEquals(new BoundingBox(), new BoundingBox(0, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new BoundingBox(Double.NaN, 0, 0, 1, 1, 1));
        assertEquals("Corner1 is null!", assertThrows(IllegalArgumentException.class, () -> BoundingBox.of((Vector) null, new Vector())).getMessage());
        assertEquals("BoundingBox [minX=0.0, minY=1.0, minZ=2.0, maxX=4.0, maxY=3.0, maxZ=6.0]", b.toString());
    }

    @Test
    void boundingBoxExpandAndShrink() {
        BoundingBox b = new BoundingBox(0, 0, 0, 2, 2, 2);
        assertEquals(new BoundingBox(-1, -1, -1, 3, 3, 3), b.clone().expand(1));
        assertEquals(new BoundingBox(-1, 0, 0, 2, 2, 2), b.clone().expand(1, 0, 0, 0, 0, 0));
        // 줄이다가 서로 지나치면 가운데에서 멈춘다
        assertEquals(new BoundingBox(1, 1, 1, 1, 1, 1), b.clone().expand(-5));
        // 방향 확장: 방향의 부호 쪽 면만 넓힌다
        assertEquals(new BoundingBox(0, 0, 0, 5, 2, 2), b.clone().expandDirectional(3, 0, 0));
        assertEquals(new BoundingBox(-3, 0, 0, 2, 2, 2), b.clone().expandDirectional(-3, 0, 0));
        assertEquals(new BoundingBox(0, 0, 0, 2, 2, 2), b.clone().expand(BlockFace.SELF, 5), "SELF 는 아무것도 안 한다");
        assertEquals(new BoundingBox(0, 0, 0, 2, 2, 4), b.clone().expand(BlockFace.SOUTH, 2));
        BoundingBox same = b.clone();
        assertSame(same, same.expand(0, 0, 0), "넓힐 것이 없으면 자기 자신");
    }

    @Test
    void boundingBoxSetOperations() {
        BoundingBox a = new BoundingBox(0, 0, 0, 2, 2, 2);
        BoundingBox touching = new BoundingBox(2, 0, 0, 4, 2, 2);
        BoundingBox over = new BoundingBox(1, 1, 1, 3, 3, 3);
        assertFalse(a.overlaps(touching), "맞닿기만 하면 겹침이 아니다");
        assertTrue(a.overlaps(over));
        assertTrue(a.overlaps(new Vector(1, 1, 1), new Vector(5, 5, 5)));
        assertTrue(a.contains(0, 0, 0));
        assertFalse(a.contains(2, 1, 1), "최댓값은 제외");
        assertTrue(a.contains(new BoundingBox(0, 0, 0, 2, 2, 2)), "같은 상자는 품는다");
        assertFalse(a.contains(over));
        assertTrue(a.contains(new Vector(2, 2, 2), new Vector(0, 0, 0)), "꼭짓점 순서는 상관없다");
        assertEquals(new BoundingBox(1, 1, 1, 2, 2, 2), a.clone().intersection(over));
        assertEquals("The bounding boxes do not overlap!", assertThrows(IllegalArgumentException.class, () -> a.clone().intersection(touching)).getMessage());
        assertEquals(new BoundingBox(0, 0, 0, 3, 3, 3), a.clone().union(over));
        assertEquals(new BoundingBox(0, 0, 0, 5, 2, 2), a.clone().union(5, 1, 1));
        BoundingBox inner = new BoundingBox(0.5, 0.5, 0.5, 1, 1, 1);
        assertSame(a, a.union(inner), "이미 품고 있으면 자기 자신");
        assertEquals(new BoundingBox(1, 2, 3, 3, 4, 5), a.clone().shift(1, 2, 3));
        BoundingBox c = new BoundingBox(1, 1, 1, 2, 2, 2);
        assertEquals(c, new BoundingBox().copy(c));
    }

    @Test
    void boundingBoxRayTrace() {
        BoundingBox b = new BoundingBox(0, 0, 0, 1, 1, 1);
        RayTraceResult r = b.rayTrace(new Vector(-1, 0.5, 0.5), new Vector(1, 0, 0), 10);
        assertEquals(BlockFace.WEST, r.getHitBlockFace());
        assertVec(0, 0.5, 0.5, r.getHitPosition());
        // 안에서 쏘면 나가는 곳
        r = b.rayTrace(new Vector(0.5, 0.5, 0.5), new Vector(1, 0, 0), 10);
        assertEquals(BlockFace.EAST, r.getHitBlockFace());
        assertVec(1, 0.5, 0.5, r.getHitPosition());
        r = b.rayTrace(new Vector(0.5, 3, 0.5), new Vector(0, -2, 0), 10);
        assertEquals(BlockFace.UP, r.getHitBlockFace());
        assertVec(0.5, 1, 0.5, r.getHitPosition());
        r = b.rayTrace(new Vector(0.5, 0.5, 5), new Vector(0, 0, -1), 10);
        assertEquals(BlockFace.SOUTH, r.getHitBlockFace());
        assertNull(b.rayTrace(new Vector(-1, 0.5, 0.5), new Vector(1, 0, 0), 0.5), "너무 멀다");
        assertNull(b.rayTrace(new Vector(2, 0.5, 0.5), new Vector(1, 0, 0), 10), "등 뒤");
        assertNull(b.rayTrace(new Vector(-1, 2, 0.5), new Vector(1, 0, 0), 10), "빗나감");
        assertNull(b.rayTrace(new Vector(-1, 0.5, 0.5), new Vector(1, 0, 0), -1), "음수 거리");
        assertEquals("Direction's magnitude is 0!", assertThrows(IllegalArgumentException.class, () -> b.rayTrace(new Vector(), new Vector(), 1)).getMessage());
    }

    // ------------------------------------------------------------------ 설정 저장·되읽기

    @Test
    void valueClassesRoundTripThroughYaml() throws Exception {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("bv", new BlockVector(1, 2, 3));
        cfg.set("box", new BoundingBox(0, 1, 2, 3, 4, 5));
        cfg.set("rule", new SpawnRule(1, 7, 2, 9));
        String yaml = cfg.saveToString();
        assertTrue(yaml.matches("(?s).*'?=='?: BlockVector\n.*"), () -> "별칭 BlockVector. 실제:\n" + yaml);
        assertTrue(yaml.matches("(?s).*'?=='?: BoundingBox\n.*"), () -> "별칭 BoundingBox. 실제:\n" + yaml);
        assertTrue(yaml.matches("(?s).*'?=='?: SpawnRule\n.*"), () -> "별칭 SpawnRule. 실제:\n" + yaml);
        YamlConfiguration back = new YamlConfiguration();
        back.loadFromString(yaml);
        assertEquals(new BlockVector(1, 2, 3), back.get("bv"));
        assertInstanceOf(BlockVector.class, back.getVector("bv"));
        assertEquals(new BoundingBox(0, 1, 2, 3, 4, 5), back.get("box"));
        assertEquals(new SpawnRule(1, 7, 2, 9), back.get("rule"));
        // Paper 가 쓴 꼴(SpawnRule 은 block-light·sky-light 맵)도 읽는다
        back.loadFromString("""
                r:
                  ==: SpawnRule
                  block-light:
                    min: 0
                    max: 7
                  sky-light:
                    min: 4
                    max: 15
                """);
        assertEquals(new SpawnRule(0, 7, 4, 15), back.get("r"));
    }

    @Test
    void spawnRuleValidatesLikePaper() {
        assertEquals("minBlockLight must be <= maxBlockLight (5 <= 3)", assertThrows(IllegalArgumentException.class, () -> new SpawnRule(5, 3, 0, 0)).getMessage());
        assertEquals("minSkyLight must be <= maxSkyLight (5 <= 3)", assertThrows(IllegalArgumentException.class, () -> new SpawnRule(0, 0, 5, 3)).getMessage());
        assertEquals("minBlockLight must be >= 0 (given -1)", assertThrows(IllegalArgumentException.class, () -> new SpawnRule(-1, 3, 0, 0)).getMessage());
        SpawnRule r = new SpawnRule(1, 7, 2, 9);
        assertEquals("minBlockLight must be <= maxBlockLight (8 <= 7)", assertThrows(IllegalArgumentException.class, () -> r.setMinBlockLight(8)).getMessage());
        r.setMaxBlockLight(0);   // Paper 는 setMax 에서 최솟값과의 순서를 보지 않는다
        assertEquals(0, r.getMaxBlockLight());
        assertEquals(r, r.clone());
    }
}
