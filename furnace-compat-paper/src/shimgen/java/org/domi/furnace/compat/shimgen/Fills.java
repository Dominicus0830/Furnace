// T-262: 전체 껍데기의 채우기 계획. 채움 표(바닐라 jar 에서 값만 뽑은 것)와 서명표를 맞대어, 껍데기 클래스마다
// "무엇을 진짜로 채울지"를 정한다. 몸통은 여기서 만들지 않는다(FullShells 가 ASM 으로 쓴다). 채우는 것은 값만으로 정해지는 멤버뿐이다:
// 단순 생성자(상위 호출 + 필드 대입), 단순 읽기, 정적 리터럴, 열거형 상수 인자, 레코드 기본 메서드. 나머지는 던지는 껍데기로 남는다.
package org.domi.furnace.compat.shimgen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.domi.furnace.tools.extract.FillTable;
import org.domi.furnace.tools.extract.SigTable;
import org.objectweb.asm.Opcodes;

final class Fills {
    /** 클래스 하나의 채우기. */
    static final class ClassFill {
        /** 채울 생성자(서술자 → 표 줄). */
        final Map<String, FillTable.Ctor> ctors = new LinkedHashMap<>();
        /** 채울 읽기(이름+서술자 → 표 줄). */
        final Map<String, FillTable.Getter> getters = new LinkedHashMap<>();
        /** 정적 리터럴(필드 이름 → 표 줄). */
        final Map<String, FillTable.Static> statics = new LinkedHashMap<>();
        /** 값이 생기는 필드(생성자가 대입하거나 정적 리터럴). */
        final Set<String> liveFields = new HashSet<>();
        /** 열거형 상수(서수 순서). 열거형을 채우지 않으면 null. */
        List<FillTable.EnumConst> enumConsts;
        /** 레코드 기본 메서드 이름. */
        final Set<String> objectMethods = new HashSet<>();

        boolean any() {
            return !ctors.isEmpty() || !getters.isEmpty() || !statics.isEmpty() || enumConsts != null || !objectMethods.isEmpty();
        }
    }

    final Map<String, ClassFill> byClass = new HashMap<>();
    int ctorCount;
    int getterCount;
    int staticCount;
    int enumCount;
    int enumConstCount;
    int objectMethodCount;

    ClassFill of(String n) { return byClass.get(n); }

    /** 이 클래스의 멤버(이름+서술자 → 접근 값)와 필드(이름 → 서술자, 접근 값). */
    private record Decl(Map<String, Integer> methods, Map<String, String> fieldDescs, Map<String, Integer> fieldAccess) {}

    static Fills plan(FullShells g, FillTable ft) {
        Fills f = new Fills();
        Map<String, Decl> decls = new HashMap<>();
        for (String n : g.shells) {
            Map<String, Integer> ms = new HashMap<>();
            Map<String, String> fd = new HashMap<>();
            Map<String, Integer> fa = new HashMap<>();
            for (SigTable.Member m : g.table(n).members(n)) {
                if (m.kind() == 'M') ms.put(m.name() + m.desc(), m.access());
                else {
                    fd.put(m.name(), m.desc());
                    fa.put(m.name(), m.access());
                }
            }
            decls.put(n, new Decl(ms, fd, fa));
        }
        // 생성자: 상위 생성자가 채워졌거나 Object·Record·Enum 일 때만 — 고정점까지 되풀이
        Set<String> okCtor = new HashSet<>();   // "소유자#서술자"
        Map<String, FillTable.Ctor> cand = new LinkedHashMap<>();
        for (String n : g.shells) {
            if (!decls.containsKey(n) || (g.decl(n).access() & Opcodes.ACC_INTERFACE) != 0) continue;
            for (FillTable.Ctor c : ft.ctors(n)) {
                Decl d = decls.get(n);
                Integer acc = d.methods().get("<init>" + c.desc());
                if (acc == null || (acc & Opcodes.ACC_ABSTRACT) != 0) continue;
                if (!c.superOwner().equals(g.decl(n).superName())) continue;
                if (!stepsFit(c, d)) continue;
                cand.put(n + "#" + c.desc(), c);
            }
        }
        // 고정점: 상위 생성자가 채워져야 채운다. 열거형은 상수를 전부 채울 수 없으면 생성자도 채우지 않는다
        // (상수가 0 인자로 만들어지는데 읽기가 진짜 값처럼 0 을 돌려주면 안 된다) — 그 탈락이 하위 생성자에도 번지므로 되풀이한다.
        Set<String> banned = new HashSet<>();
        while (true) {
            okCtor.clear();
            boolean changed = true;
            while (changed) {
                changed = false;
                for (var e : cand.entrySet()) {
                    if (okCtor.contains(e.getKey()) || banned.contains(e.getKey())) continue;
                    FillTable.Ctor c = e.getValue();
                    String so = c.superOwner();
                    boolean ok = so.equals("java/lang/Object") && c.superDesc().equals("()V")
                            || so.equals("java/lang/Record") && c.superDesc().equals("()V")
                            || so.equals("java/lang/Enum") && c.superDesc().equals("(Ljava/lang/String;I)V")
                            || okCtor.contains(so + "#" + c.superDesc());
                    if (ok) {
                        okCtor.add(e.getKey());
                        changed = true;
                    }
                }
            }
            boolean grew = false;
            for (String n : g.shells) {
                if (!decls.containsKey(n) || !isEnumClass(g, n)) continue;
                boolean hasCtor = false;
                for (String k : okCtor) if (k.startsWith(n + "#")) hasCtor = true;
                if (hasCtor && enumConsts(g, ft, n, okCtor) == null) {
                    for (String k : List.copyOf(okCtor)) if (k.startsWith(n + "#")) grew |= banned.add(k);
                }
            }
            if (!grew) break;
        }
        for (String key : okCtor) {
            FillTable.Ctor c = cand.get(key);
            ClassFill cf = f.byClass.computeIfAbsent(c.owner(), k -> new ClassFill());
            cf.ctors.put(c.desc(), c);
            for (FillTable.Step s : c.steps()) cf.liveFields.add(s.field());
        }
        for (String n : g.shells) {
            Decl d = decls.get(n);
            if (d == null) continue;
            ClassFill cf = f.byClass.get(n);
            boolean enumLike = (g.decl(n).access() & Opcodes.ACC_ENUM) != 0 && (g.decl(n).access() & Opcodes.ACC_INTERFACE) == 0;
            boolean record = (g.decl(n).access() & 0x10000) != 0;
            // 읽기: 필드가 이 클래스의 생성자 대입으로 값이 생길 때만
            if (cf != null) {
                for (FillTable.Getter gt : ft.getters(n)) {
                    Integer acc = d.methods().get(gt.name() + gt.desc());
                    if (acc == null || (acc & (Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT)) != 0) continue;
                    if (!cf.liveFields.contains(gt.field()) || !gt.fieldDesc().equals(d.fieldDescs().get(gt.field()))) continue;
                    if ((d.fieldAccess().get(gt.field()) & Opcodes.ACC_STATIC) != 0) continue;
                    cf.getters.put(gt.name() + gt.desc(), gt);
                }
            }
            // 정적 리터럴
            for (FillTable.Static s : ft.statics(n)) {
                Integer acc = d.fieldAccess().get(s.field());
                if (acc == null || (acc & Opcodes.ACC_STATIC) == 0 || !s.desc().equals(d.fieldDescs().get(s.field()))) continue;
                if (g.constant(n, s.field()) != null) continue;   // 이미 ConstantValue
                if (enumLike && (acc & Opcodes.ACC_ENUM) != 0) continue;
                if (cf == null) cf = f.byClass.computeIfAbsent(n, k -> new ClassFill());
                cf.statics.put(s.field(), s);
                cf.liveFields.add(s.field());
            }
            // 레코드 기본 메서드
            if (record) {
                List<String> comps = g.recordComponents(n);
                boolean fieldsOk = true;
                for (String rc : comps) {
                    int k = rc.indexOf(':');
                    if (!rc.substring(k + 1).equals(d.fieldDescs().get(rc.substring(0, k)))) fieldsOk = false;
                }
                if (fieldsOk) {
                    for (String om : ft.objectMethods(n)) {
                        String key = switch (om) {
                            case "toString" -> "toString()Ljava/lang/String;";
                            case "hashCode" -> "hashCode()I";
                            default -> "equals(Ljava/lang/Object;)Z";
                        };
                        if (!d.methods().containsKey(key)) continue;
                        if (cf == null) cf = f.byClass.computeIfAbsent(n, k -> new ClassFill());
                        cf.objectMethods.add(om);
                    }
                }
            }
            // 열거형: 상수를 전부 같은 서수·이름으로 알고, 인자가 전부 리터럴이고, 쓰는 생성자가 전부 채워질 때만(위 고정점이 이미 걸렀다)
            if (enumLike && cf != null) {
                Set<String> have = new HashSet<>();
                for (String dsc : cf.ctors.keySet()) have.add(n + "#" + dsc);
                cf.enumConsts = enumConsts(g, ft, n, have);
            }
        }
        f.byClass.values().removeIf(c -> !c.any());
        f.ctorCount = f.getterCount = f.staticCount = f.enumCount = f.enumConstCount = f.objectMethodCount = 0;
        for (ClassFill c : f.byClass.values()) {
            f.ctorCount += c.ctors.size();
            f.getterCount += c.getters.size();
            f.staticCount += c.statics.size();
            f.objectMethodCount += c.objectMethods.size();
            if (c.enumConsts != null) {
                f.enumCount++;
                f.enumConstCount += c.enumConsts.size();
            }
        }
        return f;
    }


    static boolean isEnumClass(FullShells g, String n) {
        int a = g.decl(n).access();
        return (a & Opcodes.ACC_ENUM) != 0 && (a & Opcodes.ACC_INTERFACE) == 0;
    }

    /** 열거형의 채울 상수 목록. 채울 수 없으면 null(추상 열거형·상수 몸통·리터럴 아닌 인자·채워지지 않는 생성자 사용). */
    static List<FillTable.EnumConst> enumConsts(FullShells g, FillTable ft, String n, Set<String> okCtor) {
        if ((g.decl(n).access() & Opcodes.ACC_ABSTRACT) != 0) return null;
        List<String> order = g.enumOrder(n);
        List<FillTable.EnumConst> consts = ft.enumConsts(n);
        if (order.isEmpty() || consts.size() != order.size()) return null;
        for (int i = 0; i < order.size(); i++) {
            FillTable.EnumConst ec = consts.get(i);
            if (ec.ordinal() != i || !ec.name().equals(order.get(i)) || ec.args() == null || !okCtor.contains(n + "#" + ec.ctorDesc())) return null;
        }
        return consts;
    }

    /** 단계가 가리키는 필드가 이 클래스에 있고(인스턴스, 서술자 같음), 인자 자리·리터럴이 서술자에 맞는지는 추출이 이미 봤다. */
    private static boolean stepsFit(FillTable.Ctor c, Decl d) {
        for (FillTable.Step s : c.steps()) {
            Integer acc = d.fieldAccess().get(s.field());
            if (acc == null || (acc & Opcodes.ACC_STATIC) != 0 || !s.desc().equals(d.fieldDescs().get(s.field()))) return false;
        }
        return true;
    }

    static List<String> kindCounts(Fills f) {
        List<String> r = new ArrayList<>();
        r.add("ctors=" + f.ctorCount);
        r.add("getters=" + f.getterCount);
        r.add("statics=" + f.staticCount);
        r.add("enums=" + f.enumCount + "(" + f.enumConstCount + " constants)");
        r.add("objectMethods=" + f.objectMethodCount);
        return r;
    }
}
