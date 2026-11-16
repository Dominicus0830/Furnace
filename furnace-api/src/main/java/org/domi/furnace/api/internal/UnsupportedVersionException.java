// 내부 접근을 쓰는 플러그인이 지원하지 않는 마인크래프트 버전에서 켜졌다.
package org.domi.furnace.api.internal;

import java.util.Arrays;

public final class UnsupportedVersionException extends IllegalStateException {

    private final String actual;
    private final String[] supported;

    public UnsupportedVersionException(String actual, String... supported) {
        super("This server runs Minecraft " + actual + " - this plugin's internals access was built for " + String.join(", ", supported)
                + " (rebuild the plugin for the new version)");
        this.actual = actual;
        this.supported = supported.clone();
    }

    public String actual() { return actual; }

    public String[] supported() { return Arrays.copyOf(supported, supported.length); }
}
