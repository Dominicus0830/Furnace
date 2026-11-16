package furnacetools;

import java.io.FileOutputStream;
import java.io.PrintStream;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;

/** 상태마다 BlockState.isPathfindable(PathComputationType) — 땅·물·공중 한 글자씩. 인자: <출력>. */
public class ExportPathfindable {
    public static void main(String[] a) throws Exception {
        Vanilla.boot();
        PrintStream out = new PrintStream(new FileOutputStream(a[0]), false, "UTF-8");
        Registry<Block> reg = BuiltInRegistries.BLOCK;
        int id = 0;
        out.println("# id key land water air  (BlockState.isPathfindable(PathComputationType))");
        for (Block block : reg) {
            String key = reg.getKey(block).toString();
            for (BlockState st : block.getStateDefinition().getPossibleStates()) {
                out.println(id + " " + key + " " + (st.isPathfindable(PathComputationType.LAND) ? 1 : 0) + (st.isPathfindable(PathComputationType.WATER) ? 1 : 0)
                        + (st.isPathfindable(PathComputationType.AIR) ? 1 : 0));
                id++;
            }
        }
        out.close();
        System.out.println("exported " + id);
    }
}
