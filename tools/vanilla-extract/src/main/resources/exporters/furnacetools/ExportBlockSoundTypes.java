package furnacetools;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * 블록별 SoundType. 이전에는 서버 바이트코드를 읽는 파이썬 도구였으나(난독화 이름 고정), 이제 서버 클래스를 직접 불러 상태마다
 * BlockState.getSoundType() 을 읽는다 — BlockSetType/WoodType 경유 소리와 상태에 따라 소리가 달라지는 블록(금 간 장식 항아리)이 따로 처리 없이 들어온다.
 * 종류 번호는 SoundType 상수의 선언 순서(쓰인 것만)다. 출력 형식:
 *   [types] 번호 크기 높이 break step place hit fall (소리 이름은 minecraft: 생략)
 *   [blocks] 블록 이름 종류번호 (기본 상태의 소리)
 *   [state-overrides] 블록 속성=값 종류번호 (그 값일 때만 기본 소리와 다른 블록)
 * 인자: <출력>.
 */
public class ExportBlockSoundTypes {
    public static void main(String[] a) throws Exception {
        Vanilla.boot();
        Registry<Block> reg = BuiltInRegistries.BLOCK;
        // SoundType 상수의 선언 순서(클래스 파일의 필드 순서)
        IdentityHashMap<SoundType, Integer> declared = new IdentityHashMap<>();
        for (Field f : SoundType.class.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) || f.getType() != SoundType.class) continue;
            declared.putIfAbsent((SoundType) f.get(null), declared.size());
        }
        List<String> names = new ArrayList<>();
        List<SoundType> base = new ArrayList<>();
        List<Over> overrides = new ArrayList<>();
        for (Block block : reg) {
            List<BlockState> states = block.getStateDefinition().getPossibleStates();
            SoundType def = block.defaultBlockState().getSoundType();
            String name = reg.getKey(block).getPath();
            names.add(name);
            base.add(def);
            List<BlockState> differing = new ArrayList<>();
            for (BlockState s : states) if (s.getSoundType() != def) differing.add(s);
            if (differing.isEmpty()) continue;
            overrides.addAll(explain(name, states, differing));
        }
        // 쓰인 종류만, 선언 순서로(선언에 없는 객체는 처음 쓰인 순서로 뒤에)
        Map<SoundType, Integer> order = new LinkedHashMap<>();
        IdentityHashMap<SoundType, Boolean> used = new IdentityHashMap<>();
        for (SoundType t : base) used.put(t, true);
        for (Over o : overrides) used.put(o.type, true);
        List<SoundType> list = new ArrayList<>(used.keySet());
        list.sort((x, y) -> {
            int dx = declared.getOrDefault(x, Integer.MAX_VALUE), dy = declared.getOrDefault(y, Integer.MAX_VALUE);
            return Integer.compare(dx, dy);
        });
        for (SoundType t : list) order.put(t, order.size());
        IdentityHashMap<SoundType, Integer> index = new IdentityHashMap<>();
        order.forEach(index::put);

        try (PrintWriter o = new PrintWriter(new OutputStreamWriter(new FileOutputStream(a[0]), "UTF-8"))) {
            o.print("# 자동 생성(" + Vanilla.generator("ExportBlockSoundTypes") + ", 바닐라 " + Vanilla.mc() + "): 블록별 SoundType.\n");
            o.print("# [types] 번호 크기 높이 break step place hit fall (소리 이름은 minecraft: 생략)\n");
            o.print("# [blocks] 블록 이름 종류번호 (BlockBehaviour.Properties.sound; 문·다락문·버튼·압력판·울타리 문·표지판은 BlockSetType/WoodType 소리)\n");
            o.print("[types]\n");
            for (SoundType t : order.keySet()) {
                o.print(index.get(t) + " " + t.getVolume() + " " + t.getPitch() + " " + ev(t.getBreakSound()) + " " + ev(t.getStepSound()) + " " + ev(t.getPlaceSound())
                        + " " + ev(t.getHitSound()) + " " + ev(t.getFallSound()) + "\n");
            }
            o.print("[blocks]\n");
            for (int i = 0; i < names.size(); i++) o.print(names.get(i) + " " + index.get(base.get(i)) + "\n");
            o.print("[state-overrides]\n");
            for (Over ov : overrides) o.print(ov.block + " " + ov.prop + "=" + ov.value + " " + index.get(ov.type) + "\n");
        }
        System.out.println("types " + order.size() + " blocks " + names.size());
    }

    static String ev(SoundEvent e) {
        String s = e.getLocation().toString();
        return s.startsWith("minecraft:") ? s.substring("minecraft:".length()) : s;
    }

    /**
     * 기본 소리와 다른 상태들을 "속성=값" 하나로 설명한다(그 값을 가진 상태가 정확히 그 집합이고 소리가 하나일 때).
     * 설명할 수 없으면 도구를 바꿔야 하는 새 경우라 멈춘다.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static List<Over> explain(String name, List<BlockState> states, List<BlockState> differing) {
        SoundType t = differing.get(0).getSoundType();
        for (BlockState s : differing) if (s.getSoundType() != t) throw new IllegalStateException(name + ": 기본과 다른 소리가 둘 이상");
        for (Property p : states.get(0).getProperties()) {
            for (Object v : p.getPossibleValues()) {
                List<BlockState> withValue = new ArrayList<>();
                for (BlockState s : states) if (s.getValue(p).equals(v)) withValue.add(s);
                if (withValue.size() == differing.size() && withValue.containsAll(differing)) return List.of(new Over(name, p.getName(), p.getName((Comparable) v), t));
            }
        }
        throw new IllegalStateException(name + ": 상태별 소리 차이를 속성 하나로 설명할 수 없다");
    }

    record Over(String block, String prop, String value, SoundType type) {}
}
