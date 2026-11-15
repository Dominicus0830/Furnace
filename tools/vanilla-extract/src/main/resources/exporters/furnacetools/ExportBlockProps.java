package furnacetools;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 블록 속성 추출. 두 파일:
 *  <출력1> 블록별: 이름 hasCollision explosionResistance requiresCorrectTool randomlyTicking friction speedFactor jumpFactor
 *          canOcclude ignitedByLava pushReaction replaceableProp dynamicShape offset soundType(volume,pitch,break,step,place,hit,fall) lootTable
 *  <출력2>(gzip) 상태별: id FULL CENTER RIGID(DUNSWE 비트 6자리씩) replaceable legacySolid blocksMotion hasBlockEntity hasAnalogOutput
 *          redstoneConductor suffocating viewBlocking hasPostProcess emissive lightEmission mapColorId
 * BlockBehaviour.Properties 의 필드를 직접 읽는다(접근 제한은 바닐라 jar 에서 열려 있다).
 */
public class ExportBlockProps {
    public static void main(String[] a) throws Exception {
        Vanilla.boot();
        Registry<Block> reg = BuiltInRegistries.BLOCK;
        try (PrintWriter w = new PrintWriter(new OutputStreamWriter(new FileOutputStream(a[0]), "UTF-8"));
             PrintWriter s = new PrintWriter(new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(a[1])), "UTF-8"))) {
            w.println("# 자동 생성: " + Vanilla.generator("ExportBlockProps") + ". 블록 hasCollision explosionResistance requiresCorrectTool randomlyTicking friction speedFactor jumpFactor canOcclude ignitedByLava pushReaction replaceable dynamicShape offset volume pitch break step place hit fall lootTable");
            s.println("# 자동 생성: " + Vanilla.generator("ExportBlockProps") + ". id FULL CENTER RIGID replaceable legacySolid blocksMotion hasBlockEntity hasAnalogOutput redstoneConductor suffocating viewBlocking hasPostProcess emissive light mapColor");
            for (Block block : reg) {
                BlockBehaviour.Properties p = block.properties();
                SoundType st = p.soundType;
                String[] snd = sounds(st);
                w.println(reg.getKey(block) + " " + b(p.hasCollision) + " " + p.explosionResistance + " " + b(p.requiresCorrectToolForDrops) + " " + b(p.isRandomlyTicking) + " " + p.friction + " " + p.speedFactor + " " + p.jumpFactor + " " + b(p.canOcclude)
                        + " " + b(p.ignitedByLava) + " " + p.pushReaction.name().toLowerCase() + " " + b(p.replaceable) + " " + b(p.dynamicShape) + " " + (p.offsetFunction != null ? 1 : 0)
                        + " " + st.getVolume() + " " + st.getPitch() + " " + String.join(" ", snd) + " " + block.getLootTable().location());
                for (BlockState t : block.getStateDefinition().getPossibleStates()) {
                    StringBuilder sb = new StringBuilder().append(Block.getId(t));
                    for (SupportType type : SupportType.values()) {
                        sb.append(' ');
                        for (Direction d : Direction.values()) sb.append(t.isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, d, type) ? '1' : '0');
                    }
                    sb.append(' ').append(b(t.canBeReplaced())).append(b(t.isSolid())).append(b(t.blocksMotion())).append(b(t.hasBlockEntity())).append(b(t.hasAnalogOutputSignal()))
                      .append(b(p.isRedstoneConductor.test(t, EmptyBlockGetter.INSTANCE, BlockPos.ZERO))).append(b(p.isSuffocating.test(t, EmptyBlockGetter.INSTANCE, BlockPos.ZERO)))
                      .append(b(p.isViewBlocking.test(t, EmptyBlockGetter.INSTANCE, BlockPos.ZERO)))
                      .append(b(p.hasPostProcess.test(t, EmptyBlockGetter.INSTANCE, BlockPos.ZERO))).append(b(p.emissiveRendering.test(t, EmptyBlockGetter.INSTANCE, BlockPos.ZERO)))
                      .append(' ').append(t.getLightEmission()).append(' ').append(mapColor(p.mapColor.apply(t)));
                    s.println(sb);
                }
            }
        }
        System.out.println("done");
    }

    static String b(boolean v) { return v ? "1" : "0"; }

    /** SoundType 의 소리 이벤트 5개(break step place hit fall) — 레코드 접근자 중 SoundEvent 를 돌려주는 것들을 선언 순서로. */
    static String[] sounds(SoundType st) throws Exception {
        List<String> out = new ArrayList<>();
        for (Field f : SoundType.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.getType() == float.class) continue;
            f.setAccessible(true);
            Object v = f.get(st);
            out.add(v instanceof SoundEvent e ? e.getLocation().toString() : String.valueOf(v));
        }
        return out.toArray(new String[0]);
    }

    static int mapColor(Object c) throws Exception {
        for (Field f : c.getClass().getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) && f.getType() == int.class) { f.setAccessible(true); return f.getInt(c); }
        }
        return -1;
    }
}
