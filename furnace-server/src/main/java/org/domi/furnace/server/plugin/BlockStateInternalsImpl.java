// 블록 상태 속성 다리(N2b-6) 구현 — 바닐라에서 뽑아 둔 표(BlockAttrs·LightTables·BlockInfo)와 블록 동작의 isSignalSource 를 한 번 모아 둔다.
package org.domi.furnace.server.plugin;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.api.internal.BlockStateInternals;
import org.domi.furnace.server.block.BlockBehaviors;
import org.domi.furnace.server.block.BlockInfo;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.block.Shape;
import org.domi.furnace.server.light.LightTables;
import org.domi.furnace.server.world.BlockAttrs;

final class BlockStateInternalsImpl implements BlockStateInternals {

    private volatile Table table;

    @Override
    public Table table() {
        Table t = table;
        if (t == null) {
            synchronized (this) {
                if (table == null) table = build();
                t = table;
            }
        }
        return t;
    }

    private static Table build() {
        Blocks blocks = Blocks.get();
        BlockInfo info = BlockInfo.get();
        BlockAttrs attrs = BlockAttrs.get();
        int n = blocks.stateCount();
        byte[] lightBlock = new byte[n], lightEmission = new byte[n], push = new byte[n];
        short[] flags = new short[n];
        float[] destroy = new float[n], resistance = new float[n], friction = new float[n];
        int[] mapColor = new int[n];
        for (int s = 0; s < n; s++) {
            int b = blocks.blockOf(s);
            lightBlock[s] = (byte) LightTables.lightBlock(s);
            lightEmission[s] = (byte) info.lightEmission(s);
            push[s] = (byte) pushOrdinal(info.pushReaction(b));
            int f = 0;
            if (attrs.canOcclude(s)) f |= CAN_OCCLUDE;
            if (attrs.isSolidRender(s)) f |= SOLID_RENDER;
            if (attrs.isReplaceable(s)) f |= REPLACEABLE;
            if (attrs.blocksMotion(s)) f |= BLOCKS_MOTION;
            if (BlockBehaviors.of(s).isSignalSource(s)) f |= SIGNAL_SOURCE;
            flags[s] = (short) f;
            destroy[s] = info.destroyTime(b);
            resistance[s] = info.explosionResistance(b);
            friction[s] = info.friction(b);
            mapColor[s] = info.mapColor(s);
        }
        return new Table(lightBlock, lightEmission, push, flags, destroy, resistance, friction, mapColor, mapColorCols());
    }

    private static int pushOrdinal(String name) {
        return switch (name) {
            case "normal" -> 0;
            case "destroy" -> 1;
            case "block" -> 2;
            case "ignore" -> 3;
            case "push_only" -> 4;
            default -> throw new IllegalStateException("모르는 피스톤 반응: " + name);
        };
    }

    /** 바닐라 MapColor 번호 → RGB(리소스 줄: 번호 RGB). */
    private static int[] mapColorCols() {
        List<int[]> rows = new ArrayList<>();
        try (var in = BlockStateInternalsImpl.class.getClassLoader().getResourceAsStream("blocks/mapcolors_1.21.1.txt")) {
            var r = new java.io.BufferedReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] p = line.split(" ");
                rows.add(new int[] {Integer.parseInt(p[0]), Integer.parseInt(p[1])});
            }
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        int[] cols = new int[rows.size()];
        for (int[] row : rows) cols[row[0]] = row[1];
        return cols;
    }

    @Override
    public double[] outlineBoxes(int stateId) {
        if (stateId < 0 || stateId >= Blocks.get().stateCount()) return new double[0];
        Shape s = BlockInfo.get().shape(stateId);
        int c = s.boxCount();
        double[] out = new double[c * 6];
        for (int i = 0; i < c; i++) System.arraycopy(s.box(i), 0, out, i * 6, 6);
        return out;
    }
}
