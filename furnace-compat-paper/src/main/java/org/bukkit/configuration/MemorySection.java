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
        if (v instanceof ConfigSection s) return isSerialized(s) ? revive(s.toMap()) : wrap(s);
        if (v instanceof List<?> l) {
            boolean convert = false;
            for (Object o : l) if (o instanceof ConfigSection) { convert = true; break; }
            if (!convert) return v;
            List<Object> r = new ArrayList<>(l.size());
            for (Object o : l) r.add(o instanceof ConfigSection s ? revive(s.toMap()) : o);
            return r;
        }
        return v;
    }

    /** 별칭(==)이 등록된 ConfigurationSerializable 로 저장된 구역인가 — Bukkit 에서는 구역이 아니라 객체다. */
    private static boolean isSerialized(ConfigSection s) {
        return s.get(ConfigurationSerialization.SERIALIZED_TYPE_KEY) instanceof String alias && ConfigurationSerialization.getClassByAlias(alias) != null;
    }

    /**
     * 맵 → 값: 안쪽 맵·리스트부터 되읽고, 별칭이 등록된 {@code ==} 맵은 ConfigurationSerializable 로 되읽는다(Bukkit 의 YAML 읽기와 같은 순서).
     * 되읽지 못하면(별칭을 모르거나 되읽기가 null) 맵 그대로 둔다.
     */
    private static Object revive(Object v) {
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> plain = new LinkedHashMap<>();
            for (var e : m.entrySet()) plain.put(String.valueOf(e.getKey()), revive(e.getValue()));
            if (plain.get(ConfigurationSerialization.SERIALIZED_TYPE_KEY) instanceof String alias && ConfigurationSerialization.getClassByAlias(alias) != null) {
                ConfigurationSerializable obj = ConfigurationSerialization.deserializeObject(plain);
                if (obj != null) return obj;
            }
            return plain;
        }
        if (v instanceof List<?> l) {
            List<Object> out = new ArrayList<>(l.size());
            for (Object o : l) out.add(revive(o));
            return out;
        }
        return v;
    }

    /** 값 → 저장할 꼴: ConfigurationSerializable 은 {@code ==} 별칭이 맨 앞인 맵으로(안쪽도 같게), 집합은 리스트로. */
    private static Object in(Object v) {
        if (v instanceof MemorySection m) return m.delegate;
        if (v instanceof ConfigurationSection s) return s.getValues(false);
        if (v instanceof ConfigurationSerializable c) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put(ConfigurationSerialization.SERIALIZED_TYPE_KEY, ConfigurationSerialization.getAlias(c.getClass()));
            for (var e : c.serialize().entrySet()) m.put(e.getKey(), in(e.getValue()));
            return m;
        }
        if (v instanceof Map<?, ?> map) {
            Map<Object, Object> out = new LinkedHashMap<>();
            for (var e : map.entrySet()) out.put(e.getKey(), in(e.getValue()));
            return out;
        }
        if (v instanceof Iterable<?> it && !(v instanceof ConfigSection)) {
            List<Object> out = new ArrayList<>();
            for (Object o : it) out.add(in(o));
            return out;
        }
        return v;
    }

    // ------------------------------------------------------------------ Paper 읽기 규칙(A15-03)
    //
    // Paper MemorySection: get(path, def) 는 자기 데이터에 없으면 def 다(기본값 구역은 보지 않는다). 인자 하나짜리만 기본값 구역의 값을 def 로 넘긴다.
    // getInt·getBoolean 등은 값이 그 종류(Number·Boolean)가 아니면 def — 따옴표 친 '5' 는 숫자가 아니다.

    /** 자기 데이터의 값(기본값 구역 제외). 없으면 null. */
    private Object own(String path) {
        return delegate.contains(path, true) ? out(delegate.get(path)) : null;
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
        out.addAll(delegate.keys(deep));
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
        for (String k : delegate.keys(deep)) {
            out.remove(k);
            out.put(k, get(k));
        }
        return out;
    }

    @Override
    public boolean contains(String path) { return delegate.contains(path); }

    @Override
    public boolean contains(String path, boolean ignoreDefault) { return delegate.contains(path, ignoreDefault); }

    @Override
    public boolean isSet(String path) { return delegate.contains(path, !root.options().copyDefaults()); }

    @Override
    public String getCurrentPath() { return delegate.getPath(); }

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
        if (value == null) {
            delegate.remove(path);
            return;
        }
        delegate.set(path, in(value));
    }

    @Override
    public ConfigurationSection createSection(String path) {
        delegate.set(path, new ConfigSection());
        return getConfigurationSection(path);
    }

    @Override
    public ConfigurationSection createSection(String path, Map<?, ?> map) {
        delegate.set(path, ConfigSection.fromMap(map));
        return getConfigurationSection(path);
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
        return delegate.get(path) instanceof ConfigSection s && !isSerialized(s) ? wrap(s) : null;
    }

    @Override
    public boolean isConfigurationSection(String path) { return delegate.get(path) instanceof ConfigSection s && !isSerialized(s); }

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
    public org.bukkit.Color getColor(String path) { return getSerializable(path, org.bukkit.Color.class); }

    @Override
    public org.bukkit.Color getColor(String path, org.bukkit.Color def) { return getSerializable(path, org.bukkit.Color.class, def); }

    @Override
    public boolean isColor(String path) { return getSerializable(path, org.bukkit.Color.class) != null; }

    @Override
    public ConfigurationSection getDefaultSection() {
        Configuration d = root.getDefaults();
        if (d == null) return null;
        String p = delegate.getPath();
        return p.isEmpty() ? d : d.getConfigurationSection(p);
    }

    @Override
    public void addDefault(String path, Object value) { delegate.addDefault(path, in(value)); }

    // ------------------------------------------------------------------ 주석

    @Override
    public List<String> getComments(String path) { return delegate.contains(path, true) ? delegate.getComments(path) : List.of(); }

    @Override
    public List<String> getInlineComments(String path) {
        String c = delegate.contains(path, true) ? delegate.getInlineComment(path) : null;
        return c == null ? List.of() : List.of(c);
    }

    @Override
    public void setComments(String path, List<String> comments) {
        if (delegate.contains(path, true)) delegate.setComments(path, comments);
    }

    @Override
    public void setInlineComments(String path, List<String> comments) {
        if (delegate.contains(path, true)) delegate.setInlineComment(path, comments == null || comments.isEmpty() ? null : comments.get(0));
    }

    @Override
    public String toString() {
        ConfigurationSection r = getRoot();
        return getClass().getSimpleName() + "[path='" + getCurrentPath() + "', root='" + (r == null ? null : r.getClass().getSimpleName()) + "']";
    }
}
