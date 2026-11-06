// 내부 접근을 쓰는 플러그인이 지원하지 않는 마인크래프트 버전에서 켜졌다.
package org.domi.furnace.api.internal;

import java.util.Arrays;

public final class UnsupportedVersionException extends IllegalStateException {

    private final String actual;
    private final String[] supported;

    public UnsupportedVersionException(String actual, String... supported) {
        super("이 서버는 마인크래프트 " + actual + " 이다 — 이 플러그인의 내부 접근은 " + String.join(", ", supported)
                + " 용으로 만들어졌다(플러그인을 새 버전에 맞춰 다시 빌드해야 한다)");
        this.actual = actual;
        this.supported = supported.clone();
    }

    public String actual() { return actual; }

    public String[] supported() { return Arrays.copyOf(supported, supported.length); }
}
