// 서버가 호환 어댑터에게 넘기는 plugins.compat.* 설정을 읽는 한 곳. 값은 다리(Internals#compatOption)로만 받는다 — 공개 API(Furnace)에는 없다.
package org.domi.furnace.compat;

import org.domi.furnace.api.Furnace;

public final class CompatOptions {

    private CompatOptions() {}

    /** 그 키의 값(안 적었거나 기본값이면 null). 다리를 주지 않거나(던짐·null) 하는 서버 구현이면 null(= 어댑터의 기본값). */
    public static String get(Furnace server, String key) {
        org.domi.furnace.api.internal.Internals in;
        try {
            in = server.internals();
        } catch (UnsupportedOperationException e) {
            return null;
        }
        return in == null ? null : in.compatOption(key);   // 다리를 null 로 주는 가짜 서버(시험용 프록시 등)도 기본값으로
    }
}
