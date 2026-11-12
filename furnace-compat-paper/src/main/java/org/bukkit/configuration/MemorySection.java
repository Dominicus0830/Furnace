package org.bukkit.configuration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.NumberConversions;
import org.domi.furnace.compat.paper.ConfigObject;
import org.domi.furnace.compat.paper.ConfigPaths;
import org.domi.furnace.compat.paper.ConfigValues;
import org.domi.furnace.api.config.ConfigSection;

/**
 * 설정 구역 — Furnace 의 {@link ConfigSection}(주석·기본값 포함) 위에 Bukkit ConfigurationSection 의 모양을 입힌다.
 * 섹션 값은 {@link ConfigurationSection}, 리스트 안의 맵은 {@link Map} 으로 돌려준다.
 */
public class MemorySection implements ConfigurationSection {

    /** 루트에서만 바뀐다(FileConfiguration 이 다시 읽을 때). */
    ConfigSection delegate;
    private final ConfigurationSection parent;
    private final Configuration root;

    /** 루트 구역 전용 — 하위 클래스가 Configuration 이어야 한다. */
    protected MemorySection() {
        if (!(this instanceof Configuration)) throw new IllegalStateException("Cannot construct a root MemorySection when not a Configuration");
        this.delegate = new ConfigSection();
        this.parent = null;
        this.root = (Configuration) this;
    }

    MemorySection(ConfigSection delegate, ConfigurationSection parent, Configuration root) {
        this.delegate = delegate;
        this.parent = parent;
        this.root = root;
    }

    // ------------------------------------------------------------------ 변환

    private ConfigurationSection wrap(ConfigSection s) { return new MemorySection(s, this, root); }

    private Object out(Object v) {
        // 직렬화 객체는 트리의 잎이다 — 넣은 객체(읽어 온 것은 읽을 때 되살린 객체) 그 참조를 돌려준다(Paper MemorySection 과 같다)
        if (v instanceof ConfigObject o) return o.value();
        if (v instanceof ConfigSection s) return wrap(s);
        if (v instanceof List<?> l) return ConfigValues.needsOut(l) ? ConfigValues.plainOut(l) : v;
        return v;
    }

    private static Object in(Object v) { return org.domi.furnace.compat.paper.ConfigValues.lower(v); }

    /** 호환 모듈 전용 — 이 구역이 감싼 Furnace 구역. */
    public ConfigSection furnaceSection() { return delegate; }

    // ------------------------------------------------------------------ 경로 구분자(A15-05)
    //
    // Paper 는 루트 옵션의 pathSeparator 로 경로를 단계로 나눈다. 기본 '.' 는 Furnace ConfigSection 의 점 경로(점이 든 literal 키도 너그럽게 찾는다)를
    // 그대로 쓰고, 다른 문자는 단계 목록을 만들어 ConfigSection 의 *At 메서드로 넘긴다 — 단계 하나가 키 하나라 점이 든 키가 그대로 한 단계다.

    private char sep() { return root.options().pathSeparator(); }

    private boolean custom() { return sep() != '.'; }

    private ConfigPaths.Parsed parse(String path) { return ConfigPaths.parse(path, sep()); }

    /** 읽을 때 모르던 별칭이 그 뒤 등록됐으면 트리를 한 번에 되살린다(읽기 입구마다 — 키를 모으거나 값을 꺼내기 전에). 남은 것이 없으면 아무것도 안 한다. */
    private void reviveLate() {
        if (root instanceof MemoryConfiguration mc && mc.pendingAliases()) mc.pendingAliases(ConfigValues.reviveTree(mc.furnaceRoot()));
    }

    /** 이 구역 데이터에서 path 가 가리키는 값(기본값 제외하려면 dHas 로 먼저 확인). 사용자 지정 구분자의 끝 구분자는 구역일 때만 그 구역. */
    private Object dGet(String path) {
        reviveLate();
        if (!custom()) return delegate.get(path);
        ConfigPaths.Parsed p = parse(path);
        if (p.keys().isEmpty()) return delegate;
        Object v = delegate.getAt(p.keys());
        return p.endsAtSection() && !(v instanceof ConfigSection) ? null : v;
    }

    /** 자기 데이터(기본값 제외)에 path 가 있는가. */
    private boolean dHas(String path) {
        reviveLate();
        if (!custom()) return delegate.contains(path, true);
        ConfigPaths.Parsed p = parse(path);
        if (p.keys().isEmpty()) return true;
        return delegate.containsAt(p.keys(), true) && (!p.endsAtSection() || delegate.getAt(p.keys()) instanceof ConfigSection);
    }

    /** Paper 처럼: 값이 null 이면 지운다(없는 중간 구역은 만들지 않는다). */
    private void dSet(String path, Object value) {
        if (!custom()) {
            if (value == null) delegate.remove(path);
            else delegate.set(path, value);
            return;
        }
        if (path.isEmpty()) throw new IllegalArgumentException("Cannot set to an empty path");
        ConfigPaths.Parsed p = parse(path);
        if (!p.endsAtSection()) {
            if (value == null) delegate.removeAt(p.keys());
            else delegate.setAt(p.keys(), value);
            return;
        }
        // 끝 구분자: Paper 는 중간 구역을 먼저 만들고(지울 때는 없으면 조용히 끝) 빈 마지막 키에서 IllegalArgumentException
        if (value == null && !allSections(p.keys())) return;
        if (value != null) ensureSections(p.keys());
        throw new IllegalArgumentException("Cannot set to an empty path");
    }

    private boolean allSections(List<String> keys) {
        for (int i = 1; i <= keys.size(); i++) {
            List<String> prefix = keys.subList(0, i);
            if (!(delegate.containsAt(prefix, true) && delegate.getAt(prefix) instanceof ConfigSection)) return false;
        }
        return true;
    }

    private void ensureSections(List<String> keys) {
        for (int i = 1; i <= keys.size(); i++) {
            List<String> prefix = keys.subList(0, i);
            if (!(delegate.containsAt(prefix, true) && delegate.getAt(prefix) instanceof ConfigSection)) delegate.setAt(prefix, new ConfigSection());
        }
    }

    /** 주석 조회·수정용 키 단계(경로가 비거나 끝 구분자로 끝나면 null — Paper 는 그때 빈 목록·무동작). */
    private List<String> commentKeys(String path) {
        ConfigPaths.Parsed p = parse(path);
        return p.keys().isEmpty() || p.endsAtSection() ? null : p.keys();
    }

    /** 이 구역 아래 키(deep 이면 하위 구역 키를 구분자로 이어서). */
    private Set<String> ownKeys(boolean deep) {
        reviveLate();
        if (!custom()) return delegate.keys(deep);
        Set<String> out = new java.util.LinkedHashSet<>();
        collectKeys(out, delegate, "", deep, sep());
        return out;
    }

    private static void collectKeys(Set<String> out, ConfigSection section, String prefix, boolean deep, char sep) {
        for (String k : section.keys(false)) {
            String full = prefix.isEmpty() ? k : prefix + sep + k;
            out.add(full);
            if (deep && section.getAt(List.of(k)) instanceof ConfigSection child) collectKeys(out, child, full, true, sep);
        }
    }

    // ------------------------------------------------------------------ Paper 읽기 규칙(A15-03)
    //
    // Paper MemorySection: get(path, def) 는 자기 데이터에 없으면 def 다(기본값 구역은 보지 않는다). 인자 하나짜리만 기본값 구역의 값을 def 로 넘긴다.
    // getInt·getBoolean 등은 값이 그 종류(Number·Boolean)가 아니면 def — 따옴표 친 '5' 는 숫자가 아니다.

    /** 자기 데이터의 값(기본값 구역 제외). 없으면 null. */
    private Object own(String path) {
        return dHas(path) ? out(dGet(path)) : null;
    }

    /** Paper getDefault: 기본값 구역의 같은 경로 값(없으면 null). */
    private Object defaultOf(String path) {
        ConfigurationSection d = getDefaultSection();
        return d == null ? null : d.get(path);
    }

    // ------------------------------------------------------------------ 조회

    @Override
    public Set<String> getKeys(boolean deep) {
        // Paper 처럼 copyDefaults 면 기본값 구역 키를 먼저 넣고 자기 키를 잇는다 — 보기만 합치고 데이터는 바꾸지 않는다(A15-11)
        Set<String> out = new java.util.LinkedHashSet<>();
        if (root.options().copyDefaults()) {
            ConfigurationSection d = getDefaultSection();
            if (d != null) out.addAll(d.getKeys(deep));
        }
        out.addAll(ownKeys(deep));
        return out;
    }

    @Override
    public Map<String, Object> getValues(boolean deep) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (root.options().copyDefaults()) {
            ConfigurationSection d = getDefaultSection();
            if (d != null) out.putAll(d.getValues(deep));
        }
        // Paper(SPIGOT-4558): 자기 키는 remove 뒤 put — 기본값에만 있는 키가 앞에 남는다
        for (String k : ownKeys(deep)) {
            out.remove(k);
            out.put(k, get(k));
        }
        return out;
    }

    /** Paper 처럼 get 과 같은 길로 정한다(기본값 설정의 구분자가 달라도 get 과 어긋나지 않게 — PR #254 검토 R3). */
    @Override
    public boolean contains(String path) { return get(path) != null; }

    @Override
    public boolean contains(String path, boolean ignoreDefault) { return (ignoreDefault ? get(path, null) : get(path)) != null; }

    @Override
    public boolean isSet(String path) { return root.options().copyDefaults() ? contains(path) : get(path, null) != null; }

    @Override
    public String getCurrentPath() { return custom() ? String.join(String.valueOf(sep()), delegate.pathSegments()) : delegate.getPath(); }

    @Override
    public String getName() { return delegate.getName(); }

    @Override
    public Configuration getRoot() { return root; }

    @Override
    public ConfigurationSection getParent() { return parent; }

    @Override
    public Object get(String path) {
        if (path.isEmpty()) return this;
        return get(path, defaultOf(path));
    }

    @Override
    public Object get(String path, Object def) {
        if (path.isEmpty()) return this;
        Object v = own(path);
        return v == null ? def : v;
    }

    @Override
    public void set(String path, Object value) {
        // 직렬화 객체는 잎으로 참조를 보관한다(Paper MemorySection.set 과 같다) — serialize() 는 쓸 때만 부른다
        dSet(path, value == null ? null : in(value));
    }

    @Override
    public ConfigurationSection createSection(String path) {
        if (path.isEmpty()) throw new IllegalArgumentException("Cannot create section at empty path");
        dSet(path, new ConfigSection());
        return getConfigurationSection(path);
    }

    @Override
    public ConfigurationSection createSection(String path, Map<?, ?> map) {
        if (!custom()) {
            dSet(path, ConfigSection.fromMap(map));
            return getConfigurationSection(path);
        }
        // 사용자 지정 구분자: Paper 처럼 맵 키도 그 구분자로 쪼개 넣는다
        ConfigurationSection section = createSection(path);
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (e.getValue() instanceof Map<?, ?> inner) section.createSection(String.valueOf(e.getKey()), inner);
            else section.set(String.valueOf(e.getKey()), e.getValue());
        }
        return section;
    }

    @Override
    public String getString(String path) {
        Object def = defaultOf(path);
        return getString(path, def != null ? def.toString() : null);
    }

    @Override
    public String getString(String path, String def) {
        Object v = get(path, def);
        return v != null ? v.toString() : def;
    }

    @Override
    public boolean isString(String path) { return get(path) instanceof String; }

    @Override
    public int getInt(String path) {
        return getInt(path, defaultOf(path) instanceof Number n ? NumberConversions.toInt(n) : 0);
    }

    @Override
    public int getInt(String path, int def) {
        return get(path, def) instanceof Number n ? NumberConversions.toInt(n) : def;
    }

    @Override
    public boolean isInt(String path) { return get(path) instanceof Integer; }

    @Override
    public boolean getBoolean(String path) {
        return getBoolean(path, defaultOf(path) instanceof Boolean b && b);
    }

    @Override
    public boolean getBoolean(String path, boolean def) {
        return get(path, def) instanceof Boolean b ? b : def;
    }

    @Override
    public boolean isBoolean(String path) { return get(path) instanceof Boolean; }

    @Override
    public double getDouble(String path) {
        return getDouble(path, defaultOf(path) instanceof Number n ? NumberConversions.toDouble(n) : 0);
    }

    @Override
    public double getDouble(String path, double def) {
        return get(path, def) instanceof Number n ? NumberConversions.toDouble(n) : def;
    }

    @Override
    public boolean isDouble(String path) { return get(path) instanceof Double; }

    @Override
    public long getLong(String path) {
        return getLong(path, defaultOf(path) instanceof Number n ? NumberConversions.toLong(n) : 0);
    }

    @Override
    public long getLong(String path, long def) {
        return get(path, def) instanceof Number n ? NumberConversions.toLong(n) : def;
    }

    @Override
    public boolean isLong(String path) { return get(path) instanceof Long; }

    @Override
    public List<?> getList(String path) {
        return getList(path, defaultOf(path) instanceof List<?> l ? l : null);
    }

    @Override
    public List<?> getList(String path, List<?> def) {
        return get(path, def) instanceof List<?> l ? l : def;
    }

    @Override
    public boolean isList(String path) { return get(path) instanceof List; }

    private static boolean isPrimitiveWrapper(Object o) {
        return o instanceof Integer || o instanceof Short || o instanceof Byte || o instanceof Long || o instanceof Double
                || o instanceof Float || o instanceof Boolean || o instanceof Character;
    }

    @Override
    public List<String> getStringList(String path) {
        List<String> out = new ArrayList<>();
        if (getList(path) instanceof List<?> l) for (Object o : l) if (o instanceof String || isPrimitiveWrapper(o)) out.add(String.valueOf(o));
        return out;
    }

    @Override
    public List<Integer> getIntegerList(String path) {
        List<Integer> out = new ArrayList<>();
        if (getList(path) instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof Integer i) out.add(i);
                else if (o instanceof String s) {
                    try {
                        out.add(Integer.valueOf(s));
                    } catch (NumberFormatException ignored) {
                        // 건너뜀(Paper 와 같다)
                    }
                } else if (o instanceof Character c) out.add((int) c);
                else if (o instanceof Number n) out.add(n.intValue());
            }
        }
        return out;
    }

    @Override
    public List<Boolean> getBooleanList(String path) {
        List<Boolean> out = new ArrayList<>();
        if (getList(path) instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof Boolean b) out.add(b);
                else if ("true".equals(o)) out.add(true);    // Paper 는 대소문자를 가린다
                else if ("false".equals(o)) out.add(false);
            }
        }
        return out;
    }

    @Override
    public List<Double> getDoubleList(String path) {
        List<Double> out = new ArrayList<>();
        if (getList(path) instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof Double d) out.add(d);
                else if (o instanceof String s) {
                    try {
                        out.add(Double.valueOf(s));
                    } catch (NumberFormatException ignored) {
                        // 건너뜀
                    }
                } else if (o instanceof Character c) out.add((double) c);
                else if (o instanceof Number n) out.add(n.doubleValue());
            }
        }
        return out;
    }

    @Override
    public List<Long> getLongList(String path) {
        List<Long> out = new ArrayList<>();
        if (getList(path) instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof Long v) out.add(v);
                else if (o instanceof String s) {
                    try {
                        out.add(Long.valueOf(s));
                    } catch (NumberFormatException ignored) {
                        // 건너뜀
                    }
                } else if (o instanceof Character c) out.add((long) c);
                else if (o instanceof Number n) out.add(n.longValue());
            }
        }
        return out;
    }

    @Override
    public List<Map<?, ?>> getMapList(String path) {
        List<Map<?, ?>> out = new ArrayList<>();
        if (getList(path) instanceof List<?> l) for (Object o : l) if (o instanceof Map<?, ?> m) out.add(m);
        return out;
    }

    @Override
    public <T> T getObject(String path, Class<T> clazz) {
        Object def = defaultOf(path);
        return getObject(path, clazz, clazz.isInstance(def) ? clazz.cast(def) : null);
    }

    @Override
    public <T> T getObject(String path, Class<T> clazz, T def) {
        Object v = get(path, def);
        return clazz.isInstance(v) ? clazz.cast(v) : def;
    }

    @Override
    public ConfigurationSection getConfigurationSection(String path) {
        return dGet(path) instanceof ConfigSection s ? wrap(s) : null;
    }

    @Override
    public boolean isConfigurationSection(String path) { return dGet(path) instanceof ConfigSection; }

    // ------------------------------------------------------------------ 직렬화 객체

    @Override
    public <T extends ConfigurationSerializable> T getSerializable(String path, Class<T> clazz) { return getObject(path, clazz); }

    @Override
    public <T extends ConfigurationSerializable> T getSerializable(String path, Class<T> clazz, T def) { return getObject(path, clazz, def); }

    @Override
    public ItemStack getItemStack(String path) { return getSerializable(path, ItemStack.class); }

    @Override
    public ItemStack getItemStack(String path, ItemStack def) { return getSerializable(path, ItemStack.class, def); }

    @Override
    public boolean isItemStack(String path) { return get(path) instanceof ItemStack; }

    @Override
    public org.bukkit.Location getLocation(String path) { return getSerializable(path, org.bukkit.Location.class); }

    @Override
    public org.bukkit.Location getLocation(String path, org.bukkit.Location def) { return getSerializable(path, org.bukkit.Location.class, def); }

    @Override
    public boolean isLocation(String path) { return getSerializable(path, org.bukkit.Location.class) != null; }

    @Override
    public org.bukkit.util.Vector getVector(String path) { return getSerializable(path, org.bukkit.util.Vector.class); }

    @Override
    public org.bukkit.util.Vector getVector(String path, org.bukkit.util.Vector def) { return getSerializable(path, org.bukkit.util.Vector.class, def); }

    @Override
    public boolean isVector(String path) { return getSerializable(path, org.bukkit.util.Vector.class) != null; }

    @Override
    public org.bukkit.OfflinePlayer getOfflinePlayer(String path) { return getSerializable(path, org.bukkit.OfflinePlayer.class); }

    @Override
    public org.bukkit.OfflinePlayer getOfflinePlayer(String path, org.bukkit.OfflinePlayer def) { return getSerializable(path, org.bukkit.OfflinePlayer.class, def); }

    @Override
    public boolean isOfflinePlayer(String path) { return getSerializable(path, org.bukkit.OfflinePlayer.class) != null; }

    @Override
    public org.bukkit.Color getColor(String path) { return getSerializable(path, org.bukkit.Color.class); }

    @Override
    public org.bukkit.Color getColor(String path, org.bukkit.Color def) { return getSerializable(path, org.bukkit.Color.class, def); }

    @Override
    public boolean isColor(String path) { return getSerializable(path, org.bukkit.Color.class) != null; }

    @Override
    public ConfigurationSection getDefaultSection() {
        Configuration d = root.getDefaults();
        if (d == null) return null;
        String p = getCurrentPath();
        return p.isEmpty() ? d : d.getConfigurationSection(p);
    }

    @Override
    public void addDefault(String path, Object value) {
        if (!custom()) {
            delegate.addDefault(path, in(value));
            return;
        }
        if (path.isEmpty()) throw new IllegalArgumentException("Cannot set to an empty path");
        delegate.addDefaultAt(parse(path).keys(), in(value));
    }

    // ------------------------------------------------------------------ 주석

    @Override
    public List<String> getComments(String path) {
        if (!dHas(path)) return List.of();
        if (!custom()) return delegate.getComments(path);
        List<String> keys = commentKeys(path);
        return keys == null ? List.of() : delegate.getCommentsAt(keys);
    }

    @Override
    public List<String> getInlineComments(String path) {
        if (!dHas(path)) return List.of();
        String c;
        if (!custom()) c = delegate.getInlineComment(path);
        else {
            List<String> keys = commentKeys(path);
            c = keys == null ? null : delegate.getInlineCommentAt(keys);
        }
        return c == null ? List.of() : List.of(c);
    }

    @Override
    public void setComments(String path, List<String> comments) {
        if (!dHas(path)) return;
        if (!custom()) {
            delegate.setComments(path, comments);
            return;
        }
        List<String> keys = commentKeys(path);
        if (keys != null) delegate.setCommentsAt(keys, comments);
    }

    @Override
    public void setInlineComments(String path, List<String> comments) {
        if (!dHas(path)) return;
        String c = comments == null || comments.isEmpty() ? null : comments.get(0);
        if (!custom()) {
            delegate.setInlineComment(path, c);
            return;
        }
        List<String> keys = commentKeys(path);
        if (keys != null) delegate.setInlineCommentAt(keys, c);
    }

    @Override
    public String toString() {
        ConfigurationSection r = getRoot();
        return getClass().getSimpleName() + "[path='" + getCurrentPath() + "', root='" + (r == null ? null : r.getClass().getSimpleName()) + "']";
    }
}
