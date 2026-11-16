package furnacetools;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;

/**
 * 바닐라 BlockState.updateShape(direction, neighbor, level, pos, neighborPos) 차분 테스트 벡터 생성기(저장소 리소스가 아니라 시험 재료를 만들 때 쓴다).
 * 인자: <출력> <블록이름[,블록이름...]> <이웃후보 블록이름,...> <상태당 시도 수> <시드> [<block_tags 파일>]
 * 출력 한 줄: "<state id> <dir 0..5> <cell0..cell26(후보 인덱스; 27칸, 중심=13은 state 자체)> <결과 state id>"
 * 후보 풀은 모든 후보 블록의 "모든 상태"가 아니라 상태에서 무작위 추출(풀 크기 제한). 태그 파일 기본값은 저장소의 worldgen/<판>/block_tags.txt.
 */
public class ShapeVectors {
    static final Direction[] DIRS = Direction.values();

    public static void main(String[] a) throws Exception {
        Vanilla.boot();
        Registry<Block> reg = BuiltInRegistries.BLOCK;
        bindBlockTags(reg, a.length > 5 ? a[5]
                : Vanilla.repo().resolve("furnace-server/src/main/resources/worldgen/" + Vanilla.mc() + "/block_tags.txt").toString());
        List<BlockState> all = new ArrayList<>();
        IdentityHashMap<BlockState, Integer> ids = new IdentityHashMap<>();
        Map<String, List<BlockState>> byName = new HashMap<>();
        for (Block block : reg) for (BlockState st : block.getStateDefinition().getPossibleStates()) {
            ids.put(st, all.size()); all.add(st);
            byName.computeIfAbsent(reg.getKey(block).toString().replace("minecraft:", ""), k -> new ArrayList<>()).add(st);
        }
        PrintStream out = new PrintStream(new FileOutputStream(a[0]), false, "UTF-8");
        List<String> targets = Arrays.asList(a[1].split(","));
        List<BlockState> pool = new ArrayList<>();
        pool.add(all.get(0)); // air
        for (String n : a[2].split(",")) { List<BlockState> l = byName.get(n.trim()); if (l == null) throw new RuntimeException("no block " + n); pool.addAll(l); }
        int tries = Integer.parseInt(a[3]);
        Random rnd = new Random(Long.parseLong(a[4]));
        int count = 0;
        for (String tn : targets) {
            List<BlockState> sts = byName.get(tn.trim());
            if (sts == null) throw new RuntimeException("no block " + tn);
            for (BlockState st : sts) {
                for (int t = 0; t < tries; t++) {
                    BlockState[] cells = new BlockState[27];
                    int[] idx = new int[27];
                    // 중심 근처(6방향 이웃)는 풀에서 무작위, 나머지는 70% 공기
                    for (int i = 0; i < 27; i++) {
                        int pi = rnd.nextInt(100) < 65 ? 0 : rnd.nextInt(pool.size());
                        idx[i] = pi; cells[i] = pool.get(pi);
                    }
                    final BlockState center = st;
                    cells[13] = center;
                    final BlockState[] fc = cells;
                    LevelAccessor level = (LevelAccessor) Proxy.newProxyInstance(ShapeVectors.class.getClassLoader(), new Class[]{LevelAccessor.class}, (proxy, m, args) -> {
                        if (m.isDefault()) {
                            if (m.getParameterCount() >= 2 && m.getParameterTypes()[0] == BlockPos.class
                                && (m.getParameterTypes()[1] == Block.class || m.getParameterTypes()[1] == Fluid.class)) return null; // scheduleTick no-op
                            return InvocationHandler.invokeDefault(proxy, m, args);
                        }
                        String n = m.getName();
                        Class<?>[] pt = m.getParameterTypes();
                        if (pt.length == 1 && pt[0] == BlockPos.class) {
                            BlockPos p = (BlockPos) args[0];
                            BlockState s = cell(fc, p);
                            if (n.equals("getBlockState")) return s;
                            if (n.equals("getFluidState")) return s.getFluidState();
                            if (n.equals("getBlockEntity")) return null;
                        }
                        Class<?> rt = m.getReturnType();
                        if (rt == boolean.class) return false;
                        if (rt == int.class) return 0;
                        if (rt == long.class) return 0L;
                        if (rt == float.class) return 0f;
                        if (rt == double.class) return 0d;
                        return null;
                    });
                    BlockPos pos = new BlockPos(1, 1, 1);
                    for (int d = 0; d < 6; d++) {
                        BlockPos np = pos.relative(DIRS[d]);
                        BlockState nb = cell(cells, np);
                        BlockState res;
                        try { res = st.updateShape(DIRS[d], nb, level, pos, np); } catch (Throwable ex) { out.println("# EXC " + ids.get(st) + " " + d + " " + ex); continue; }
                        StringBuilder sb = new StringBuilder().append(ids.get(st)).append(' ').append(d);
                        for (int i = 0; i < 27; i++) sb.append(' ').append(ids.get(pool.get(idx[i])));
                        sb.append(' ').append(ids.get(res));
                        out.println(sb); count++;
                    }
                }
            }
        }
        out.close();
        System.out.println("vectors=" + count);
    }

    // 바닐라 부트스트랩만으로는 블록 태그가 비어 있다 — 저장소의 block_tags.txt(바닐라 데이터 생성기 결과)로 레지스트리에 바인딩한다.
    @SuppressWarnings("unchecked")
    static void bindBlockTags(Registry<Block> reg, String file) throws Exception {
        Map<TagKey<Block>, List<Holder<Block>>> map = new HashMap<>();
        Map<String, Block> byName = new HashMap<>();
        for (Block b : reg) byName.put(reg.getKey(b).toString(), b);
        for (String line : Files.readAllLines(Path.of(file))) {
            line = line.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] p = line.split(" ");
            List<Holder<Block>> holders = new ArrayList<>();
            for (int i = 1; i < p.length; i++) { if (byName.containsKey(p[i])) holders.add(reg.getHolder(ResourceLocation.withDefaultNamespace(p[i].substring("minecraft:".length()))).get()); }
            map.put(TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace(p[0].substring("minecraft:".length()))), holders);
        }
        ((MappedRegistry<Block>) reg).bindTags(map);
    }

    static BlockState cell(BlockState[] c, BlockPos p) {
        int x = p.getX(), y = p.getY(), z = p.getZ();
        if (x < 0 || x > 2 || y < 0 || y > 2 || z < 0 || z > 2) return Blocks.AIR.defaultBlockState();
        return c[x + y * 3 + z * 9];
    }
}
