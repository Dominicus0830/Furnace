// 콘솔용 ANSI 직렬화기 — Text 를 터미널 색 코드(SGR)가 든 문자열로. 트루컬러/256색/16색/없음을 옵션으로 고른다.
// 꾸밈: 굵게 1, 기울임 3, 밑줄 4, 취소선 9, 난독화 5(깜박임). 이름 있는 16색은 기본적으로 터미널 팔레트 코드(30-37, 90-97)로 쓴다.
package org.domi.furnace.api.text.serializer;

import java.util.Locale;
import java.util.function.Function;
import org.domi.furnace.api.text.NamedTextColor;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.text.TextColor;
import org.domi.furnace.api.text.TextDecoration;
import org.domi.furnace.api.text.Texts;
import org.domi.furnace.api.text.Translators;

public final class AnsiTextSerializer {

    /** 색 표현 방식. */
    public enum ColorMode {
        /** 24비트 색(38;2;r;g;b). */
        TRUE_COLOR,
        /** xterm 256색(38;5;n). */
        ANSI_256,
        /** 기본 16색. */
        ANSI_16,
        /** 아무 코드도 쓰지 않는다(평문). */
        NONE;

        /** 환경변수로 터미널 능력을 추정한다: NO_COLOR → NONE, COLORTERM=truecolor/24bit → TRUE_COLOR, TERM 에 256color → ANSI_256, dumb → NONE, 그 밖에 ANSI_16. */
        public static ColorMode detect() { return detect(System::getenv); }

        public static ColorMode detect(Function<String, String> env) {
            String noColor = env.apply("NO_COLOR");
            if (noColor != null && !noColor.isEmpty()) return NONE;
            String ct = env.apply("COLORTERM");
            if (ct != null && (ct.contains("truecolor") || ct.contains("24bit"))) return TRUE_COLOR;
            String term = env.apply("TERM");
            if (term != null && term.equals("dumb")) return NONE;
            if (term != null && term.contains("256color")) return ANSI_256;
            return ANSI_16;
        }
    }

    private static final String ESC = "\u001B[";
    /** NamedTextColor.VALUES 순서(0..f)의 SGR 전경색 코드. */
    private static final int[] SGR16 = {30, 34, 32, 36, 31, 35, 33, 37, 90, 94, 92, 96, 91, 95, 93, 97};
    private static final int[] DECO_ON = new int[5], DECO_OFF = new int[5];

    static {
        DECO_ON[TextDecoration.BOLD.ordinal()] = 1;
        DECO_OFF[TextDecoration.BOLD.ordinal()] = 22;
        DECO_ON[TextDecoration.ITALIC.ordinal()] = 3;
        DECO_OFF[TextDecoration.ITALIC.ordinal()] = 23;
        DECO_ON[TextDecoration.UNDERLINED.ordinal()] = 4;
        DECO_OFF[TextDecoration.UNDERLINED.ordinal()] = 24;
        DECO_ON[TextDecoration.STRIKETHROUGH.ordinal()] = 9;
        DECO_OFF[TextDecoration.STRIKETHROUGH.ordinal()] = 29;
        DECO_ON[TextDecoration.OBFUSCATED.ordinal()] = 5;
        DECO_OFF[TextDecoration.OBFUSCATED.ordinal()] = 25;
    }

    private final ColorMode mode;
    private final Locale locale;
    private final boolean exactNamedColors;
    private final boolean resetAtEnd;

    private AnsiTextSerializer(Builder b) {
        this.mode = b.mode;
        this.locale = b.locale;
        this.exactNamedColors = b.exactNamedColors;
        this.resetAtEnd = b.resetAtEnd;
    }

    public static AnsiTextSerializer trueColor() { return builder().colorMode(ColorMode.TRUE_COLOR).build(); }

    public static AnsiTextSerializer ansi256() { return builder().colorMode(ColorMode.ANSI_256).build(); }

    public static AnsiTextSerializer ansi16() { return builder().colorMode(ColorMode.ANSI_16).build(); }

    /** 환경에서 추정한 방식. */
    public static AnsiTextSerializer detected() { return builder().colorMode(ColorMode.detect()).build(); }

    public static Builder builder() { return new Builder(); }

    public ColorMode colorMode() { return mode; }

    /** 글자 + SGR 코드. 스타일이 바뀌는 곳에만 코드를 쓰고, 끝에 스타일이 남아 있으면 ESC[0m 으로 닫는다(옵션). */
    public String serialize(Text text) {
        if (mode == ColorMode.NONE) return text.plainText(locale);
        StringBuilder sb = new StringBuilder();
        TextColor[] color = new TextColor[1];
        boolean[] on = new boolean[5];
        Texts.forEachSegment(text, (node, eff) -> {
            String content = Texts.plainContent(node, locale);
            if (content.isEmpty()) return;
            int[] codes = new int[8];
            String colorCode = null;
            int n = 0;
            TextColor want = eff.color();
            boolean[] wantDeco = new boolean[5];
            for (TextDecoration d : TextDecoration.values()) {
                wantDeco[d.ordinal()] = eff.hasDecoration(d);
                if (on[d.ordinal()] && !wantDeco[d.ordinal()]) {
                    codes[n++] = DECO_OFF[d.ordinal()];
                    on[d.ordinal()] = false;
                }
            }
            if (want == null && color[0] != null) {
                codes[n++] = 39;
                color[0] = null;
            } else if (want != null && !want.equals(color[0])) {
                colorCode = colorParams(want);
                color[0] = want;
            }
            int[] ons = new int[5];
            int m = 0;
            for (TextDecoration d : TextDecoration.values()) {
                if (wantDeco[d.ordinal()] && !on[d.ordinal()]) {
                    ons[m++] = DECO_ON[d.ordinal()];
                    on[d.ordinal()] = true;
                }
            }
            if (n > 0 || colorCode != null || m > 0) {
                sb.append(ESC);
                boolean first = true;
                for (int i = 0; i < n; i++) {
                    if (!first) sb.append(';');
                    sb.append(codes[i]);
                    first = false;
                }
                if (colorCode != null) {
                    if (!first) sb.append(';');
                    sb.append(colorCode);
                    first = false;
                }
                for (int i = 0; i < m; i++) {
                    if (!first) sb.append(';');
                    sb.append(ons[i]);
                    first = false;
                }
                sb.append('m');
            }
            sb.append(content);
        });
        boolean dirty = color[0] != null;
        for (boolean b : on) dirty |= b;
        if (dirty && resetAtEnd) sb.append(ESC).append("0m");
        return sb.toString();
    }

    private String colorParams(TextColor c) {
        if (mode == ColorMode.ANSI_16 || c.isNamed() && !exactNamedColors) {
            TextColor named = NamedTextColor.nearestTo(c);
            return Integer.toString(SGR16[NamedTextColor.VALUES.indexOf(named)]);
        }
        if (mode == ColorMode.ANSI_256) return "38;5;" + index256(c.red(), c.green(), c.blue());
        return "38;2;" + c.red() + ';' + c.green() + ';' + c.blue();
    }

    private static final int[] CUBE = {0, 95, 135, 175, 215, 255};

    /** RGB → xterm 256색 번호(6x6x6 큐브와 24단 회색 중 더 가까운 쪽). */
    static int index256(int r, int g, int b) {
        int ri = nearestCube(r), gi = nearestCube(g), bi = nearestCube(b);
        int cubeDist = dist(r, g, b, CUBE[ri], CUBE[gi], CUBE[bi]);
        int avg = (r + g + b) / 3;
        int gray = Math.max(0, Math.min(23, Math.round((avg - 8) / 10f)));
        int gv = 8 + gray * 10;
        int grayDist = dist(r, g, b, gv, gv, gv);
        return grayDist < cubeDist ? 232 + gray : 16 + 36 * ri + 6 * gi + bi;
    }

    private static int nearestCube(int v) {
        int best = 0;
        for (int i = 1; i < CUBE.length; i++) if (Math.abs(CUBE[i] - v) < Math.abs(CUBE[best] - v)) best = i;
        return best;
    }

    private static int dist(int r1, int g1, int b1, int r2, int g2, int b2) {
        return (r1 - r2) * (r1 - r2) + (g1 - g2) * (g1 - g2) + (b1 - b2) * (b1 - b2);
    }

    /** 옵션 빌더. */
    public static final class Builder {
        private ColorMode mode = ColorMode.TRUE_COLOR;
        private Locale locale = Translators.SERVER_LOCALE;
        private boolean exactNamedColors;
        private boolean resetAtEnd = true;

        Builder() {}

        public Builder colorMode(ColorMode m) { mode = m; return this; }

        public Builder locale(Locale l) { locale = l; return this; }

        /** true 면 이름 있는 16색도 트루컬러/256색 모드에서 정확한 RGB 로 쓴다(기본은 터미널 팔레트 코드). */
        public Builder exactNamedColors(boolean v) { exactNamedColors = v; return this; }

        /** 끝에 남은 스타일을 ESC[0m 으로 닫는다(기본 true). */
        public Builder resetAtEnd(boolean v) { resetAtEnd = v; return this; }

        public AnsiTextSerializer build() { return new AnsiTextSerializer(this); }
    }
}
