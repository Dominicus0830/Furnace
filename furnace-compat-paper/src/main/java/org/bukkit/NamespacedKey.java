package org.bukkit;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import org.bukkit.plugin.Plugin;

/** 네임스페이스:키 한 쌍. 소문자·숫자·밑줄·점·하이픈·슬래시만 쓴다(키), 네임스페이스는 슬래시 없이. */
public final class NamespacedKey {

    public static final String MINECRAFT = "minecraft";
    public static final String BUKKIT = "bukkit";
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9._-]+");
    private static final Pattern KEY = Pattern.compile("[a-z0-9/._-]+");

    private final String namespace;
    private final String key;

    public NamespacedKey(String namespace, String key) {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(key, "key");
        if (!NAMESPACE.matcher(namespace).matches()) throw new IllegalArgumentException("잘못된 네임스페이스: " + namespace);
        if (!KEY.matcher(key).matches()) throw new IllegalArgumentException("잘못된 키: " + key);
        this.namespace = namespace;
        this.key = key;
    }

    public NamespacedKey(Plugin plugin, String key) {
        this(plugin.getName().toLowerCase(Locale.ROOT).replace(' ', '_'), key.toLowerCase(Locale.ROOT));
    }

    public static NamespacedKey minecraft(String key) { return new NamespacedKey(MINECRAFT, key); }

    /** "ns:key" 또는 "key"(네임스페이스 minecraft). 잘못되면 null. */
    public static NamespacedKey fromString(String string) {
        if (string == null || string.isEmpty()) return null;
        String[] p = string.toLowerCase(Locale.ROOT).split(":", 3);
        try {
            if (p.length == 1) return minecraft(p[0]);
            if (p.length == 2) return new NamespacedKey(p[0].isEmpty() ? MINECRAFT : p[0], p[1]);
        } catch (IllegalArgumentException e) {
            return null;
        }
        return null;
    }

    public String getNamespace() { return namespace; }

    public String getKey() { return key; }

    @Override
    public boolean equals(Object o) { return o instanceof NamespacedKey k && k.namespace.equals(namespace) && k.key.equals(key); }

    @Override
    public int hashCode() { return 31 * namespace.hashCode() + key.hashCode(); }

    @Override
    public String toString() { return namespace + ":" + key; }
}
