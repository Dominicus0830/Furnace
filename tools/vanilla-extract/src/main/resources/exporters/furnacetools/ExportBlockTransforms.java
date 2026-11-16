package furnacetools;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 블록 state별 mirror→rotate 결과 (StructureTemplate 가 state.mirror(m).rotate(r) 로 배치).
 * 인자: <변환 출력> [<담장 테스트 출력> [<지지 마스크 출력>]] — 모두 풀어 쓴 텍스트(실행기가 gzip 으로 묶는다).
 * 변환 출력: 줄 하나 = 변환이 자기 자신이 아닌 state. "id m0r0 m0r1 m0r2 m0r3 m1r0 ... m2r3" (m: NONE,LEFT_RIGHT,FRONT_BACK / r: NONE,CW90,CW180,CCW90).
 */
public class ExportBlockTransforms {
    public static void main(String[] a) throws Exception {
        Vanilla.boot();
        Registry<Block> reg = BuiltInRegistries.BLOCK;
        List<BlockState> all = new ArrayList<>();
        IdentityHashMap<BlockState, Integer> ids = new IdentityHashMap<>();
        for (Block block : reg) for (BlockState st : block.getStateDefinition().getPossibleStates()) { ids.put(st, all.size()); all.add(st); }
        Mirror[] mirrors = Mirror.values();
        Rotation[] rots = Rotation.values();
        PrintStream out = new PrintStream(new FileOutputStream(a[0]), false, "UTF-8");
        out.println("# states " + all.size() + " ; 줄: id 뒤에 mirror(NONE,LEFT_RIGHT,FRONT_BACK) x rotation(NONE,CW90,CW180,CCW90) 12개 결과 state id");
        int n = 0;
        for (int id = 0; id < all.size(); id++) {
            BlockState st = all.get(id);
            StringBuilder sb = new StringBuilder().append(id);
            boolean changed = false;
            for (Mirror m : mirrors) for (Rotation r : rots) {
                BlockState res = st.mirror(m).rotate(r);
                int rid = ids.get(res);
                if (rid != id) changed = true;
                sb.append(' ').append(rid);
            }
            if (changed) { out.println(sb); n++; }
        }
        out.close();
        if (a.length > 1) {
            // 담장 연결 판정용: 상태의 충돌 모양 아래면이 담장 테스트 상자(F,G,H,I,J)를 완전히 덮는지 5비트(F=1,G=2,H=4,I=8,J=16).
            VoxelShape[] tests = {
                Block.box(7.0, 0.0, 7.0, 9.0, 16.0, 9.0), Block.box(7.0, 0.0, 0.0, 9.0, 16.0, 9.0), Block.box(7.0, 0.0, 7.0, 9.0, 16.0, 16.0),
                Block.box(0.0, 0.0, 7.0, 9.0, 16.0, 9.0), Block.box(7.0, 0.0, 7.0, 16.0, 16.0, 9.0)};
            PrintStream o2 = new PrintStream(new FileOutputStream(a[1]), false, "UTF-8");
            o2.println("# states " + all.size() + " ; 줄: id wallTestMask (F=post,G=north,H=south,I=west,J=east 5비트: 위 블록 아래면이 해당 테스트 상자를 완전히 덮음)");
            BlockPos zero = new BlockPos(0, 0, 0);
            for (int id = 0; id < all.size(); id++) {
                VoxelShape face = all.get(id).getCollisionShape(EmptyBlockGetter.INSTANCE, zero).getFaceShape(Direction.DOWN);
                int mask = 0;
                for (int i = 0; i < 5; i++) if (!Shapes.joinIsNotEmpty(tests[i], face, BooleanOp.ONLY_FIRST)) mask |= 1 << i;
                if (mask != 0) o2.println(id + " " + mask);
            }
            o2.close();
        }
        if (a.length > 2) {
            // 지지 타입별 면 마스크: CENTER(횃불·랜턴), RIGID(레일·압력판). 비트0~5 = DOWN,UP,NORTH,SOUTH,WEST,EAST.
            PrintStream o3 = new PrintStream(new FileOutputStream(a[2]), false, "UTF-8");
            o3.println("# states " + all.size() + " ; 줄: id centerMask rigidMask collisionFaceNonEmptyMask");
            Direction[] dirs = Direction.values();
            BlockPos zero2 = new BlockPos(0, 0, 0);
            for (int id = 0; id < all.size(); id++) {
                int cm = 0, rm = 0, nm = 0;
                VoxelShape coll = all.get(id).getCollisionShape(EmptyBlockGetter.INSTANCE, zero2);
                for (int i = 0; i < dirs.length; i++) {
                    if (all.get(id).isFaceSturdy(EmptyBlockGetter.INSTANCE, zero2, dirs[i], SupportType.CENTER)) cm |= 1 << i;
                    if (all.get(id).isFaceSturdy(EmptyBlockGetter.INSTANCE, zero2, dirs[i], SupportType.RIGID)) rm |= 1 << i;
                    if (!coll.getFaceShape(dirs[i]).isEmpty()) nm |= 1 << i;
                }
                if (cm != 0 || rm != 0 || nm != 0) o3.println(id + " " + cm + " " + rm + " " + nm);
            }
            o3.close();
        }
        System.out.println("states=" + all.size() + " changed=" + n + " mirrors=" + Arrays.toString(mirrors) + " rots=" + Arrays.toString(rots));
    }
}
