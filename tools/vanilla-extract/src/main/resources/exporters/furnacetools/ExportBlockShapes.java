package furnacetools;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 블록 상태별 모양(VoxelShape → 상자 목록). 출력: 모양 사전(S <번호> <상자;…>) + 상태 줄(<상태 id> outline collision support occlusion interaction).
 * 인자: <출력>(풀어 쓴 텍스트 — 실행기가 gzip 으로 묶는다).
 */
public class ExportBlockShapes {
    static final BlockPos ZERO = new BlockPos(0, 0, 0);
    static final Map<String, Integer> dict = new LinkedHashMap<>();

    public static void main(String[] a) throws Exception {
        Vanilla.boot();
        PrintStream out = new PrintStream(new FileOutputStream(a[0]), false, "UTF-8");
        Registry<Block> reg = BuiltInRegistries.BLOCK;
        List<String> rows = new ArrayList<>();
        int id = 0;
        for (Block block : reg) {
            for (BlockState st : block.getStateDefinition().getPossibleStates()) {
                // 위치에 따라 어긋나는 블록(꽃·대나무 등): (0,0,0)의 어긋남을 빼서 기준 모양으로(어긋남 식은 Furnace 가 계산)
                Vec3 off = st.getOffset(EmptyBlockGetter.INSTANCE, ZERO);
                boolean offset = off.x() != 0 || off.y() != 0 || off.z() != 0 || !st.getOffset(EmptyBlockGetter.INSTANCE, new BlockPos(5, 0, 11)).equals(off);
                rows.add(id + " " + sid(st.getShape(EmptyBlockGetter.INSTANCE, ZERO), off) + " " + sid(st.getCollisionShape(EmptyBlockGetter.INSTANCE, ZERO), off)
                        + " " + sid(st.getBlockSupportShape(EmptyBlockGetter.INSTANCE, ZERO), off)
                        + " " + sid(st.getOcclusionShape(EmptyBlockGetter.INSTANCE, ZERO), off) + " " + sid(st.getInteractionShape(EmptyBlockGetter.INSTANCE, ZERO), off) + (offset ? " o" : ""));
                id++;
            }
        }
        out.println("# 바닐라 " + Vanilla.mc() + " 블록 모양: S <번호> <minX,minY,minZ,maxX,maxY,maxZ;…> / <상태 id> outline collision support occlusion interaction [o = 위치 어긋남 블록, 모양은 어긋남 뺀 기준]");
        for (var e : dict.entrySet()) out.println("S " + e.getValue() + " " + e.getKey());
        for (String r : rows) out.println(r);
        out.close();
        System.out.println("states " + id + " shapes " + dict.size());
    }

    static int sid(VoxelShape shape, Vec3 off) {
        StringBuilder sb = new StringBuilder();
        for (AABB b : shape.toAabbs()) {
            if (sb.length() > 0) sb.append(';');
            sb.append(b.minX - off.x()).append(',').append(b.minY - off.y()).append(',').append(b.minZ - off.z()).append(',')
              .append(b.maxX - off.x()).append(',').append(b.maxY - off.y()).append(',').append(b.maxZ - off.z());
        }
        String k = sb.length() == 0 ? "-" : sb.toString();
        return dict.computeIfAbsent(k, x -> dict.size());
    }
}
