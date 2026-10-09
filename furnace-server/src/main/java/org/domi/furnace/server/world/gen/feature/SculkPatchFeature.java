// 바닐라 SculkPatchFeature + SculkSpreader(월드젠 설정) + SculkBehaviour(기본·SculkBlock·SculkVeinBlock) 이식.
// 커서 이동 후보는 NON_CORNER_NEIGHBOURS(18칸, x→y→z 순) 를 매번 Util.shuffledCopy 로 섞는다. 월드젠 스프레더는 커서를 합치지
// 않으므로(isWorldGeneration) 갱신 후 남은 커서 순서가 그대로 유지된다. 소리·레벨 이벤트는 관측 결과가 없어 생략.
package org.domi.furnace.server.world.gen.feature;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class SculkPatchFeature implements ConfiguredFeature {

    private static final int DOWN = 0, UP = 1;
    private static final int[] DX = MultifaceSpreader.DX, DY = MultifaceSpreader.DY, DZ = MultifaceSpreader.DZ;

    /** SculkSpreader.createWorldGenSpreader(): growthSpawnCost 50, noGrowthRadius 1, chargeDecayRate 5, additionalDecayRate 10. */
    private static final int GROWTH_SPAWN_COST = 50, NO_GROWTH_RADIUS = 1, CHARGE_DECAY_RATE = 5, ADDITIONAL_DECAY_RATE = 10;
    private static final int MAX_CURSORS = 32, MAX_CHARGE = 1000;

    private static final int[][] NON_CORNER_NEIGHBOURS;

    static {
        List<int[]> l = new ArrayList<>(18);
        for (int z = -1; z <= 1; z++)
            for (int y = -1; y <= 1; y++)
                for (int x = -1; x <= 1; x++)
                    if ((x == 0 || y == 0 || z == 0) && !(x == 0 && y == 0 && z == 0)) l.add(new int[]{x, y, z});
        NON_CORNER_NEIGHBOURS = l.toArray(new int[0][]);
    }

    /** 레지스트리 기반 블록 표(피처 인스턴스 간 공유). */
    public static final class Blocks {
        final MultifaceSpreader.Env env;
        final MultifaceSpreader veinSpreader;       // SculkVeinBlock.veinSpreader (모든 퍼짐 유형)
        final MultifaceSpreader sameSpaceSpreader;  // SculkVeinBlock.sameSpaceSpreader (SAME_POSITION)
        final boolean[] replaceableWorldGen, replaceable;
        final int catalyst, sensor, sensorWaterlogged, shriekerSummon, shriekerSummonWaterlogged;
        final boolean[] sensorOrShrieker;
        final int air, water;

        public Blocks(MultifaceSpreader.Env env) {
            this.env = env;
            var r = env.registry;
            this.veinSpreader = new MultifaceSpreader(env, env.sculkVeinBase, true, MultifaceSpreader.ALL_SPREAD_TYPES);
            this.sameSpaceSpreader = new MultifaceSpreader(env, env.sculkVeinBase, true, new int[]{MultifaceSpreader.SAME_POSITION});
            int n = r.totalStateCount();
            this.replaceableWorldGen = MultifaceSpreader.Env.flags(r, n, BlockTags.get().blocksOf("minecraft:sculk_replaceable_world_gen").toArray(new String[0]));
            this.replaceable = MultifaceSpreader.Env.flags(r, n, BlockTags.get().blocksOf("minecraft:sculk_replaceable").toArray(new String[0]));
            this.catalyst = r.stateIdOrAir("minecraft:sculk_catalyst");
            this.sensor = exact(r, "minecraft:sculk_sensor;power=0,sculk_sensor_phase=inactive,waterlogged=false");
            this.sensorWaterlogged = exact(r, "minecraft:sculk_sensor;power=0,sculk_sensor_phase=inactive,waterlogged=true");
            this.shriekerSummon = exact(r, "minecraft:sculk_shrieker;can_summon=true,shrieking=false,waterlogged=false");
            this.shriekerSummonWaterlogged = exact(r, "minecraft:sculk_shrieker;can_summon=true,shrieking=false,waterlogged=true");
            this.sensorOrShrieker = MultifaceSpreader.Env.flags(r, n, "minecraft:sculk_sensor", "minecraft:sculk_shrieker");
            this.air = r.stateIdOrAir("minecraft:air");
            this.water = r.stateIdOrAir("minecraft:water");
        }

        private static int exact(org.domi.furnace.server.world.BlockRegistry r, String key) {
            int id = r.exactStateId(key);
            if (id < 0) throw new IllegalStateException("블록 상태 없음: " + key);
            return id;
        }

        boolean isSculk(int s) { return s == env.sculk; }

        boolean isVein(int s) { return veinSpreader.is(s); }
    }

    private final Blocks b;
    private final int chargeCount, amountPerCharge, spreadAttempts, growthRounds, spreadRounds;
    private final TreeIntProvider extraRareGrowths;
    private final float catalystChance;

    public SculkPatchFeature(Blocks blocks, int chargeCount, int amountPerCharge, int spreadAttempts, int growthRounds, int spreadRounds,
                             TreeIntProvider extraRareGrowths, float catalystChance) {
        this.b = blocks;
        this.chargeCount = chargeCount;
        this.amountPerCharge = amountPerCharge;
        this.spreadAttempts = spreadAttempts;
        this.growthRounds = growthRounds;
        this.spreadRounds = spreadRounds;
        this.extraRareGrowths = extraRareGrowths;
        this.catalystChance = catalystChance;
    }

    @Override
    public boolean place(FeatureWorldView v, WorldgenRandom random, int x, int y, int z) {
        if (!canSpreadFrom(v, x, y, z)) return false;
        List<Cursor> cursors = new ArrayList<>();
        int rounds = spreadRounds + growthRounds;
        for (int r = 0; r < rounds; r++) {
            for (int i = 0; i < chargeCount; i++) addCursors(cursors, x, y, z, amountPerCharge);
            boolean convert = r < spreadRounds;
            for (int i = 0; i < spreadAttempts; i++) cursors = updateCursors(v, cursors, x, y, z, random, convert);
            cursors.clear();
        }
        if (random.nextFloat() <= catalystChance && b.env.isCollisionShapeFullBlock(v.getBlock(x, y - 1, z))) {
            v.trySetBlock(x, y, z, b.catalyst);
        }
        int extra = extraRareGrowths.sample(random);
        for (int i = 0; i < extra; i++) {
            int px = x + random.nextInt(5) - 2;
            int pz = z + random.nextInt(5) - 2;
            if (b.env.attrs.isAir(v.getBlock(px, y, pz)) && b.env.attrs.isFaceSturdy(v.getBlock(px, y - 1, pz), UP)) {
                v.trySetBlock(px, y, pz, b.shriekerSummon);
            }
        }
        return true;
    }

    private boolean canSpreadFrom(FeatureWorldView v, int x, int y, int z) {
        int s = v.getBlock(x, y, z);
        if (b.isSculk(s) || b.isVein(s)) return true;
        if (!b.env.attrs.isAir(s) && !b.env.isWaterSourceBlock(s)) return false;
        for (int d = 0; d < 6; d++) {
            if (b.env.isCollisionShapeFullBlock(v.getBlock(x + DX[d], y + DY[d], z + DZ[d]))) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ SculkSpreader

    private static final class Cursor {
        int x, y, z;
        int charge;
        int updateDelay;     // 0
        int decayDelay = 1;
        int facings = -1;    // -1 = null, 그 외 Direction 비트 마스크

        Cursor(int x, int y, int z, int charge) { this.x = x; this.y = y; this.z = z; this.charge = charge; }
    }

    private static void addCursors(List<Cursor> cursors, int x, int y, int z, int charge) {
        while (charge > 0) {
            int c = Math.min(charge, MAX_CHARGE);
            if (cursors.size() < MAX_CURSORS) cursors.add(new Cursor(x, y, z, c));
            charge -= c;
        }
    }

    private List<Cursor> updateCursors(FeatureWorldView v, List<Cursor> cursors, int ox, int oy, int oz, WorldgenRandom random, boolean convert) {
        if (cursors.isEmpty()) return cursors;
        List<Cursor> next = new ArrayList<>(cursors.size());
        for (Cursor c : cursors) {
            update(v, c, ox, oy, oz, random, convert);
            if (c.charge > 0) next.add(c);   // 월드젠: 같은 칸 커서도 합치지 않는다
        }
        return next;
    }

    private static final int DEFAULT = 0, SCULK = 1, VEIN = 2;

    private int behaviour(int s) { return b.isSculk(s) ? SCULK : b.isVein(s) ? VEIN : DEFAULT; }

    /** SculkSpreader.ChargeCursor.update. */
    private void update(FeatureWorldView v, Cursor c, int ox, int oy, int oz, WorldgenRandom random, boolean convert) {
        if (c.charge <= 0) return;
        if (c.updateDelay > 0) { c.updateDelay--; return; }
        int st = v.getBlock(c.x, c.y, c.z);
        int beh = behaviour(st);
        if (convert && attemptSpreadVein(v, beh, c.x, c.y, c.z, st, c.facings)) {
            if (beh != SCULK) {   // canChangeBlockStateOnSpread
                st = v.getBlock(c.x, c.y, c.z);
                beh = behaviour(st);
            }
        }
        c.charge = attemptUseCharge(v, beh, c, ox, oy, oz, random, convert);
        if (c.charge <= 0) {
            onDischarged(v, beh, st, c.x, c.y, c.z);
            return;
        }
        int[] np = getValidMovementPos(v, c.x, c.y, c.z, random);
        if (np != null) {
            onDischarged(v, beh, st, c.x, c.y, c.z);
            c.x = np[0]; c.y = np[1]; c.z = np[2];
            long dx = c.x - ox, dz = c.z - oz;
            if (!(dx * dx + dz * dz < 225)) { c.charge = 0; return; }   // closerThan((ox, y, oz), 15)
            st = v.getBlock(c.x, c.y, c.z);
        }
        if (b.isSculk(st) || b.isVein(st)) c.facings = b.veinSpreader.faceMask(st);
        c.decayDelay = beh == DEFAULT ? Math.max(c.decayDelay - 1, 0) : 1;
        c.updateDelay = 1;   // getSpreadDelay
    }

    private boolean attemptSpreadVein(FeatureWorldView v, int beh, int x, int y, int z, int st, int facings) {
        if (beh == DEFAULT) {
            if (facings == -1) return b.sameSpaceSpreader.spreadAll(v, v.getBlock(x, y, z), x, y, z, true) > 0;
            if (facings != 0) {
                if (!b.env.attrs.isAir(st) && b.env.fluids.of(st) != FluidKind.WATER) return false;
                return regrow(v, x, y, z, st, facings);
            }
        }
        return b.veinSpreader.spreadAll(v, st, x, y, z, true) > 0;
    }

    /** SculkVeinBlock.regrow. */
    private boolean regrow(FeatureWorldView v, int x, int y, int z, int st, int facings) {
        boolean any = false;
        int s = b.env.sculkVeinBase;
        for (int d = 0; d < 6; d++) {
            if ((facings & (1 << d)) == 0) continue;
            if (b.env.canAttachTo(v.getBlock(x + DX[d], y + DY[d], z + DZ[d]), d)) {
                s = b.veinSpreader.withFace(s, d, true);
                any = true;
            }
        }
        if (!any) return false;
        if (b.env.fluids.of(st) != FluidKind.EMPTY) s = s - 2;   // waterlogged=true
        v.trySetBlock(x, y, z, s);
        return true;
    }

    private int attemptUseCharge(FeatureWorldView v, int beh, Cursor c, int ox, int oy, int oz, WorldgenRandom random, boolean convert) {
        switch (beh) {
            case SCULK: {
                int charge = c.charge;
                if (charge == 0 || random.nextInt(CHARGE_DECAY_RATE) != 0) return charge;
                long ddx = c.x - ox, ddy = c.y - oy, ddz = c.z - oz;
                double distSqr = ddx * ddx + ddy * ddy + ddz * ddz;
                boolean closeToCatalyst = distSqr < (double) NO_GROWTH_RADIUS * NO_GROWTH_RADIUS;
                if (!closeToCatalyst && canPlaceGrowth(v, c.x, c.y, c.z)) {
                    if (random.nextInt(GROWTH_SPAWN_COST) < charge) {
                        int ay = c.y + 1;
                        int growth = random.nextInt(11) == 0 ? b.shriekerSummon : b.sensor;
                        if (b.env.fluids.of(v.getBlock(c.x, ay, c.z)) != FluidKind.EMPTY) {
                            growth = growth == b.sensor ? b.sensorWaterlogged : b.shriekerSummonWaterlogged;
                        }
                        v.trySetBlock(c.x, ay, c.z, growth);
                    }
                    return Math.max(0, charge - GROWTH_SPAWN_COST);
                }
                if (random.nextInt(ADDITIONAL_DECAY_RATE) != 0) return charge;
                return charge - (closeToCatalyst ? 1 : decayPenalty(distSqr, charge));
            }
            case VEIN: {
                if (convert && attemptPlaceSculk(v, c.x, c.y, c.z, random)) return c.charge - 1;
                return random.nextInt(CHARGE_DECAY_RATE) == 0 ? (int) Math.floor(c.charge * 0.5F) : c.charge;
            }
            default:
                return c.decayDelay > 0 ? c.charge : 0;
        }
    }

    /** SculkBlock.getDecayPenalty. */
    private static int decayPenalty(double distSqr, int charge) {
        float f = (float) Math.sqrt(distSqr) - (float) NO_GROWTH_RADIUS;
        f = f * f;
        int j = (24 - NO_GROWTH_RADIUS) * (24 - NO_GROWTH_RADIUS);
        float f1 = Math.min(1.0F, f / (float) j);
        return Math.max(1, (int) ((float) charge * f1 * 0.5F));
    }

    /** SculkBlock.canPlaceGrowth. */
    private boolean canPlaceGrowth(FeatureWorldView v, int x, int y, int z) {
        int above = v.getBlock(x, y + 1, z);
        if (!b.env.attrs.isAir(above) && !(b.env.flag(b.env.waterBlock, above) && b.env.fluids.of(above) == FluidKind.WATER)) return false;
        int count = 0;
        for (int pz = z - 4; pz <= z + 4; pz++)
            for (int py = y; py <= y + 2; py++)
                for (int px = x - 4; px <= x + 4; px++) {
                    if (b.env.flag(b.sensorOrShrieker, v.getBlock(px, py, pz))) count++;
                    if (count > 2) return false;
                }
        return true;
    }

    /** SculkVeinBlock.attemptPlaceSculk. */
    private boolean attemptPlaceSculk(FeatureWorldView v, int x, int y, int z, WorldgenRandom random) {
        int st = v.getBlock(x, y, z);
        for (int d : MultifaceSpreader.shuffledDirections(random)) {
            if (!b.veinSpreader.hasFace(st, d)) continue;
            int px = x + DX[d], py = y + DY[d], pz = z + DZ[d];
            int s2 = v.getBlock(px, py, pz);
            if (!b.env.flag(b.replaceableWorldGen, s2)) continue;
            int sculk = b.env.sculk;
            v.trySetBlock(px, py, pz, sculk);
            b.veinSpreader.spreadAll(v, sculk, px, py, pz, true);
            int opp = d ^ 1;
            for (int d2 = 0; d2 < 6; d2++) {
                if (d2 == opp) continue;
                int qx = px + DX[d2], qy = py + DY[d2], qz = pz + DZ[d2];
                int s3 = v.getBlock(qx, qy, qz);
                if (b.isVein(s3)) onDischarged(v, VEIN, s3, qx, qy, qz);
            }
            return true;
        }
        return false;
    }

    /** SculkVeinBlock.onDischarged (그 외 동작은 아무것도 안 함). */
    private void onDischarged(FeatureWorldView v, int beh, int st, int x, int y, int z) {
        if (beh != VEIN || !b.isVein(st)) return;
        for (int d = 0; d < 6; d++) {
            if (b.veinSpreader.hasFace(st, d) && b.isSculk(v.getBlock(x + DX[d], y + DY[d], z + DZ[d]))) {
                st = b.veinSpreader.withFace(st, d, false);
            }
        }
        if (!b.veinSpreader.hasAnyFace(st)) {
            st = b.env.fluids.of(v.getBlock(x, y, z)) == FluidKind.EMPTY ? b.air : b.water;
        }
        v.trySetBlock(x, y, z, st);
    }

    /** ChargeCursor.getValidMovementPos. */
    private int[] getValidMovementPos(FeatureWorldView v, int x, int y, int z, WorldgenRandom random) {
        int n = NON_CORNER_NEIGHBOURS.length;
        int[] order = new int[n];
        for (int i = 0; i < n; i++) order[i] = i;
        MultifaceSpreader.shuffle(order, n, random);
        int[] result = null;
        for (int i : order) {
            int[] o = NON_CORNER_NEIGHBOURS[i];
            int px = x + o[0], py = y + o[1], pz = z + o[2];
            int s = v.getBlock(px, py, pz);
            if ((b.isSculk(s) || b.isVein(s)) && isMovementUnobstructed(v, x, y, z, o)) {
                result = new int[]{px, py, pz};
                if (hasSubstrateAccess(v, s, px, py, pz)) break;
            }
        }
        return result;
    }

    /** SculkVeinBlock.hasSubstrateAccess — 비월드젠 태그(sculk_replaceable)를 쓴다. */
    private boolean hasSubstrateAccess(FeatureWorldView v, int s, int x, int y, int z) {
        if (!b.isVein(s)) return false;
        for (int d = 0; d < 6; d++) {
            if (b.veinSpreader.hasFace(s, d) && b.env.flag(b.replaceable, v.getBlock(x + DX[d], y + DY[d], z + DZ[d]))) return true;
        }
        return false;
    }

    private boolean isMovementUnobstructed(FeatureWorldView v, int x, int y, int z, int[] o) {
        if (Math.abs(o[0]) + Math.abs(o[1]) + Math.abs(o[2]) == 1) return true;
        int dirX = o[0] < 0 ? 4 : 5;      // WEST / EAST
        int dirY = o[1] < 0 ? DOWN : UP;
        int dirZ = o[2] < 0 ? 2 : 3;      // NORTH / SOUTH
        if (o[0] == 0) return isUnobstructed(v, x, y, z, dirY) || isUnobstructed(v, x, y, z, dirZ);
        if (o[1] == 0) return isUnobstructed(v, x, y, z, dirX) || isUnobstructed(v, x, y, z, dirZ);
        return isUnobstructed(v, x, y, z, dirX) || isUnobstructed(v, x, y, z, dirY);
    }

    private boolean isUnobstructed(FeatureWorldView v, int x, int y, int z, int dir) {
        return !b.env.attrs.isFaceSturdy(v.getBlock(x + DX[dir], y + DY[dir], z + DZ[dir]), dir ^ 1);
    }
}
