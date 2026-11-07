package org.bukkit.plugin;

/** 플러그인 바탕 클래스(Bukkit PluginBase): 이름으로 같음을 판단한다. */
public abstract class PluginBase implements Plugin {

    @Override
    public final int hashCode() { return getName().hashCode(); }

    @Override
    public final boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Plugin other)) return false;
        return getName().equals(other.getName());
    }

    @Override
    public final String getName() { return getDescription().getName(); }
}
