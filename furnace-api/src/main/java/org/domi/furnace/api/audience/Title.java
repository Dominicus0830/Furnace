// 화면 가운데 제목 — 제목·부제·시간(틱 단위, 바닐라 기본 10·70·20).
package org.domi.furnace.api.audience;

import java.util.Objects;
import org.domi.furnace.api.text.Text;

public record Title(Text title, Text subtitle, Times times) {

    public record Times(int fadeInTicks, int stayTicks, int fadeOutTicks) {
        public static final Times DEFAULT = new Times(10, 70, 20);
    }

    public Title {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(subtitle, "subtitle");
    }

    public static Title title(Text title, Text subtitle) { return new Title(title, subtitle, null); }

    public static Title title(Text title, Text subtitle, Times times) { return new Title(title, subtitle, times); }
}
