// 바닐라 FireBlock: 불. 30~39틱마다 틱 — 나이를 먹고, 비에 꺼지고, 타는 블록을 태워 없애거나 옮겨 붙고, 주변 빈칸으로 번진다.
// 블록별 불붙음(ignite)·타 없어짐(burn) 확률은 bootStrap 표(blocks/fire_1.21.1.txt).
package org.domi.furnace.server.block.types;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;

public class FireBlock extends BaseFireBlock {

    static final Prop AGE = B.prop("age");
    /** PROPERTY_BY_DIRECTION(아래 제외). */
    private static final Prop[] SIDE = new Prop[6];
    private static final int[] IGNITE = new int[B.BLOCKS.blockCount()], BURN = new int[B.BLOCKS.blockCount()];
    private static final Prop WATERLOGGED = B.prop("waterlogged");
    private static volatile FireBlock INSTANCE;

    static {
        for (int d = Dir.UP; d <= Dir.EAST; d++) SIDE[d] = B.prop(Dir.NAME[d]);
        try (var in = FireBlock.class.getClassLoader().getResourceAsStream("blocks/fire_1.21.1.txt");
             var r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] p = line.split(" ");
                int b = B.id(p[0]);
                IGNITE[b] = Integer.parseInt(p[1]);
                BURN[b] = Integer.parseInt(p[2]);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public FireBlock(int block) {
        super(block);
        INSTANCE = this;
    }

    static FireBlock instance() {
        FireBlock f = INSTANCE;
        return f != null ? f : (FireBlock) B.behavior(B.defaultState(FIRE));
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        return canSurvive(state, level, pos) ? stateWithAge(level, pos, AGE.intValue(state)) : B.defaultState("air");
    }

    /** getStateForPlacement(level, pos): 아래가 탈 것도 단단하지도 않으면 탈 것이 있는 옆·위 면에 붙는다. */
    int stateForPlacement(BlockLevel level, Pos pos) {
        Pos below = pos.below();
        int bs = level.getBlockState(below);
        if (!canBurn(bs) && !B.isFaceSturdy(bs, level, below, Dir.UP)) {
            int s = defaultState();
            for (int d : Dir.ALL) {
                if (d != Dir.DOWN) s = SIDE[d].with(s, canBurn(level.getBlockState(pos.relative(d))));
            }
            return s;
        }
        return defaultState();
    }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) {
        Pos below = pos.below();
        return B.isFaceSturdy(level.getBlockState(below), level, below, Dir.UP) || isValidFireLocation(level, pos);
    }

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) {
        level.scheduleTick(pos, block, fireTickDelay(level.random()));
        if (!level.gameRule("doFireTick")) return;
        if (!canSurvive(state, level, pos)) level.removeBlock(pos, false);
        int below = level.getBlockState(pos.below());
        boolean infiniburn = B.isTag(below, infiniburnTag(level));
        int age = AGE.intValue(state);
        if (!infiniburn && level.isRaining() && isNearRain(level, pos) && random.nextFloat() < 0.2F + age * 0.03F) {
            level.removeBlock(pos, false);
            return;
        }
        int newAge = Math.min(15, age + random.nextInt(3) / 2);
        if (age != newAge) {
            state = AGE.withInt(state, newAge);
            level.setBlock(pos, state, 4);
        }
        if (!infiniburn) {
            if (!isValidFireLocation(level, pos)) {
                Pos b = pos.below();
                if (!B.isFaceSturdy(level.getBlockState(b), level, b, Dir.UP) || age > 3) level.removeBlock(pos, false);
                return;
            }
            if (age == 15 && random.nextInt(4) == 0 && !canBurn(level.getBlockState(pos.below()))) {
                level.removeBlock(pos, false);
                return;
            }
        }
        boolean burnout = level.biomeIs(pos, "minecraft:increased_fire_burnout");
        int k = burnout ? -50 : 0;
        checkBurnOut(level, pos.east(), 300 + k, random, age, pos);
        checkBurnOut(level, pos.west(), 300 + k, random, age, pos);
        checkBurnOut(level, pos.below(), 250 + k, random, age, pos);
        checkBurnOut(level, pos.above(), 250 + k, random, age, pos);
        checkBurnOut(level, pos.north(), 300 + k, random, age, pos);
        checkBurnOut(level, pos.south(), 300 + k, random, age, pos);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = -1; y <= 4; y++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    int chance = 100;
                    if (y > 1) chance += (y - 1) * 100;
                    Pos p = pos.offset(x, y, z);
                    int odds = igniteOdds(level, p);
                    if (odds > 0) {
                        int n = (odds + 40 + level.difficulty() * 7) / (age + 30);
                        if (burnout) n /= 2;
                        if (n > 0 && random.nextInt(chance) <= n && (!level.isRaining() || !isNearRain(level, p))) {
                            int a = Math.min(15, age + random.nextInt(5) / 4);
                            if (!B.is(level.getBlockState(p), FIRE)   // 플러그인 BlockIgniteEvent(번짐) — 이미 불이면 묻지 않는다
                                    && !level.gameEvents().blockIgnite(level.dimension(), p, org.domi.furnace.server.plugin.GameEvents.IGNITE_SPREAD, pos, null)) continue;
                            level.setBlock(p, stateWithAge(level, p, a), 3);
                        }
                    }
                }
            }
        }
    }

    private static String infiniburnTag(BlockLevel level) {
        return switch (level.dimension()) {
            case "minecraft:the_nether" -> "minecraft:infiniburn_nether";
            case "minecraft:the_end" -> "minecraft:infiniburn_end";
            default -> "minecraft:infiniburn_overworld";
        };
    }

    protected boolean isNearRain(BlockLevel level, Pos pos) {
        return level.isRainingAt(pos) || level.isRainingAt(pos.west()) || level.isRainingAt(pos.east())
                || level.isRainingAt(pos.north()) || level.isRainingAt(pos.south());
    }

    private static int burnOdds(int state) { return WATERLOGGED.has(state) && WATERLOGGED.is(state) ? 0 : BURN[B.block(state)]; }

    private static int igniteOdds(int state) { return WATERLOGGED.has(state) && WATERLOGGED.is(state) ? 0 : IGNITE[B.block(state)]; }

    /** checkBurnOut: 그 칸이 타면 불이 옮겨 붙거나(비가 안 오면 1/2 남짓) 사라진다. TNT 는 점화. */
    private void checkBurnOut(BlockLevel level, Pos pos, int chance, RandomSource random, int age, Pos fire) {
        int odds = burnOdds(level.getBlockState(pos));
        if (random.nextInt(chance) < odds) {
            int s = level.getBlockState(pos);
            if (!level.gameEvents().blockBurn(level.dimension(), pos, s, fire)) return;   // 플러그인 BlockBurnEvent 취소(CraftBukkit 처럼 난수 뒤)
            if (random.nextInt(age + 10) < 5 && !level.isRainingAt(pos)) {
                int a = Math.min(age + random.nextInt(5) / 4, 15);
                level.setBlock(pos, stateWithAge(level, pos, a), 3);
            } else {
                level.removeBlock(pos, false);
            }
            if (B.behavior(s) instanceof TntBlock) TntBlock.prime(level, pos);
        }
    }

    private int stateWithAge(BlockLevel level, Pos pos, int age) {
        int s = getState(level, pos);
        return B.is(s, FIRE) ? AGE.withInt(s, age) : s;
    }

    private boolean isValidFireLocation(BlockLevel level, Pos pos) {
        for (int d : Dir.ALL) if (canBurn(level.getBlockState(pos.relative(d)))) return true;
        return false;
    }

    /** getIgniteOdds(level, pos): 빈칸이면 이웃 중 가장 잘 불붙는 값. */
    private int igniteOdds(BlockLevel level, Pos pos) {
        if (!level.isEmptyBlock(pos)) return 0;
        int max = 0;
        for (int d : Dir.ALL) max = Math.max(igniteOdds(level.getBlockState(pos.relative(d))), max);
        return max;
    }

    @Override
    protected boolean canBurn(int state) { return igniteOdds(state) > 0; }

    @Override
    public void onPlace(int state, BlockLevel level, Pos pos, int oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        level.scheduleTick(pos, block, fireTickDelay(level.random()));
    }

    private static int fireTickDelay(RandomSource r) { return 30 + r.nextInt(10); }
}
