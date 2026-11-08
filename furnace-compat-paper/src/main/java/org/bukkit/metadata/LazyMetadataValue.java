package org.bukkit.metadata;

import java.lang.ref.SoftReference;
import java.util.concurrent.Callable;
import org.bukkit.plugin.Plugin;

/** 처음 읽을 때 계산하는 메타데이터. 계산 결과는 캐시 전략에 따라 기억한다(부드러운 참조 — 메모리가 모자라면 다시 계산한다). */
public class LazyMetadataValue extends MetadataValueAdapter {

    /** 계산 결과 null 을 "아직 계산 안 함" 과 가르는 표지. */
    private static final Object NULL = new Object();

    private Callable<Object> lazyValue;
    private CacheStrategy cacheStrategy;
    private SoftReference<Object> cached;

    public LazyMetadataValue(Plugin owningPlugin, Callable<Object> lazyValue) { this(owningPlugin, CacheStrategy.CACHE_AFTER_FIRST_EVAL, lazyValue); }

    public LazyMetadataValue(Plugin owningPlugin, CacheStrategy cacheStrategy, Callable<Object> lazyValue) {
        super(owningPlugin);
        if (cacheStrategy == null) throw new IllegalArgumentException("cacheStrategy cannot be null");
        if (lazyValue == null) throw new IllegalArgumentException("lazyValue cannot be null");
        this.cached = new SoftReference<>(null);
        this.lazyValue = lazyValue;
        this.cacheStrategy = cacheStrategy;
    }

    /** FixedMetadataValue 가 계산 준비 없이 쓰는 생성자. */
    protected LazyMetadataValue(Plugin owningPlugin) { super(owningPlugin); }

    @Override
    public Object value() {
        Object v = evaluate();
        return v == NULL ? null : v;
    }

    private synchronized Object evaluate() {
        Object v = cached.get();
        if (cacheStrategy == CacheStrategy.NEVER_CACHE || v == null) {
            try {
                v = lazyValue.call();
            } catch (Exception e) {
                throw new MetadataEvaluationException(e);
            }
            if (v == null) v = NULL;
            cached = new SoftReference<>(v);
        }
        return v;
    }

    @Override
    public synchronized void invalidate() {
        if (cacheStrategy != CacheStrategy.CACHE_ETERNALLY) cached.clear();
    }

    public enum CacheStrategy {
        /** 한 번 계산하면 invalidate 전까지 다시 계산하지 않는다. */
        CACHE_AFTER_FIRST_EVAL,
        /** 읽을 때마다 계산한다. */
        NEVER_CACHE,
        /** 한 번 계산하면 invalidate 해도 다시 계산하지 않는다. */
        CACHE_ETERNALLY
    }
}
