package org.bukkit.metadata;

import org.bukkit.plugin.Plugin;

/** 처음 준 값을 그대로 지니는 메타데이터(invalidate 해도 바뀌지 않는다). 역사적 이유로 LazyMetadataValue 를 잇는다(Bukkit 과 같은 계층). */
public class FixedMetadataValue extends LazyMetadataValue {

    private final Object internalValue;

    public FixedMetadataValue(Plugin owningPlugin, Object value) {
        super(owningPlugin);
        this.internalValue = value;
    }

    @Override
    public void invalidate() {}

    @Override
    public Object value() { return internalValue; }
}
