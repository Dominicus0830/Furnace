package org.bukkit.metadata;

import java.lang.ref.WeakReference;
import java.util.Objects;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.NumberConversions;

/** value()·invalidate() 만 채우면 되는 MetadataValue 바탕. 형 변환은 Bukkit 규칙(NumberConversions)이다. */
public abstract class MetadataValueAdapter implements MetadataValue {

    protected final WeakReference<Plugin> owningPlugin;

    protected MetadataValueAdapter(Plugin owningPlugin) {
        if (owningPlugin == null) throw new IllegalArgumentException("owningPlugin cannot be null");
        this.owningPlugin = new WeakReference<>(owningPlugin);
    }

    @Override
    public Plugin getOwningPlugin() { return owningPlugin.get(); }

    @Override
    public int asInt() { return NumberConversions.toInt(value()); }

    @Override
    public float asFloat() { return NumberConversions.toFloat(value()); }

    @Override
    public double asDouble() { return NumberConversions.toDouble(value()); }

    @Override
    public long asLong() { return NumberConversions.toLong(value()); }

    @Override
    public short asShort() { return NumberConversions.toShort(value()); }

    @Override
    public byte asByte() { return NumberConversions.toByte(value()); }

    @Override
    public boolean asBoolean() {
        Object v = value();
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.intValue() != 0;
        if (v instanceof String s) return Boolean.parseBoolean(s);
        return v != null;
    }

    @Override
    public String asString() { return Objects.toString(value(), ""); }
}
