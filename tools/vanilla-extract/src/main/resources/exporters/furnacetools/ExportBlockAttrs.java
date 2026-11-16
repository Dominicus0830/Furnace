package furnacetools;

import java.io.FileOutputStream;
import java.io.PrintStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/** 상태별 블록 속성(공기·가림·교체·액체·모양 마스크 등). 인자: <출력>. */
public class ExportBlockAttrs {
    static final BlockPos ZERO = new BlockPos(0, 0, 0);

    public static void main(String[] a) throws Exception {
        Vanilla.boot();
        PrintStream out = new PrintStream(new FileOutputStream(a[0]), false, "UTF-8");
        Registry<Block> reg = BuiltInRegistries.BLOCK;
        Direction[] dirs = Direction.values();
        int id = 0;
        out.println("# id key air occlude replaceable light liquid blocksMotion solid solidRender collisionEmpty sturdyMask(DOWN,UP,NORTH,SOUTH,WEST,EAST) destroy collisionFullFaceMask(DOWN,UP,NORTH,SOUTH,WEST,EAST)");
        for (Block block : reg) {
            String key = reg.getKey(block).toString();
            for (BlockState st : block.getStateDefinition().getPossibleStates()) {
                String s = st.toString();
                int br = s.indexOf('[');
                String props = br < 0 ? "" : s.substring(br + 1, s.length() - 1);
                int mask = 0;
                for (int i = 0; i < dirs.length; i++) if (st.isFaceSturdy(EmptyBlockGetter.INSTANCE, ZERO, dirs[i])) mask |= 1 << i;
                int cmask = 0;
                VoxelShape collision = st.getCollisionShape(EmptyBlockGetter.INSTANCE, ZERO);   // BlockState.getCollisionShape(level,pos) (캐시값)
                for (int i = 0; i < dirs.length; i++) if (Block.isFaceFull(collision, dirs[i])) cmask |= 1 << i;
                boolean collisionEmpty = collision.isEmpty();
                float destroy;
                try { destroy = st.getDestroySpeed(EmptyBlockGetter.INSTANCE, ZERO); } catch (Throwable t) { destroy = -2f; }
                out.println(id + " " + key + " " + props.replace(' ', '_') + " " + b(st.isAir()) + b(st.canOcclude()) + b(st.canBeReplaced()) + " " + st.getLightEmission() + " "
                        + b(st.liquid()) + b(st.blocksMotion()) + b(st.isSolid())
                        + b(st.isSolidRender(EmptyBlockGetter.INSTANCE, ZERO)) + b(collisionEmpty) + " " + mask + " " + destroy + " " + cmask);
                id++;
            }
        }
        out.close();
        System.out.println("exported " + id);
    }

    static String b(boolean v) { return v ? "1" : "0"; }
}
