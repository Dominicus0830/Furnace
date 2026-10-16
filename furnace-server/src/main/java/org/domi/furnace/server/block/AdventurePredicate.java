// 바닐라 AdventureModePredicate.test(BlockInWorld): 술어 중 하나라도 맞으면 참. 술어는 BlockPredicate(blocks·state·nbt).
// 값은 can_place_on/can_break 구성 요소의 정규 값({predicates:[…], show_in_tooltip}).
package org.domi.furnace.server.block;

import java.util.Map;
import org.domi.furnace.server.item.Val;

public final class AdventurePredicate {
    private AdventurePredicate() {}

    public static boolean test(Object component, BlockLevel level, Pos pos) {
        Object preds = Val.map(component).get("predicates");
        if (preds == null) return false;
        int state = level.getBlockState(pos);
        for (Object p : Val.list(preds)) if (matches(Val.map(p), state, level, pos)) return true;
        return false;
    }

    /** BlockPredicate.matches(BlockInWorld). */
    static boolean matches(Map<String, Object> p, int state, BlockLevel level, Pos pos) {
        Object blocks = p.get("blocks");
        if (blocks != null && !inHolderSet(blocks, state)) return false;
        Object props = p.get("state");
        if (props != null && !stateMatches(Val.map(props), state)) return false;
        // nbt: 블록 엔티티가 있어야 맞출 수 있다 — 블록 엔티티 저장 형태가 들어오기 전에는 거짓
        return p.get("nbt") == null;
    }

    private static boolean inHolderSet(Object set, int state) {
        Blocks b = Blocks.get();
        if (Val.isList(set)) {
            for (Object o : Val.list(set)) if (b.blockOf(state) == b.block(Val.string(o))) return true;
            return false;
        }
        String s = Val.string(set);
        if (s.startsWith("#")) return B.isTag(state, s.substring(1));
        return b.blockOf(state) == b.block(s);
    }

    /** StatePropertiesPredicate: 각 항목이 그 블록의 속성이고 값이 같거나(문자열) 범위 안이어야(min·max, 문자열 비교는 값 파싱). */
    static boolean stateMatches(Map<String, Object> props, int state) {
        Blocks b = Blocks.get();
        for (var e : props.entrySet()) {
            Prop prop = b.prop(e.getKey());
            if (prop == null || !prop.has(state)) return false;
            String actual = prop.valueName(state);
            Object want = e.getValue();
            if (Val.isMap(want)) {
                Map<String, Object> r = Val.map(want);
                String[] values = prop.values(state);
                int idx = indexOf(values, actual);
                Object min = r.get("min"), max = r.get("max");
                if (min != null) {
                    int i = indexOf(values, Val.string(min));
                    if (i < 0 || idx < i) return false;
                }
                if (max != null) {
                    int i = indexOf(values, Val.string(max));
                    if (i < 0 || idx > i) return false;
                }
            } else if (!actual.equals(Val.string(want))) {
                return false;
            }
        }
        return true;
    }

    /** 값의 비교 순위(Comparable 순서): 불리언은 false < true, 정수는 수, 열거는 선언 순서(=값 목록 순서). */
    private static int indexOf(String[] values, String v) {
        if (values.length == 2 && (values[0].equals("true") || values[0].equals("false"))) {
            return v.equals("false") ? 0 : v.equals("true") ? 1 : -1;
        }
        for (int i = 0; i < values.length; i++) if (values[i].equals(v)) return i;
        return -1;
    }
}
