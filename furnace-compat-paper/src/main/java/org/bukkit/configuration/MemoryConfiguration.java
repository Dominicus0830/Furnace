package org.bukkit.configuration;

import java.util.Map;
import org.domi.furnace.api.config.ConfigSection;

public class MemoryConfiguration extends MemorySection implements Configuration {

    private MemoryConfigurationOptions options;
    /** setDefaults 로 받은 설정(Paper 처럼 getDefaults 가 같은 객체를 돌려준다 — 그쪽 pathSeparator 도 그대로). */
    private MemorySection defaultsRef;
    /** addDefault 로 만든 기본값 트리의 포장 객체 — Paper 처럼 부를 때마다 같은 객체라 그 options() 변경이 남는다(PR #264 검토 R4). */
    private MemoryConfiguration defaultsWrapper;
    /** 포장 객체의 구분자를 우리가 마지막으로 맞춘 값 — 그대로면 플러그인이 안 바꾼 것이라 루트 구분자를 따라간다(꺼짐일 때). */
    private char defaultsWrapperAuto = '.';

    public MemoryConfiguration() {}

    public MemoryConfiguration(Configuration defaults) { setDefaults(defaults); }

    @Override
    public void addDefaults(Map<String, Object> defaults) {
        for (Map.Entry<String, Object> e : defaults.entrySet()) addDefault(e.getKey(), e.getValue());
    }

    @Override
    public void addDefaults(Configuration defaults) {
        // 빠른 길은 두 쪽 모두 '.' 일 때만(점으로 이은 경로를 점으로 다시 나누므로). 아니면 Paper 처럼 getValues(true) 를 addDefault 로 하나씩(PR #254 검토 R1)
        if (defaults instanceof MemorySection m && options().pathSeparator() == '.' && m.getRoot().options().pathSeparator() == '.') {
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
            defaultsRef = null;
        } else if (defaults instanceof MemorySection m) {
            delegate.setDefaults(m.delegate);
            defaultsRef = m;
        } else {
            delegate.setDefaults(null);
            defaultsRef = null;
            addDefaults(defaults);
        }
    }

    @Override
    public Configuration getDefaults() {
        ConfigSection d = delegate.getDefaults();
        if (d == null) return null;
        if (defaultsRef instanceof Configuration c && defaultsRef.delegate == d) return c;
        MemoryConfiguration c = defaultsWrapper;
        if (c == null || c.delegate != d) {
            c = new MemoryConfiguration();
            c.delegate = d;
            defaultsWrapper = c;
            defaultsWrapperAuto = '.';
        }
        // addDefault 로 만들어진 기본값 트리는 이쪽 구분자로 쓰였다. 옵션(paper-default-separator)을 켜면 Paper 처럼 '.' 로 쪼갰으므로 '.' 그대로.
        // 플러그인이 포장 객체의 구분자를 직접 바꿨으면(Paper 와 같게) 건드리지 않는다
        if (c.options().pathSeparator() == defaultsWrapperAuto) {
            char want = org.domi.furnace.compat.paper.ConfigPaths.paperDefaultSeparator() ? '.' : options().pathSeparator();
            c.options().pathSeparator(want);
            defaultsWrapperAuto = want;
        }
        return c;
    }

    @Override
    public ConfigurationSection getParent() { return null; }

    /** 읽을 때 되살리지 못한 {@code ==} 구역(그때 등록되지 않았던 별칭)이 남아 있는가 — 있으면 읽기 입구에서 다시 시도한다. */
    private boolean pendingAliases;
    private int aliasEpoch;

    /** 호환 모듈 전용: 다시 시도할 때가 됐는가(남은 구역이 있고 그 뒤 클래스 등록이 바뀌었을 때만). */
    public final boolean pendingAliases() { return pendingAliases && aliasEpoch != org.bukkit.configuration.serialization.ConfigurationSerialization.epoch(); }

    public final void pendingAliases(boolean pending) {
        this.pendingAliases = pending;
        this.aliasEpoch = org.bukkit.configuration.serialization.ConfigurationSerialization.epoch();
    }

    /** 하위 클래스(YamlConfiguration)가 쓰는 Furnace 설정 트리 루트. */
    protected final ConfigSection furnaceRoot() { return delegate; }

    protected final void furnaceRoot(ConfigSection root) { this.delegate = root; }

    @Override
    public MemoryConfigurationOptions options() {
        if (options == null) options = new MemoryConfigurationOptions(this);
        return options;
    }
}
