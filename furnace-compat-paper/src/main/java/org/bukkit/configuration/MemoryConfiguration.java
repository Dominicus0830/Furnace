package org.bukkit.configuration;

import java.util.Map;
import org.domi.furnace.api.config.ConfigSection;

public class MemoryConfiguration extends MemorySection implements Configuration {

    private MemoryConfigurationOptions options;

    public MemoryConfiguration() {}

    public MemoryConfiguration(Configuration defaults) { setDefaults(defaults); }

    @Override
    public void addDefaults(Map<String, Object> defaults) {
        for (Map.Entry<String, Object> e : defaults.entrySet()) addDefault(e.getKey(), e.getValue());
    }

    @Override
    public void addDefaults(Configuration defaults) {
        if (defaults instanceof MemorySection m) {
            ConfigSection d = delegate.getDefaults();
            if (d == null) delegate.setDefaults(d = new ConfigSection());
            for (String k : m.delegate.keys(true)) {
                Object v = m.delegate.get(k);
                if (!(v instanceof ConfigSection)) d.set(k, v);
            }
        } else {
            for (Map.Entry<String, Object> e : defaults.getValues(true).entrySet()) {
                if (!(e.getValue() instanceof ConfigurationSection)) addDefault(e.getKey(), e.getValue());
            }
        }
    }

    @Override
    public void setDefaults(Configuration defaults) {
        if (defaults == null) {
            delegate.setDefaults(null);
        } else if (defaults instanceof MemorySection m) {
            delegate.setDefaults(m.delegate);
        } else {
            delegate.setDefaults(null);
            addDefaults(defaults);
        }
    }

    @Override
    public Configuration getDefaults() {
        ConfigSection d = delegate.getDefaults();
        if (d == null) return null;
        MemoryConfiguration c = new MemoryConfiguration();
        c.delegate = d;
        return c;
    }

    @Override
    public ConfigurationSection getParent() { return null; }

    /** 하위 클래스(YamlConfiguration)가 쓰는 Furnace 설정 트리 루트. */
    protected final ConfigSection furnaceRoot() { return delegate; }

    protected final void furnaceRoot(ConfigSection root) { this.delegate = root; }

    @Override
    public MemoryConfigurationOptions options() {
        if (options == null) options = new MemoryConfigurationOptions(this);
        return options;
    }
}
