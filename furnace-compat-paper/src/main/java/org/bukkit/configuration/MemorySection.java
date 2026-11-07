package org.bukkit.configuration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
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
        if (v instanceof ConfigSection s) return wrap(s);
        if (v instanceof List<?> l) {
            boolean convert = false;
            for (Object o : l) if (o instanceof ConfigSection) { convert = true; break; }
            if (!convert) return v;
            List<Object> r = new ArrayList<>(l.size());
            for (Object o : l) r.add(o instanceof ConfigSection s ? s.toMap() : o);
            return r;
        }
        return v;
    }

    private static Object in(Object v) {
        if (v instanceof MemorySection m) return m.delegate;
        if (v instanceof ConfigurationSection s) return s.getValues(false);
        if (v instanceof ConfigurationSerializable c) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("==", c.getClass().getName());
            m.putAll(c.serialize());
            return m;
        }
        if (v instanceof Set<?> set) return new ArrayList<>(set);
        return v;
    }

    private void applyCopyDefaults() {
        if (root.options().copyDefaults()) delegate.copyDefaults();
    }

    // ------------------------------------------------------------------ 조회

    @Override
    public Set<String> getKeys(boolean deep) {
        applyCopyDefaults();
        return delegate.keys(deep);
    }

    @Override
    public Map<String, Object> getValues(boolean deep) {
        applyCopyDefaults();
        Map<String, Object> out = new LinkedHashMap<>();
        for (String k : delegate.keys(deep)) out.put(k, get(k));
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
    public Object get(String path) { return out(delegate.get(path)); }

    @Override
    public Object get(String path, Object def) {
        Object v = get(path);
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
    public String getString(String path) { return delegate.getString(path); }

    @Override
    public String getString(String path, String def) { return delegate.getString(path, def); }

    @Override
    public boolean isString(String path) { return delegate.get(path) instanceof String; }

    @Override
    public int getInt(String path) { return delegate.getInt(path); }

    @Override
    public int getInt(String path, int def) { return delegate.getInt(path, def); }

    @Override
    public boolean isInt(String path) { return delegate.get(path) instanceof Integer; }

    @Override
    public boolean getBoolean(String path) { return delegate.getBoolean(path); }

    @Override
    public boolean getBoolean(String path, boolean def) { return delegate.getBoolean(path, def); }

    @Override
    public boolean isBoolean(String path) { return delegate.get(path) instanceof Boolean; }

    @Override
    public double getDouble(String path) { return delegate.getDouble(path); }

    @Override
    public double getDouble(String path, double def) { return delegate.getDouble(path, def); }

    @Override
    public boolean isDouble(String path) { return delegate.get(path) instanceof Double; }

    @Override
    public long getLong(String path) { return delegate.getLong(path); }

    @Override
    public long getLong(String path, long def) { return delegate.getLong(path, def); }

    @Override
    public boolean isLong(String path) { return delegate.get(path) instanceof Long; }

    @Override
    public List<?> getList(String path) {
        return out(delegate.get(path)) instanceof List<?> l ? l : null;
    }

    @Override
    public List<?> getList(String path, List<?> def) {
        List<?> l = getList(path);
        return l == null ? def : l;
    }

    @Override
    public boolean isList(String path) { return delegate.get(path) instanceof List; }

    @Override
    public List<String> getStringList(String path) { return delegate.getStringList(path); }

    @Override
    public List<Integer> getIntegerList(String path) { return delegate.getIntegerList(path); }

    @Override
    public List<Boolean> getBooleanList(String path) {
        List<Boolean> out = new ArrayList<>();
        if (getList(path) instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof Boolean b) out.add(b);
                else if (o instanceof String s) {
                    if (s.equalsIgnoreCase("true")) out.add(true);
                    else if (s.equalsIgnoreCase("false")) out.add(false);
                }
            }
        }
        return out;
    }

    @Override
    public List<Double> getDoubleList(String path) {
        List<Double> out = new ArrayList<>();
        if (getList(path) instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof Number n) out.add(n.doubleValue());
                else if (o instanceof String s) {
                    try {
                        out.add(Double.valueOf(s.trim()));
                    } catch (NumberFormatException ignored) {
                        // 건너뜀
                    }
                }
            }
        }
        return out;
    }

    @Override
    public List<Long> getLongList(String path) {
        List<Long> out = new ArrayList<>();
        if (getList(path) instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof Number n) out.add(n.longValue());
                else if (o instanceof String s) {
                    try {
                        out.add(Long.valueOf(s.trim()));
                    } catch (NumberFormatException ignored) {
                        // 건너뜀
                    }
                }
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
    public <T> T getObject(String path, Class<T> clazz) { return getObject(path, clazz, null); }

    @Override
    public <T> T getObject(String path, Class<T> clazz, T def) {
        Object v = get(path);
        return clazz.isInstance(v) ? clazz.cast(v) : def;
    }

    @Override
    public ConfigurationSection getConfigurationSection(String path) {
        return delegate.get(path) instanceof ConfigSection s ? wrap(s) : null;
    }

    @Override
    public boolean isConfigurationSection(String path) { return delegate.get(path) instanceof ConfigSection; }

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
