package furnacetools;

import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 비계·물·용암의 문맥별 충돌 모양(BlockState.getCollisionShape(level, pos, ctx))을 바닐라 서버 jar 를 직접 돌려 뽑는다.
 * 결과는 ContextCollisionShapeVanillaTest 의 정답표(furnace-server/src/test/resources/collision/<판>/context-shapes.tsv). 인자: <출력>.
 */
public class ExportContextCollisionShapes {
    static FluidState fluidAbove;

    public static void main(String[] args) throws Exception {
        Vanilla.boot();
        Registry<Block> reg = BuiltInRegistries.BLOCK;
        Map<String, Block> blocks = new HashMap<>();
        for (Block b : reg) blocks.put(reg.getKey(b).toString(), b);
        // EntityCollisionContext 의 생성자는 보호돼 있어 리플렉션으로 연다.
        Constructor<EntityCollisionContext> ctxNew = EntityCollisionContext.class.getDeclaredConstructor(boolean.class, double.class, ItemStack.class, Predicate.class, Entity.class);
        ctxNew.setAccessible(true);
        ItemStack emptyStack = ItemStack.EMPTY;
        CollisionContext emptyCtx = CollisionContext.empty();
        BlockPos pos = new BlockPos(0, 64, 0);
        BlockGetter level = (BlockGetter) Proxy.newProxyInstance(ExportContextCollisionShapes.class.getClassLoader(), new Class<?>[] {BlockGetter.class}, (proxy, m, a) -> {
            switch (m.getName()) {
                case "getFluidState": return fluidAbove;                    // getFluidState(pos.above()) 만 묻는다
                case "getBlockState": throw new IllegalStateException("getBlockState 를 묻지 않아야 한다");
                case "getBlockEntity": return null;
                case "getHeight": return 384;
                case "getMinBuildHeight": return -64;
                default:
                    if (m.isDefault()) return InvocationHandler.invokeDefault(proxy, m, a);
                    throw new IllegalStateException(m.toString());
            }
        });
        // 유체 상태: 물·용암 블록 상태의 getFluidState
        List<BlockState> waterStates = blocks.get("minecraft:water").getStateDefinition().getPossibleStates();
        List<BlockState> lavaStates = blocks.get("minecraft:lava").getStateDefinition().getPossibleStates();
        Map<String, FluidState> fluids = new LinkedHashMap<>();
        fluids.put("empty", blocks.get("minecraft:air").getStateDefinition().getPossibleStates().get(0).getFluidState());
        Set<FluidState> waterFluids = new HashSet<>(), lavaFluids = new HashSet<>();
        for (BlockState s : waterStates) waterFluids.add(s.getFluidState());
        for (BlockState s : lavaStates) lavaFluids.add(s.getFluidState());
        for (BlockState s : waterStates) { String l = props(s); if (l.equals("level=0")) fluids.put("water_source", s.getFluidState()); if (l.equals("level=3")) fluids.put("water_flowing3", s.getFluidState()); if (l.equals("level=8")) fluids.put("water_falling", s.getFluidState()); }
        for (BlockState s : lavaStates) { String l = props(s); if (l.equals("level=0")) fluids.put("lava_source", s.getFluidState()); if (l.equals("level=3")) fluids.put("lava_flowing3", s.getFluidState()); }

        out = new ArrayList<>();
        double[] offs = {-1.5, -1.0, -1.0E-5, -9.999999747378752E-6, -5.0E-6, 0.0, 0.25, 0.4999, 0.5 - 9.999999747378752E-6, 0.4999901, 0.5, 0.75, 0.99999, 1.0 - 9.999999747378752E-6, 0.99999001, 1.0, 1.5};
        double[] liquidOffs = {-1.0, 0.0, 0.4999, 0.5 - 9.999999747378752E-6, 0.4999901, 0.5, 1.0, 1.5};
        int n = 0;
        for (BlockState s : blocks.get("minecraft:scaffolding").getStateDefinition().getPossibleStates()) {
            fluidAbove = fluids.get("empty");
            out.add(row("scaffolding", props(s), "none", "empty", s.getCollisionShape(level, pos, emptyCtx))); n++;
            for (double o : offs) for (boolean desc : new boolean[] {false, true}) {
                CollisionContext ctx = ctxNew.newInstance(desc, 64 + o, emptyStack, (Predicate<FluidState>) f -> false, null);
                out.add(row("scaffolding", props(s), ctxKey(64 + o, desc, false, false), "empty", s.getCollisionShape(level, pos, ctx))); n++;
            }
        }
        for (String liquid : new String[] {"water", "lava"}) {
            for (BlockState s : blocks.get("minecraft:" + liquid).getStateDefinition().getPossibleStates()) {
                if (!Set.of("level=0", "level=1", "level=7", "level=8", "level=15").contains(props(s))) continue;
                for (var fa : fluids.entrySet()) {
                    fluidAbove = fa.getValue();
                    out.add(row(liquid, props(s), "none", fa.getKey(), s.getCollisionShape(level, pos, emptyCtx))); n++;
                    for (double o : liquidOffs) for (int stand = 0; stand < 4; stand++) {
                        boolean w = (stand & 1) != 0, l = (stand & 2) != 0;
                        Predicate<FluidState> p = f -> w && waterFluids.contains(f) || l && lavaFluids.contains(f);
                        boolean desc = stand == 3;   // 웅크림은 액체 모양에 영향이 없어야 한다 — 한 경우만 섞는다
                        CollisionContext ctx = ctxNew.newInstance(desc, 64 + o, emptyStack, p, null);
                        out.add(row(liquid, props(s), ctxKey(64 + o, desc, w, l), fa.getKey(), s.getCollisionShape(level, pos, ctx))); n++;
                    }
                }
            }
        }
        try (PrintStream ps = new PrintStream(args[0], "UTF-8")) {
            ps.println("# 바닐라 " + Vanilla.mc() + " 문맥 충돌 모양: BlockState.getCollisionShape(level, (0,64,0), ctx) — 바닐라 서버 jar 를 직접 돌려 뽑았다(도구 " + Vanilla.generator("ExportContextCollisionShapes") + ").");
            ps.println("# shape 줄: 번호, 상자들(minX,minY,minZ,maxX,maxY,maxZ;… 빔은 -), getCoords(Y)");
            ps.println("# 나머지 줄: 블록, 속성, 문맥(none = CollisionContext.empty(), 아니면 발높이;웅크림;물위서기;용암위서기), 위 칸 유체, shape 번호");
            for (var e : shapeIds.entrySet()) ps.println("shape\t" + e.getValue() + "\t" + e.getKey());
            for (String r : out) ps.println(r);
        }
        System.out.println("rows " + n + " shapes " + shapeIds.size());
    }

    static List<String> out;
    static final Map<String, Integer> shapeIds = new LinkedHashMap<>();

    static String props(BlockState state) {
        String s = state.toString();
        int i = s.indexOf('[');
        return i < 0 ? "" : s.substring(i + 1, s.length() - 1);
    }

    static String ctxKey(double bottom, boolean desc, boolean w, boolean l) {
        return Double.toString(bottom) + ";" + (desc ? 1 : 0) + ";" + (w ? 1 : 0) + ";" + (l ? 1 : 0);
    }

    static String row(String block, String props, String ctx, String above, VoxelShape shape) {
        StringBuilder b = new StringBuilder();
        for (AABB box : shape.toAabbs()) {
            if (b.length() > 0) b.append(';');
            b.append(box.minX).append(',').append(box.minY).append(',').append(box.minZ).append(',')
                    .append(box.maxX).append(',').append(box.maxY).append(',').append(box.maxZ);
        }
        if (b.length() == 0) b.append('-');
        String key = b + "\t" + shape.getCoords(Direction.Axis.Y).toString().replace(" ", "");
        Integer id = shapeIds.computeIfAbsent(key, k -> shapeIds.size());
        return block + "\t" + props + "\t" + ctx + "\t" + above + "\t" + id;
    }
}
