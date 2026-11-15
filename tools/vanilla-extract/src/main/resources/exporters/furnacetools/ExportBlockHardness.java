package furnacetools;

import java.io.FileOutputStream;
import java.io.PrintStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;

/** 블록별 destroyTime(경도, BlockState.getDestroySpeed) — 기본 state 기준(같은 블록의 state 는 모두 같다). 인자: <출력>. */
public class ExportBlockHardness {
    static final BlockPos ZERO = new BlockPos(0, 0, 0);

    public static void main(String[] a) throws Exception {
        Vanilla.boot();
        PrintStream out = new PrintStream(new FileOutputStream(a[0]), false, "UTF-8");
        Registry<Block> reg = BuiltInRegistries.BLOCK;
        out.println("# 자동 생성(" + Vanilla.generator("ExportBlockHardness") + ", 바닐라 " + Vanilla.mc() + "): 블록 이름 destroyTime(-1 = 부술 수 없음)");
        int n = 0;
        for (Block block : reg) {
            out.println(reg.getKey(block) + " " + block.defaultBlockState().getDestroySpeed(EmptyBlockGetter.INSTANCE, ZERO));
            n++;
        }
        out.close();
        System.out.println("exported " + n);
    }
}
