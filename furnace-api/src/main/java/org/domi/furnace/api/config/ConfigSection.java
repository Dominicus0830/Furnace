// 포맷에 독립적인 설정 트리 노드 — 삽입 순서를 보존하는 맵 + 점(.) 경로 기반 타입 접근 + 주석/기본값
package org.domi.furnace.api.config;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 설정 트리의 한 노드(맵). 값은 String, Boolean, Integer/Long, Double, List, 하위 {@link ConfigSection},
 * {@code java.time} 날짜/시간(TOML 전용), 또는 null 이다.
 *
 * <p><b>경로</b>: {@code "database.host"} 처럼 점(.)으로 하위 섹션을 따라간다. 파일에 점이 든 키가 literal 로
 * 존재할 수 있다(예: plugin.yml 의 {@code furnace.admin} 권한 노드). 조회는 "남은 경로 전체가 literal 키"인지를
 * 먼저 보고, 아니면 가장 짧은 접두사부터 하위 섹션으로 내려가며 시도한다. {@code set} 으로 새 경로를 만들 때는
 * 기존에 해당 키/섹션이 없으면 점마다 중첩 섹션을 만든다. literal 점 키를 새로 만들려면 {@link #setLiteral}.
 *
 * <p><b>null</b>: {@code set(path, null)} 은 키를 제거하지 않고 null 값을 저장한다(YAML {@code key:} 와 동일).
 * 제거는 {@link #remove}. 값이 null 인 키는 {@code get} 시 기본값(defaults)으로 대체된다.
 *
 * <p>스레드 안전하지 않다. 숫자는 정수면 Integer(범위 초과 시 Long), 실수면 Double 로 정규화된다.
 */
public final class ConfigSection {

    private static final Object ABSENT = new Object();

    private final LinkedHashMap<String, Object> map = new LinkedHashMap<>();
    private ConfigSection parent;
    private String key;
    private ConfigSection defaults;
    private HashMap<String, List<String>> comments;
    private HashMap<String, String> inlineComments;
    private List<String> header;
    private List<String> footer;
    /** 이 구역이 "값 하나"의 저장 꼴일 때 그 원래 객체(호환 계층의 직렬화 객체 보관). 복사·읽기·쓰기 경로는 건드리지 않는다. */
    private Object attachment;

    public ConfigSection() {}

    /** Map 을 섹션으로 변환한다(키는 literal, 값은 정규화). */
    public static ConfigSection fromMap(Map<?, ?> source) {
        ConfigSection s = new ConfigSection();
        for (Map.Entry<?, ?> e : source.entrySet()) {
            s.putChild(String.valueOf(e.getKey()), normalize(e.getValue()));
        }
        return s;
    }

    // ---------------------------------------------------------------- 조회

    /** 값 조회(없으면 defaults, 그래도 없으면 null). 빈 경로는 이 섹션 자신. */
    public Object get(String path) {
        if (path.isEmpty()) return this;
        Object v = lookup(path, 0);
        if (v == ABSENT || v == null) {
            Object d = defaultValue(path);
            return d == ABSENT ? null : d;
        }
        return v;
    }

    public Object get(String path, Object def) {
        Object v = get(path);
        return v == null ? def : v;
    }

    /** 키가 존재하는지(값이 null 이어도 true). defaults 포함. */
    public boolean contains(String path) {
        return contains(path, false);
    }

    public boolean contains(String path, boolean ignoreDefaults) {
        if (path.isEmpty()) return true;
        if (lookup(path, 0) != ABSENT) return true;
        return !ignoreDefaults && defaultValue(path) != ABSENT;
    }

    public String getString(String path) { return asString(get(path)); }

    public String getString(String path, String def) {
        String s = asString(get(path));
        return s == null ? def : s;
    }

    public int getInt(String path) { return getInt(path, 0); }

    public int getInt(String path, int def) {
        Object v = get(path);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                // 숫자가 아니면 기본값
            }
        }
        return def;
    }

    public long getLong(String path) { return getLong(path, 0L); }

    public long getLong(String path, long def) {
        Object v = get(path);
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                // 숫자가 아니면 기본값
            }
        }
        return def;
    }

    public double getDouble(String path) { return getDouble(path, 0.0); }

    public double getDouble(String path, double def) {
        Object v = get(path);
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
                // 숫자가 아니면 기본값
            }
        }
        return def;
    }

    public boolean getBoolean(String path) { return getBoolean(path, false); }

    public boolean getBoolean(String path, boolean def) {
        Object v = get(path);
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) {
            if (s.equalsIgnoreCase("true")) return true;
            if (s.equalsIgnoreCase("false")) return false;
        }
        return def;
    }

    /** 리스트(읽기 전용 뷰). 리스트가 아니면 null. */
    public List<?> getList(String path) {
        return get(path) instanceof List<?> l ? Collections.unmodifiableList(l) : null;
    }

    public List<?> getList(String path, List<?> def) {
        List<?> l = getList(path);
        return l == null ? def : l;
    }

    /** 문자열 리스트. 스칼라 원소만 문자열로 변환해 담고, 없으면 빈 리스트. */
    public List<String> getStringList(String path) {
        List<String> out = new ArrayList<>();
        if (get(path) instanceof List<?> l) {
            for (Object o : l) {
                String s = asString(o);
                if (s != null) out.add(s);
            }
        }
        return out;
    }

    /** 정수 리스트. 숫자 원소(또는 숫자 문자열)만 담는다. */
    public List<Integer> getIntegerList(String path) {
        List<Integer> out = new ArrayList<>();
        if (get(path) instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof Number n) {
                    out.add(n.intValue());
                } else if (o instanceof String s) {
                    try {
                        out.add(Integer.parseInt(s.trim()));
                    } catch (NumberFormatException ignored) {
                        // 건너뜀
                    }
                }
            }
        }
        return out;
    }

    /** 하위 섹션. 없거나 섹션이 아니면 null. */
    public ConfigSection getSection(String path) {
        return get(path) instanceof ConfigSection s ? s : null;
    }

    /** 섹션 원소만 모은 리스트(TOML 테이블 배열 등). */
    public List<ConfigSection> getSectionList(String path) {
        List<ConfigSection> out = new ArrayList<>();
        if (get(path) instanceof List<?> l) {
            for (Object o : l) if (o instanceof ConfigSection s) out.add(s);
        }
        return out;
    }

    public boolean isString(String path) { return get(path) instanceof String; }
    public boolean isNumber(String path) { return get(path) instanceof Number; }
    public boolean isBoolean(String path) { return get(path) instanceof Boolean; }
    public boolean isList(String path) { return get(path) instanceof List; }
    public boolean isSection(String path) { return get(path) instanceof ConfigSection; }

    /** 이 섹션 아래 키 목록. deep 이면 하위 섹션 키를 "a.b" 형태 경로로 재귀 포함(섹션 자신 포함). */
    public Set<String> keys(boolean deep) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        collectKeys(out, "", deep);
        return out;
    }

    public int size() { return map.size(); }

    public boolean isEmpty() { return map.isEmpty(); }

    // ---------------------------------------------------------------- 변경

    /**
     * 값 저장. 중간 섹션은 필요 시 생성한다. 지원 타입: 문자열/숫자/불리언/null/java.time/Map/Collection/배열/
     * ConfigSection (Map·컬렉션은 깊은 변환, 이미 다른 부모에 붙은 섹션은 복사). 그 외는 IllegalArgumentException.
     * 중간 경로에 섹션이 아닌 값이 있으면 새 섹션으로 교체한다.
     */
    public void set(String path, Object value) {
        if (path.isEmpty()) throw new IllegalArgumentException("빈 경로");
        setResolved(path, 0, normalize(value));
    }

    /** 점이 들어 있어도 분해하지 않고 이 섹션의 literal 키로 저장한다. */
    public void setLiteral(String key, Object value) {
        putChild(key, normalize(value));
    }

    /** 제거하고 이전 값을 반환(없으면 null). 해당 키의 주석도 함께 제거된다. */
    public Object remove(String path) {
        Loc loc = locate(path, 0);
        return loc == null ? null : loc.owner.removeKey(loc.key);
    }

    /** 하위 섹션을 가져오고, 없거나 섹션이 아니면 새로 만들어 붙인다. */
    public ConfigSection getOrCreateSection(String path) {
        Object v = lookup(path, 0);
        if (v instanceof ConfigSection s) return s;
        ConfigSection s = new ConfigSection();
        set(path, s);
        return s;
    }

    /** 깊은 복사(주석 포함, defaults 제외). 복사본은 부모가 없다. */
    public ConfigSection copy() {
        ConfigSection c = new ConfigSection();
        for (Map.Entry<String, Object> e : map.entrySet()) c.putChild(e.getKey(), copyValue(e.getValue()));
        if (comments != null) {
            c.comments = new HashMap<>();
            for (Map.Entry<String, List<String>> e : comments.entrySet()) c.comments.put(e.getKey(), new ArrayList<>(e.getValue()));
        }
        if (inlineComments != null) c.inlineComments = new HashMap<>(inlineComments);
        if (header != null) c.header = new ArrayList<>(header);
        if (footer != null) c.footer = new ArrayList<>(footer);
        return c;
    }

    /** 붙여 둔 원래 객체(없으면 null). {@link #copy()} 는 붙인 것을 옮기지 않는다. */
    public Object attachment() { return attachment; }

    /** 이 구역에 원래 객체를 붙인다(null 이면 뗀다). 구역 내용은 그 객체의 저장 꼴을 담고 있어야 한다. */
    public void attach(Object value) { this.attachment = value; }

    /** 이 구역 아래(자기 포함)에서 객체가 붙은 구역마다 action 을 부른다. 구역 자식만 따라간다(목록 안은 보지 않는다). */
    public void forEachAttached(java.util.function.Consumer<ConfigSection> action) {
        if (attachment != null) action.accept(this);
        for (Object v : new ArrayList<>(map.values())) if (v instanceof ConfigSection s) s.forEachAttached(action);
    }

    /** 자식 전체를 source 로 바꾼다(키 주석은 건드리지 않는다). 붙인 객체의 저장 꼴을 다시 쓸 때. */
    public void replaceChildren(Map<?, ?> source) {
        for (Object v : map.values()) if (v instanceof ConfigSection os) { os.parent = null; os.key = null; }
        map.clear();
        for (Map.Entry<?, ?> e : source.entrySet()) putChild(String.valueOf(e.getKey()), normalize(e.getValue()));
    }

    /** 일반 Map/List 로 깊은 변환한 스냅샷. */
    public Map<String, Object> toMap() {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : map.entrySet()) out.put(e.getKey(), plain(e.getValue()));
        return out;
    }

    // ---------------------------------------------------------------- 트리 정보

    public ConfigSection getParent() { return parent; }

    public ConfigSection getRoot() {
        ConfigSection s = this;
        while (s.parent != null) s = s.parent;
        return s;
    }

    /** 부모 안에서의 키 (루트면 빈 문자열). */
    public String getName() { return key == null ? "" : key; }

    /** 루트부터의 전체 경로 (루트면 빈 문자열). */
    public String getPath() {
        if (parent == null) return "";
        String pp = parent.getPath();
        return pp.isEmpty() ? key : pp + "." + key;
    }

    // ---------------------------------------------------------------- 키 한 단계씩 지정하는 경로(구분자가 '.' 이 아닌 호환 설정용)
    //
    // 위 메서드들은 점으로 경로를 나누고 점이 든 literal 키도 너그럽게 찾는다. 아래 메서드는 호출하는 쪽이 이미 키 단계로 나눈 목록을 준다 —
    // 단계 하나가 곧 키 하나라서 점이 든 키("minecraft:stone.x", 도메인 이름)가 그대로 한 단계다. 단계가 섹션이 아니면 없는 것으로 본다(너그러운 대체 없음).

    /** 루트부터 이 섹션까지의 키 단계(루트면 빈 목록). */
    public List<String> pathSegments() {
        if (parent == null) return List.of();
        ArrayList<String> out = new ArrayList<>(parent.pathSegments());
        out.add(key);
        return out;
    }

    private Loc locateSegments(List<String> segs) {
        ConfigSection cur = this;
        for (int i = 0; i < segs.size() - 1; i++) {
            if (!(cur.map.get(segs.get(i)) instanceof ConfigSection next)) return null;
            cur = next;
        }
        return segs.isEmpty() || !cur.map.containsKey(segs.get(segs.size() - 1)) ? null : new Loc(cur, segs.get(segs.size() - 1));
    }

    private Object lookupSegments(List<String> segs) {
        Loc loc = locateSegments(segs);
        return loc == null ? ABSENT : loc.owner.map.get(loc.key);
    }

    private Object defaultValueSegments(List<String> segs) {
        ConfigSection root = getRoot();
        if (root.defaults == null) return ABSENT;
        ArrayList<String> full = new ArrayList<>(pathSegments());
        full.addAll(segs);
        return root.defaults.lookupSegments(full);
    }

    /** 값 조회(없으면 defaults, 그래도 없으면 null). 단계가 없으면 이 섹션 자신. */
    public Object getAt(List<String> segs) {
        if (segs.isEmpty()) return this;
        Object v = lookupSegments(segs);
        if (v == ABSENT || v == null) {
            Object d = defaultValueSegments(segs);
            return d == ABSENT ? null : d;
        }
        return v;
    }

    public boolean containsAt(List<String> segs, boolean ignoreDefaults) {
        if (segs.isEmpty()) return true;
        if (lookupSegments(segs) != ABSENT) return true;
        return !ignoreDefaults && defaultValueSegments(segs) != ABSENT;
    }

    /** 값 저장. 중간 단계는 필요하면 새 섹션으로 만들고, 섹션이 아닌 값이 있으면 새 섹션으로 바꾼다. 단계가 비면 IllegalArgumentException. */
    public void setAt(List<String> segs, Object value) {
        if (segs.isEmpty()) throw new IllegalArgumentException("빈 경로");
        Object v = normalize(value);
        ConfigSection cur = this;
        for (int i = 0; i < segs.size() - 1; i++) {
            if (cur.map.get(segs.get(i)) instanceof ConfigSection next) {
                cur = next;
            } else {
                ConfigSection made = new ConfigSection();
                cur.putChild(segs.get(i), made);
                cur = made;
            }
        }
        cur.putChild(segs.get(segs.size() - 1), v);
    }

    /** 제거하고 이전 값을 돌려준다(없으면 null). 그 키의 주석도 함께 지운다. */
    public Object removeAt(List<String> segs) {
        Loc loc = locateSegments(segs);
        return loc == null ? null : loc.owner.removeKey(loc.key);
    }

    public List<String> getCommentsAt(List<String> segs) {
        Loc loc = locateSegments(segs);
        List<String> c = loc == null || loc.owner.comments == null ? null : loc.owner.comments.get(loc.key);
        return c == null ? List.of() : Collections.unmodifiableList(c);
    }

    /** 키가 존재해야 한다(없으면 IllegalArgumentException). */
    public void setCommentsAt(List<String> segs, List<String> lines) {
        Loc loc = locateSegments(segs);
        if (loc == null) throw new IllegalArgumentException("존재하지 않는 경로: " + segs);
        loc.owner.setCommentsRaw(loc.key, lines == null || lines.isEmpty() ? null : new ArrayList<>(lines));
    }

    public String getInlineCommentAt(List<String> segs) {
        Loc loc = locateSegments(segs);
        return loc == null || loc.owner.inlineComments == null ? null : loc.owner.inlineComments.get(loc.key);
    }

    public void setInlineCommentAt(List<String> segs, String comment) {
        Loc loc = locateSegments(segs);
        if (loc == null) throw new IllegalArgumentException("존재하지 않는 경로: " + segs);
        loc.owner.setInlineRaw(loc.key, comment);
    }

    /** 기본값 하나 추가(루트 defaults 의 이 섹션 아래에 생성). */
    public void addDefaultAt(List<String> segs, Object value) {
        ConfigSection root = getRoot();
        if (root.defaults == null) root.defaults = new ConfigSection();
        ArrayList<String> full = new ArrayList<>(pathSegments());
        full.addAll(segs);
        root.defaults.setAt(full, value);
    }

    // ---------------------------------------------------------------- 기본값

    /** 기본값 트리 지정(루트에서 사용). 조회 시 값이 없으면 여기서 같은 경로를 찾는다. */
    public void setDefaults(ConfigSection defaults) { this.defaults = defaults; }

    public ConfigSection getDefaults() { return defaults; }

    /** 기본값 하나 추가(루트 defaults 의 이 섹션 경로 아래에 생성). */
    public void addDefault(String path, Object value) {
        ConfigSection root = getRoot();
        if (root.defaults == null) root.defaults = new ConfigSection();
        String full = getPath();
        root.defaults.set(full.isEmpty() ? path : full + "." + path, value);
    }

    /** defaults 에는 있고 실제 값에는 없는 키를 (주석 포함) 채워 넣는다. 추가한 키 수를 반환. */
    public int copyDefaults() {
        ConfigSection root = getRoot();
        if (root.defaults == null) return 0;
        String full = getPath();
        Object d = full.isEmpty() ? root.defaults : root.defaults.lookup(full, 0);
        return d instanceof ConfigSection ds ? mergeMissing(ds) : 0;
    }

    // ---------------------------------------------------------------- 주석

    /** 키 위에 붙은 주석 줄. '#' 뒤 텍스트(앞 공백 한 칸 제거). null 원소는 빈 줄, "" 는 빈 주석(#). */
    public List<String> getComments(String path) {
        Loc loc = locate(path, 0);
        List<String> c = loc == null || loc.owner.comments == null ? null : loc.owner.comments.get(loc.key);
        return c == null ? List.of() : Collections.unmodifiableList(c);
    }

    /** 키 위 주석 설정(null/빈 리스트면 제거). 키가 존재해야 한다. */
    public void setComments(String path, List<String> lines) {
        Loc loc = requireLoc(path);
        loc.owner.setCommentsRaw(loc.key, lines == null || lines.isEmpty() ? null : new ArrayList<>(lines));
    }

    /** 값과 같은 줄 끝 주석('#' 뒤 텍스트). 없으면 null. */
    public String getInlineComment(String path) {
        Loc loc = locate(path, 0);
        return loc == null || loc.owner.inlineComments == null ? null : loc.owner.inlineComments.get(loc.key);
    }

    public void setInlineComment(String path, String comment) {
        Loc loc = requireLoc(path);
        loc.owner.setInlineRaw(loc.key, comment);
    }

    /** 파일 맨 위 주석(첫 키와 빈 줄로 분리된 블록). */
    public List<String> getHeader() {
        return header == null ? List.of() : Collections.unmodifiableList(header);
    }

    public void setHeader(List<String> lines) {
        header = lines == null || lines.isEmpty() ? null : new ArrayList<>(lines);
    }

    /** 파일 맨 아래, 마지막 키 뒤에 남은 주석. */
    public List<String> getFooter() {
        return footer == null ? List.of() : Collections.unmodifiableList(footer);
    }

    public void setFooter(List<String> lines) {
        footer = lines == null || lines.isEmpty() ? null : new ArrayList<>(lines);
    }

    // ---------------------------------------------------------------- Object

    /** 데이터 동등성 — 키/값만 비교한다(키 순서·주석·defaults 는 무시). */
    @Override
    public boolean equals(Object o) {
        return o == this || (o instanceof ConfigSection s && map.equals(s.map));
    }

    @Override
    public int hashCode() { return map.hashCode(); }

    @Override
    public String toString() { return map.toString(); }

    // ---------------------------------------------------------------- 패키지 내부 (파서/작성기용)

    boolean hasKey(String k) { return map.containsKey(k); }

    Object rawGet(String k) { return map.get(k); }

    /** 이미 정규화된 값을 literal 키로 넣는다(파서 전용). */
    void putRaw(String k, Object v) { putChild(k, v); }

    Map<String, Object> rawMap() { return Collections.unmodifiableMap(map); }

    List<String> rawComments(String k) { return comments == null ? null : comments.get(k); }

    String rawInline(String k) { return inlineComments == null ? null : inlineComments.get(k); }

    void setCommentsRaw(String k, List<String> lines) {
        if (lines == null) {
            if (comments != null) comments.remove(k);
            return;
        }
        if (comments == null) comments = new HashMap<>();
        comments.put(k, lines);
    }

    void setInlineRaw(String k, String c) {
        if (c == null) {
            if (inlineComments != null) inlineComments.remove(k);
            return;
        }
        if (inlineComments == null) inlineComments = new HashMap<>();
        inlineComments.put(k, c);
    }

    /** long 을 Integer(범위 내) 또는 Long 으로 정규화한다. */
    static Object intOrLong(long v) {
        return v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE ? (Object) (int) v : (Object) v;
    }

    static Object copyValue(Object v) {
        if (v instanceof ConfigSection s) return s.copy();
        if (v instanceof List<?> l) {
            ArrayList<Object> out = new ArrayList<>(l.size());
            for (Object o : l) out.add(copyValue(o));
            return out;
        }
        return v;
    }

    // ---------------------------------------------------------------- 내부 구현

    private static final class Loc {
        final ConfigSection owner;
        final String key;

        Loc(ConfigSection owner, String key) {
            this.owner = owner;
            this.key = key;
        }
    }

    private Loc requireLoc(String path) {
        Loc loc = locate(path, 0);
        if (loc == null) throw new IllegalArgumentException("존재하지 않는 경로: " + path);
        return loc;
    }

    /** 경로 해석: 남은 경로 전체가 literal 키면 그것, 아니면 짧은 접두사부터 하위 섹션으로 재귀. */
    private Object lookup(String path, int from) {
        String rest = from == 0 ? path : path.substring(from);
        Object v = map.get(rest);
        if (v != null || map.containsKey(rest)) return v;
        for (int dot = path.indexOf('.', from); dot >= 0; dot = path.indexOf('.', dot + 1)) {
            if (map.get(path.substring(from, dot)) instanceof ConfigSection s) {
                Object r = s.lookup(path, dot + 1);
                if (r != ABSENT) return r;
            }
        }
        return ABSENT;
    }

    private Loc locate(String path, int from) {
        String rest = from == 0 ? path : path.substring(from);
        if (map.containsKey(rest)) return new Loc(this, rest);
        for (int dot = path.indexOf('.', from); dot >= 0; dot = path.indexOf('.', dot + 1)) {
            if (map.get(path.substring(from, dot)) instanceof ConfigSection s) {
                Loc r = s.locate(path, dot + 1);
                if (r != null) return r;
            }
        }
        return null;
    }

    private void setResolved(String path, int from, Object v) {
        String rest = from == 0 ? path : path.substring(from);
        if (map.containsKey(rest)) {
            putChild(rest, v);
            return;
        }
        for (int dot = path.indexOf('.', from); dot >= 0; dot = path.indexOf('.', dot + 1)) {
            if (map.get(path.substring(from, dot)) instanceof ConfigSection s) {
                s.setResolved(path, dot + 1, v);
                return;
            }
        }
        int dot = path.indexOf('.', from);
        if (dot < 0) {
            putChild(rest, v);
            return;
        }
        ConfigSection s = new ConfigSection();
        putChild(path.substring(from, dot), s);
        s.setResolved(path, dot + 1, v);
    }

    private void putChild(String k, Object v) {
        Object old = map.put(k, v);
        if (old instanceof ConfigSection os && os != v) {
            os.parent = null;
            os.key = null;
        }
        if (v instanceof ConfigSection s) {
            s.parent = this;
            s.key = k;
        }
    }

    private Object removeKey(String k) {
        Object old = map.remove(k);
        if (old instanceof ConfigSection os) {
            os.parent = null;
            os.key = null;
        }
        if (comments != null) comments.remove(k);
        if (inlineComments != null) inlineComments.remove(k);
        return old;
    }

    private Object defaultValue(String path) {
        ConfigSection root = getRoot();
        if (root.defaults == null) return ABSENT;
        String full = getPath();
        return root.defaults.lookup(full.isEmpty() ? path : full + "." + path, 0);
    }

    private void collectKeys(Set<String> out, String prefix, boolean deep) {
        for (Map.Entry<String, Object> e : map.entrySet()) {
            String k = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            out.add(k);
            if (deep && e.getValue() instanceof ConfigSection s) s.collectKeys(out, k, true);
        }
    }

    private int mergeMissing(ConfigSection src) {
        int added = 0;
        for (Map.Entry<String, Object> e : src.map.entrySet()) {
            String k = e.getKey();
            Object sv = e.getValue();
            if (!map.containsKey(k)) {
                putChild(k, copyValue(sv));
                List<String> c = src.rawComments(k);
                if (c != null) setCommentsRaw(k, new ArrayList<>(c));
                String ic = src.rawInline(k);
                if (ic != null) setInlineRaw(k, ic);
                added++;
            } else if (sv instanceof ConfigSection ss && map.get(k) instanceof ConfigSection ts) {
                added += ts.mergeMissing(ss);
            }
        }
        return added;
    }

    private static String asString(Object v) {
        if (v == null || v instanceof ConfigSection || v instanceof List) return null;
        return v.toString();
    }

    private static Object plain(Object v) {
        if (v instanceof ConfigSection s) return s.toMap();
        if (v instanceof List<?> l) {
            ArrayList<Object> out = new ArrayList<>(l.size());
            for (Object o : l) out.add(plain(o));
            return out;
        }
        return v;
    }

    /** 외부 값을 트리 허용 타입으로 정규화한다. */
    static Object normalize(Object v) {
        if (v == null || v instanceof String || v instanceof Boolean || v instanceof Integer || v instanceof Double) return v;
        if (v instanceof Long l) return intOrLong(l);
        if (v instanceof Short || v instanceof Byte) return ((Number) v).intValue();
        if (v instanceof Float f) return Double.valueOf(f.toString());
        if (v instanceof CharSequence || v instanceof Character || v instanceof UUID) return v.toString();
        if (v instanceof Enum<?> e) return e.name();
        if (v instanceof OffsetDateTime || v instanceof LocalDateTime || v instanceof LocalDate || v instanceof LocalTime) return v;
        if (v instanceof ConfigSection s) return s.parent != null ? s.copy() : s;
        if (v instanceof Map<?, ?> m) return fromMap(m);
        if (v instanceof Collection<?> c) {
            ArrayList<Object> out = new ArrayList<>(c.size());
            for (Object o : c) out.add(normalize(o));
            return out;
        }
        if (v instanceof Object[] arr) {
            ArrayList<Object> out = new ArrayList<>(arr.length);
            for (Object o : arr) out.add(normalize(o));
            return out;
        }
        throw new IllegalArgumentException("지원하지 않는 설정 값 타입: " + v.getClass().getName());
    }
}
