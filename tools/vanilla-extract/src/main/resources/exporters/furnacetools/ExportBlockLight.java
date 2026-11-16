package furnacetools;

import java.io.FileOutputStream;
import java.io.PrintStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 상태별 빛 속성: getLightBlock(빛 막는 정도 0..15), useShapeForLightOcclusion, canOcclude.
 * 한 줄에 상태 순서대로: 첫 줄 lightBlock 16진 한 글자씩, 둘째 줄 useShape 0/1, 셋째 줄 canOcclude 0/1. 인자: <출력>.
 */
public class ExportBlockLight {
    public static void main(String[] a) throws Exception {
        Vanilla.boot();
        Registry<Block> reg = BuiltInRegistries.BLOCK;
        StringBuilder lb = new StringBuilder(), us = new StringBuilder(), co = new StringBuilder();
        int n = 0;
        for (Block block : reg) {
            for (BlockState st : block.getStateDefinition().getPossibleStates()) {
                lb.append(Integer.toHexString(st.getLightBlock(EmptyBlockGetter.INSTANCE, new BlockPos(0, 0, 0))));
                us.append(st.useShapeForLightOcclusion() ? '1' : '0');
                co.append(st.canOcclude() ? '1' : '0');
                n++;
            }
        }
        try (PrintStream out = new PrintStream(new FileOutputStream(a[0]), false, "UTF-8")) {
            out.println(lb);
            out.println(us);
            out.println(co);
        }
        System.out.println("exported " + n);
    }
}
