package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.block.types.LiquidBlock;
import org.domi.furnace.server.block.types.ScaffoldingBlock;
import org.domi.furnace.server.world.ChunkColumn;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 비계·물·용암의 문맥 충돌 모양을 바닐라 1.21.1 결과와 대조한다(T-018 R6).
 * 정답표 `collision/context-shapes-1.21.1.tsv` 는 바닐라 서버 jar 를 직접 돌려 BlockState.getCollisionShape(level, (0,64,0), ctx) 를
 * 뽑은 것이다(도구 tools/vanilla-data/ExportContextCollisionShapes.java). 문맥은 EntityCollisionContext 의 5인자 생성자 값(발 높이·웅크림·
 * 물/용암 위에 서기)과 empty, 위 칸 유체는 빈 것·원천·흐름·떨어지는 흐름.
 * 모양은 1/16 격자 칸 채움으로, 걸어 오르기 후보 격자(getCoords(Y))는 Furnace 가 격자를 바닐라와 같게 둔 모양(exactYCoords)일 때 그대로 비교한다.
 */
class ContextCollisionShapeVanillaTest {

    private static BlockPlacementTest.TestLevel level;
    private static final Pos AT = new Pos(0, 64, 0);

    @BeforeAll
    static void setUp() {
        BlockPlacementTest.install();
        level = new BlockPlacementTest.TestLevel();
    }

    record Expected(String boxes, String yCoords) {}

    @Test
    void matchesVanillaTable() throws Exception {
        Map<Integer, Expected> shapes = new HashMap<>();
        List<String[]> rows = new ArrayList<>();
        try (var in = getClass().getClassLoader().getResourceAsStream("collision/context-shapes-1.21.1.tsv");
             var r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            for (String line; (line = r.readLine()) != null; ) {
                if (line.startsWith("#") || line.isBlank()) continue;
                String[] f = line.split("\t");
                if (f[0].equals("shape")) shapes.put(Integer.parseInt(f[1]), new Expected(f[2], f[3]));
                else rows.add(f);
            }
        }
        assertEquals(3100, rows.size(), "정답표 줄 수");
        int checked = 0, nonEmpty = 0, exactY = 0;
        Map<String, Integer> perShape = new HashMap<>();
        List<String> bad = new ArrayList<>();
        for (String[] f : rows) {
            int state = state(f[0], f[1]);
            setAbove(f[3]);
            CollisionContext ctx = context(f[2]);
            Shape got = B.behavior(state).getCollisionShape(state, level, AT, ctx);
            Expected want = shapes.get(Integer.parseInt(f[4]));
            String why = compare(got, want);
            if (why != null) bad.add(String.join(" ", f) + " → " + why);
            if (!got.isEmpty()) nonEmpty++;
            if (got.exactYCoords() != null) exactY++;
            perShape.merge(f[4], 1, Integer::sum);
            checked++;
        }
        System.out.println("context collision shapes checked=" + checked + " nonEmpty=" + nonEmpty + " exactY=" + exactY + " perShape=" + perShape);
        assertTrue(bad.isEmpty(), bad.size() + " 건 다름, 처음: " + bad.subList(0, Math.min(10, bad.size())));
        // 빈 시험 막기: 네 가지 바닐라 모양(비계 안정·빔·바닥 판, 액체 반 칸)이 모두 나온다
        assertEquals(4, perShape.size());
        assertTrue(nonEmpty > 500);
    }

    /** 바닐라 결과와 같은지: 1/16 격자 칸 채움, 그리고 Furnace 가 정확한 격자를 가진 모양이면 Y 격자 좌표. 같으면 null. */
    private static String compare(Shape got, Expected want) {
        String gotFill = fill(got), wantFill = fill(want.boxes());
        if (!gotFill.equals(wantFill)) return "모양 다름: furnace=" + boxes(got) + " vanilla=" + want.boxes();
        double[] ys = got.exactYCoords();
        if (ys != null && !got.isEmpty()) {
            StringBuilder b = new StringBuilder("[");
            for (int i = 0; i < ys.length; i++) b.append(i == 0 ? "" : ",").append(ys[i]);
            String g = b.append(']').toString();
            if (!g.equals(want.yCoords())) return "Y 격자 다름: furnace=" + g + " vanilla=" + want.yCoords();
        }
        return null;
    }

    private static String boxes(Shape s) {
        if (s.isEmpty()) return "-";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.boxCount(); i++) b.append(i == 0 ? "" : ";").append(java.util.Arrays.toString(s.box(i)));
        return b.toString();
    }

    private static String fill(Shape s) {
        boolean[] cells = new boolean[16 * 16 * 16];
        for (int i = 0; i < s.boxCount(); i++) mark(cells, s.box(i));
        return bits(cells);
    }

    private static String fill(String boxes) {
        boolean[] cells = new boolean[16 * 16 * 16];
        if (!boxes.equals("-")) {
            for (String box : boxes.split(";")) {
                String[] v = box.split(",");
                double[] d = new double[6];
                for (int k = 0; k < 6; k++) d[k] = Double.parseDouble(v[k]);
                mark(cells, d);
            }
        }
        return bits(cells);
    }

    private static void mark(boolean[] cells, double[] b) {
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
            double cx = (x + 0.5) / 16, cy = (y + 0.5) / 16, cz = (z + 0.5) / 16;
            if (b[0] <= cx && cx < b[3] && b[1] <= cy && cy < b[4] && b[2] <= cz && cz < b[5]) cells[(x * 16 + y) * 16 + z] = true;
        }
        // 1/16 격자에 맞지 않는 상자는 없어야 한다(맞지 않으면 칸 채움 비교가 모양을 다 보지 못한다)
        for (double v : b) assertEquals(Math.rint(v * 16), v * 16, 1.0E-9, "1/16 격자 밖 좌표 " + v);
    }

    private static String bits(boolean[] cells) {
        StringBuilder b = new StringBuilder(cells.length);
        for (boolean c : cells) b.append(c ? '1' : '0');
        return b.toString();
    }

    private static int state(String block, String props) {
        int st = B.defaultState("minecraft:" + block);
        if (!props.isEmpty()) {
            for (String kv : props.split(",")) {
                String[] p = kv.split("=");
                st = B.prop(p[0]).with(st, p[1]);
            }
        }
        return st;
    }

    /** 위 칸 유체를 놓는다(청크에 바로 — 이웃 갱신·유체 틱 없이). */
    private static void setAbove(String fluid) {
        int st = switch (fluid) {
            case "empty" -> B.defaultState("minecraft:air");
            case "water_source" -> state("water", "level=0");
            case "water_flowing3" -> state("water", "level=3");
            case "water_falling" -> state("water", "level=8");
            case "lava_source" -> state("lava", "level=0");
            case "lava_flowing3" -> state("lava", "level=3");
            default -> throw new IllegalArgumentException(fluid);
        };
        ChunkColumn col = level.column(0, 0);
        col.setBlock(0, AT.y() + 1, 0, st);
        assertEquals(st, level.getBlockState(AT.above()));
    }

    private static CollisionContext context(String key) {
        if (key.equals("none")) return CollisionContext.EMPTY;
        String[] v = key.split(";");
        return CollisionContext.of(Double.parseDouble(v[0]), v[1].equals("1"), v[2].equals("1"), v[3].equals("1"));
    }

    /** 바닐라 모양 상수와 같은 격자: 비계 바닥 판(1/8 격자)·액체 반 칸(1/2 격자)은 Shapes.box 그대로. */
    @Test
    void newShapesUseVanillaGrid() {
        assertArrayEquals(new double[] {0, 0.125, 0.25, 0.375, 0.5, 0.625, 0.75, 0.875, 1.0}, ScaffoldingBlock.UNSTABLE_SHAPE_BOTTOM.exactYCoords());
        assertArrayEquals(new double[] {0, 0.5, 1.0}, LiquidBlock.STABLE_SHAPE.exactYCoords());
    }
}
