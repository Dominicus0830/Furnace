package furnacetools;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 소리 블록 악기 추출. BlockState.instrument() 를 상태마다 읽어, 기본값(harp)이 아닌 블록을 악기별로 모은다.
 * 한 블록 안에서 상태마다 다르면 오류로 멈춘다. 출력 줄: <악기 이름> <블록 이름> <블록 이름> ... 인자: <출력>.
 */
public class ExportNoteInstruments {
    public static void main(String[] a) throws Exception {
        Vanilla.boot();
        Registry<Block> reg = BuiltInRegistries.BLOCK;
        Map<String, List<String>> by = new TreeMap<>();
        for (Block block : reg) {
            String inst = null;
            for (BlockState t : block.getStateDefinition().getPossibleStates()) {
                String i = t.instrument().getSerializedName();
                if (inst == null) inst = i;
                else if (!inst.equals(i)) throw new IllegalStateException("상태마다 악기가 다름: " + reg.getKey(block));
            }
            if (!"harp".equals(inst)) by.computeIfAbsent(inst, k -> new ArrayList<>()).add(reg.getKey(block).toString());
        }
        try (PrintWriter w = new PrintWriter(new OutputStreamWriter(new FileOutputStream(a[0]), "UTF-8"))) {
            w.println("# 자동 생성: " + Vanilla.generator("ExportNoteInstruments") + ". 악기 블록... (적히지 않은 블록은 harp)");
            for (var e : by.entrySet()) w.println(e.getKey() + " " + String.join(" ", e.getValue()));
        }
        System.out.println("done");
    }
}
