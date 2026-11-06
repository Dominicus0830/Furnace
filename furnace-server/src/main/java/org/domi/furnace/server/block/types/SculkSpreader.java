// 바닐라 SculkSpreader(레벨용): 전하 커서(최대 32개, 각 최대 1000)를 매 틱 움직이며 SculkBehaviour 로 맥·스컬크·감지체·비명체를 키운다.
// 같은 칸에 모인 커서는 합치고(합 1000 이하), 칸마다 전하 합으로 레벨 이벤트 3006(입자)을 보낸다. 그 순회 순서는
// fastutil Object2IntOpenHashMap(BlockPos 해시) 순서를 흉내 낸다. 월드젠 쪽은 world/gen/feature/SculkPatchFeature.
package org.domi.furnace.server.block.types;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;

public final class SculkSpreader {

    public static final int MAX_GROWTH_RATE_RADIUS = 24, MAX_CHARGE = 1000, SHRIEKER_PLACEMENT_RATE = 11;
    public static final float MAX_DECAY_FACTOR = 0.5F;
    private static final int MAX_CURSORS = 32;

    final boolean isWorldGeneration;
    private final String replaceableBlocks;
    private final int growthSpawnCost, noGrowthRadius, chargeDecayRate, additionalDecayRate;
    private List<ChargeCursor> cursors = new ArrayList<>();

    public SculkSpreader(boolean isWorldGeneration, String replaceableBlocks, int growthSpawnCost, int noGrowthRadius, int chargeDecayRate,
                         int additionalDecayRate) {
        this.isWorldGeneration = isWorldGeneration;
        this.replaceableBlocks = replaceableBlocks;
        this.growthSpawnCost = growthSpawnCost;
        this.noGrowthRadius = noGrowthRadius;
        this.chargeDecayRate = chargeDecayRate;
        this.additionalDecayRate = additionalDecayRate;
    }

    public static SculkSpreader createLevelSpreader() { return new SculkSpreader(false, "minecraft:sculk_replaceable", 10, 4, 10, 5); }

    public static SculkSpreader createWorldGenSpreader() { return new SculkSpreader(true, "minecraft:sculk_replaceable_world_gen", 50, 1, 5, 10); }

    public String replaceableBlocks() { return replaceableBlocks; }

    public int growthSpawnCost() { return growthSpawnCost; }

    public int noGrowthRadius() { return noGrowthRadius; }

    public int chargeDecayRate() { return chargeDecayRate; }

    public int additionalDecayRate() { return additionalDecayRate; }

    public boolean isWorldGeneration() { return isWorldGeneration; }

    public List<ChargeCursor> getCursors() { return cursors; }

    public void clear() { cursors.clear(); }

    /** load: "cursors" 목록이 있으면 비우고 앞에서부터 32개까지(형식이 틀린 항목은 버린다 — resultOrPartial). */
    public void load(NbtCompound tag) {
        if (!(tag.tags().get("cursors") instanceof NbtList list)) return;
        cursors.clear();
        int n = 0;
        for (Object o : list.items()) {
            if (n >= MAX_CURSORS) break;
            if (!(o instanceof NbtCompound c)) continue;
            ChargeCursor cc = ChargeCursor.load(c);
            if (cc == null) continue;
            addCursor(cc);
            n++;
        }
    }

    public void save(NbtCompound tag) {
        NbtList list = new NbtList(cursors.isEmpty() ? (byte) 0 : (byte) 10);
        for (ChargeCursor c : cursors) list.add(c.save());
        tag.putList("cursors", list);
    }

    public void addCursors(Pos pos, int charge) {
        while (charge > 0) {
            int i = Math.min(charge, MAX_CHARGE);
            addCursor(new ChargeCursor(pos, i));
            charge -= i;
        }
    }

    private void addCursor(ChargeCursor cursor) {
        if (cursors.size() < MAX_CURSORS) cursors.add(cursor);
    }

    public void updateCursors(BlockLevel level, Pos pos, RandomSource random, boolean shouldConvertBlocks) {
        if (cursors.isEmpty()) return;
        List<ChargeCursor> list = new ArrayList<>();
        Map<Pos, ChargeCursor> map = new HashMap<>();
        PosIntOpenHashMap sums = new PosIntOpenHashMap();
        for (ChargeCursor c : cursors) {
            c.update(level, pos, random, this, shouldConvertBlocks);
            if (c.charge <= 0) {
                level.levelEvent(null, 3006, c.getPos(), 0);
            } else {
                Pos p = c.getPos();
                sums.add(p, c.charge);
                ChargeCursor existing = map.get(p);
                if (existing == null) {
                    map.put(p, c);
                    list.add(c);
                } else if (!isWorldGeneration && c.charge + existing.charge <= MAX_CHARGE) {
                    existing.mergeWith(c);
                } else {
                    list.add(c);
                    if (c.charge < existing.charge) map.put(p, c);
                }
            }
        }
        sums.forEachDescending((p, k) -> {
            ChargeCursor cc = map.get(p);
            Collection<Integer> facings = cc == null ? null : cc.getFacingData();
            if (k > 0 && facings != null) {
                int l = (int) (Math.log1p(k) / 2.3F) + 1;
                int m = (l << 6) + MultifaceBlock.pack(facings);
                level.levelEvent(null, 3006, p, m);
            }
        });
        cursors = list;
    }

    // ------------------------------------------------------------------ ChargeCursor

    public static final class ChargeCursor {
        /** NON_CORNER_NEIGHBOURS: betweenClosed(-1..1) 순서(x 가장 빠름) 중 모서리·중심 제외 18칸. */
        private static final int[][] NON_CORNER_NEIGHBOURS;

        static {
            List<int[]> l = new ArrayList<>(18);
            for (int z = -1; z <= 1; z++)
                for (int y = -1; y <= 1; y++)
                    for (int x = -1; x <= 1; x++)
                        if ((x == 0 || y == 0 || z == 0) && !(x == 0 && y == 0 && z == 0)) l.add(new int[] {x, y, z});
            NON_CORNER_NEIGHBOURS = l.toArray(new int[0][]);
        }

        private Pos pos;
        int charge;
        private int updateDelay;
        private int decayDelay;
        /** null = 없음(Optional.empty), 그 밖에는 Direction 순서 집합. */
        private TreeSet<Integer> facings;

        private ChargeCursor(Pos pos, int charge, int decayDelay, int updateDelay, TreeSet<Integer> facings) {
            this.pos = pos;
            this.charge = charge;
            this.decayDelay = decayDelay;
            this.updateDelay = updateDelay;
            this.facings = facings;
        }

        public ChargeCursor(Pos pos, int charge) { this(pos, charge, 1, 0, null); }

        public Pos getPos() { return pos; }

        public int getCharge() { return charge; }

        public int getDecayDelay() { return decayDelay; }

        public int getUpdateDelay() { return updateDelay; }

        public Collection<Integer> getFacingData() { return facings; }

        private boolean shouldUpdate(BlockLevel level, Pos rootPos, boolean isWorldGen) {
            if (charge <= 0) return false;
            if (isWorldGen) return true;
            // ServerLevel.shouldTickBlocksAt(rootPos): 블록 엔티티 틱이 도는 청크(촉매 칸)는 블록 틱 범위 안이다.
            return level instanceof ChunkLevel cl && cl.hasChunkAt(rootPos);
        }

        public void update(BlockLevel level, Pos rootPos, RandomSource random, SculkSpreader spreader, boolean shouldConvertBlocks) {
            if (!shouldUpdate(level, rootPos, spreader.isWorldGeneration)) return;
            if (updateDelay > 0) {
                updateDelay--;
                return;
            }
            int state = level.getBlockState(pos);
            SculkBehaviour sb = SculkBehaviour.of(state);
            if (shouldConvertBlocks && sb.attemptSpreadVein(level, pos, state, facings, spreader.isWorldGeneration)) {
                if (sb.canChangeBlockStateOnSpread()) {
                    state = level.getBlockState(pos);
                    sb = SculkBehaviour.of(state);
                }
                level.playSound(null, pos, "minecraft:block.sculk.spread", "blocks", 1.0F, 1.0F);
            }
            charge = sb.attemptUseCharge(this, level, rootPos, random, spreader, shouldConvertBlocks);
            if (charge <= 0) {
                sb.onDischarged(level, state, pos, random);
                return;
            }
            Pos next = getValidMovementPos(level, pos, random);
            if (next != null) {
                sb.onDischarged(level, state, pos, random);
                pos = next;
                if (spreader.isWorldGeneration) {
                    long dx = pos.x() - rootPos.x(), dz = pos.z() - rootPos.z();
                    if (!(dx * dx + dz * dz < 225)) {   // closerThan((root.x, y, root.z), 15)
                        charge = 0;
                        return;
                    }
                }
                state = level.getBlockState(next);
            }
            if (SculkBehaviour.isSculkBehaviour(state)) facings = availableFaces(state);
            decayDelay = sb.updateDecayDelay(decayDelay);
            updateDelay = sb.getSculkSpreadDelay();
        }

        void mergeWith(ChargeCursor c) {
            charge += c.charge;
            c.charge = 0;
            updateDelay = Math.min(updateDelay, c.updateDelay);
        }

        /** MultifaceBlock.availableFaces: 다면 블록이 아니면 빈 집합. */
        private static TreeSet<Integer> availableFaces(int state) {
            TreeSet<Integer> s = new TreeSet<>();
            if (B.behavior(state) instanceof MultifaceBlock) {
                for (int d : Dir.ALL) if (MultifaceBlock.hasFace(state, d)) s.add(d);
            }
            return s;
        }

        /** getValidMovementPos: 섞은 18칸 중 SculkBehaviour 블록으로 막힘 없이 갈 수 있는 마지막 칸(맥이 기질에 닿으면 바로). */
        private static Pos getValidMovementPos(BlockLevel level, Pos pos, RandomSource random) {
            int n = NON_CORNER_NEIGHBOURS.length;
            int[][] order = NON_CORNER_NEIGHBOURS.clone();
            for (int i = n; i > 1; i--) {   // Util.shuffledCopy
                int j = random.nextInt(i);
                int[] t = order[i - 1];
                order[i - 1] = order[j];
                order[j] = t;
            }
            Pos m = pos;
            for (int[] o : order) {
                Pos p = pos.offset(o[0], o[1], o[2]);
                int s = level.getBlockState(p);
                if (SculkBehaviour.isSculkBehaviour(s) && isMovementUnobstructed(level, pos, o)) {
                    m = p;
                    if (SculkVeinBlock.hasSubstrateAccess(level, s, p)) break;
                }
            }
            return m.equals(pos) ? null : m;
        }

        private static boolean isMovementUnobstructed(BlockLevel level, Pos from, int[] o) {
            if (Math.abs(o[0]) + Math.abs(o[1]) + Math.abs(o[2]) == 1) return true;
            int dx = o[0] < 0 ? Dir.WEST : Dir.EAST, dy = o[1] < 0 ? Dir.DOWN : Dir.UP, dz = o[2] < 0 ? Dir.NORTH : Dir.SOUTH;
            if (o[0] == 0) return isUnobstructed(level, from, dy) || isUnobstructed(level, from, dz);
            if (o[1] == 0) return isUnobstructed(level, from, dx) || isUnobstructed(level, from, dz);
            return isUnobstructed(level, from, dx) || isUnobstructed(level, from, dy);
        }

        private static boolean isUnobstructed(BlockLevel level, Pos pos, int dir) {
            Pos p = pos.relative(dir);
            return !B.isFaceSturdy(level.getBlockState(p), level, p, Dir.opposite(dir));
        }

        // CODEC: pos(int[3]), charge(0..1000), decay_delay(0..1), update_delay, facings(선택, 방향 이름 목록)
        NbtCompound save() {
            NbtCompound t = new NbtCompound();
            t.putIntArray("pos", new int[] {pos.x(), pos.y(), pos.z()});
            t.putInt("charge", charge);
            t.putInt("decay_delay", decayDelay);
            t.putInt("update_delay", updateDelay);
            if (facings != null) {
                NbtList l = new NbtList(facings.isEmpty() ? (byte) 0 : (byte) 8);
                for (int d : facings) l.add(Dir.NAME[d]);
                t.putList("facings", l);
            }
            return t;
        }

        static ChargeCursor load(NbtCompound t) {
            if (!(t.tags().get("pos") instanceof int[] p) || p.length != 3) return null;
            if (!t.containsNumber("charge") || !t.containsNumber("decay_delay") || !t.containsNumber("update_delay")) return null;
            int charge = t.getIntOr0("charge"), decay = t.getIntOr0("decay_delay"), update = t.getIntOr0("update_delay");
            if (charge < 0 || charge > MAX_CHARGE || decay < 0 || decay > 1) return null;
            TreeSet<Integer> f = null;
            if (t.tags().get("facings") instanceof NbtList l) {
                f = new TreeSet<>();
                for (Object o : l.items()) {
                    int d = o instanceof String s ? java.util.Arrays.asList(Dir.NAME).indexOf(s) : -1;
                    if (d < 0) return null;
                    f.add(d);
                }
            }
            return new ChargeCursor(new Pos(p[0], p[1], p[2]), charge, decay, update, f);
        }
    }

    // ------------------------------------------------------------------ Object2IntOpenHashMap<BlockPos> 순회 순서

    /** fastutil Object2IntOpenHashMap(기본 용량 16, 적재율 0.75)의 삽입·재해시·순회(뒤에서 앞으로) 순서. 키 해시는 Vec3i.hashCode. */
    static final class PosIntOpenHashMap {
        private Pos[] key = new Pos[33];
        private int[] value = new int[33];
        private int n = 32, mask = 31, maxFill = 24, size;

        interface Visitor {
            void visit(Pos p, int v);
        }

        private static int hash(Pos p) {
            int h = ((p.y() + p.z() * 31) * 31 + p.x()) * 0x9E3779B9;   // HashCommon.mix
            return h ^ h >>> 16;
        }

        /** computeInt(p, (k, v) -> (v == null ? 0 : v) + add). */
        void add(Pos p, int add) {
            int pos = hash(p) & mask;
            while (key[pos] != null) {
                if (key[pos].equals(p)) {
                    value[pos] += add;
                    return;
                }
                pos = pos + 1 & mask;
            }
            key[pos] = p;
            value[pos] = add;
            if (size++ >= maxFill) rehash(arraySize(size + 1));
        }

        private static int arraySize(int expected) {
            long s = Math.max(2, Long.highestOneBit((long) Math.ceil(expected / 0.75f) - 1) << 1);
            return (int) s;
        }

        private void rehash(int newN) {
            int newMask = newN - 1;
            Pos[] nk = new Pos[newN + 1];
            int[] nv = new int[newN + 1];
            int i = n;
            for (int j = size; j-- != 0; ) {
                while (key[--i] == null) ;
                int pos = hash(key[i]) & newMask;
                while (nk[pos] != null) pos = pos + 1 & newMask;
                nk[pos] = key[i];
                nv[pos] = value[i];
            }
            n = newN;
            mask = newMask;
            maxFill = Math.min((int) Math.ceil(newN * 0.75f), newN - 1);
            key = nk;
            value = nv;
        }

        void forEachDescending(Visitor v) {
            for (int i = n; i-- > 0; ) if (key[i] != null) v.visit(key[i], value[i]);
        }
    }
}
