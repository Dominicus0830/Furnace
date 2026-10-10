// 글자별 색 태그(패키지 내부): gradient · rainbow · transition · pride.
// 방식: 태그가 닫힐 때 이미 해석된 자식 목록을 받아, 보이는 글자 단위(코드포인트; 글자가 아닌 내용은 1칸)를 센 뒤 글자마다 색을 입힌다.
// 이미 색이 정해진 마디(와 그 후손)는 색을 바꾸지 않되 위치는 글자 수만큼 흘려보낸다. 같은 색이 이어지면 한 마디로 합친다.
package org.domi.furnace.api.text.markup;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import org.domi.furnace.api.text.Content;
import org.domi.furnace.api.text.NamedTextColor;
import org.domi.furnace.api.text.Style;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.text.TextColor;

final class ColorEffects {

    private ColorEffects() {}

    /** index 번째 글자(총 size 개 중)의 색. */
    interface Palette {
        TextColor at(int index, int size);
    }

    // ------------------------------------------------------------------ 태그 만들기

    static Tag gradient(ArgumentQueue args, Context ctx) {
        List<TextColor> colors = new ArrayList<>();
        double phase = 0;
        while (args.hasNext()) {
            ArgumentQueue.Argument a = args.pop();
            TextColor c = StandardTags.colorOrNull(a.value());
            if (c != null) {
                colors.add(c);
            } else {
                OptionalDouble p = a.asDouble();
                if (p.isPresent() && !args.hasNext()) phase = p.getAsDouble();
                else throw ctx.newException("'" + a.value() + "' 을(를) 색으로 읽을 수 없다");
            }
        }
        if (colors.isEmpty()) {
            colors.add(NamedTextColor.WHITE);
            colors.add(NamedTextColor.BLACK);
        }
        if (colors.size() == 1) throw ctx.newException("그라디언트에는 색이 둘 이상 필요하다");
        if (phase < -1 || phase > 1) throw ctx.newException("그라디언트 phase 는 -1.0 ~ 1.0 이어야 한다: " + phase);
        if (phase < 0) {
            java.util.Collections.reverse(colors);
            phase = 1 + phase;
        }
        return modifying(gradientPalette(colors.toArray(new TextColor[0]), (float) phase));
    }

    static Palette gradientPalette(TextColor[] colors, float phase) {
        int len = colors.length;
        float shift = phase * (len - 1);
        return (index, size) -> {
            float factorStep = (len - 1) / (float) Math.max(1, size - 1);
            float position = index * factorStep + shift;
            int low = (int) Math.floor(position);
            int high = (int) Math.ceil(position);
            return TextColor.lerp(position - low, colors[low % len], colors[high % len]);
        };
    }

    static Tag rainbow(ArgumentQueue args, Context ctx) {
        boolean reversed = false;
        int phase = 0;
        while (args.hasNext()) {
            String v = args.pop().value();
            if (v.startsWith("!")) {
                reversed = true;
                v = v.substring(1);
            }
            if (!v.isEmpty()) {
                try {
                    phase = Integer.parseInt(v.trim());
                } catch (NumberFormatException e) {
                    throw ctx.newException("rainbow 인자는 '!' 또는 정수 phase 여야 한다: '" + v + "'");
                }
            }
        }
        return modifying(rainbowPalette(reversed, phase));
    }

    static Palette rainbowPalette(boolean reversed, int phase) {
        double dividedPhase = phase / 10d;   // Adventure RainbowTag: double 로 더한 뒤 float 로
        return (index, size) -> {
            float i = index;
            float hue = reversed
                    ? (float) (((size - i) / size + dividedPhase) % 1)
                    : (float) ((i / size + dividedPhase) % 1);
            if (hue < 0) hue += 1f;
            return hsv(hue);
        };
    }

    /** HSV(s=v=1) → RGB — Adventure TextColor.color(HSVLike) 와 같은 float 식(각 채널은 255 를 곱해 내림). */
    static TextColor hsv(float hue) {
        final float s = 1f, v = 1f;
        float h = hue * 6;
        int i = (int) Math.floor(h);
        float f = h - i;
        float p = v * (1 - s);
        float q = v * (1 - s * f);
        float t = v * (1 - s * (1 - f));
        float r, g, b;
        switch (i) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return TextColor.color((int) (r * 255f), (int) (g * 255f), (int) (b * 255f));
    }

    static Tag transition(ArgumentQueue args, Context ctx) {
        List<TextColor> colors = new ArrayList<>();
        double phase = 0;
        while (args.hasNext()) {
            ArgumentQueue.Argument a = args.pop();
            TextColor c = StandardTags.colorOrNull(a.value());
            if (c != null) {
                colors.add(c);
            } else {
                OptionalDouble p = a.asDouble();
                if (p.isPresent() && !args.hasNext()) phase = p.getAsDouble();
                else throw ctx.newException("'" + a.value() + "' 을(를) 색으로 읽을 수 없다");
            }
        }
        if (colors.isEmpty()) throw ctx.newException("transition 에는 색이 하나 이상 필요하다");
        if (phase < 0 || phase > 1) throw ctx.newException("transition phase 는 0.0 ~ 1.0 이어야 한다: " + phase);
        float position = (float) phase * (colors.size() - 1);
        int low = (int) Math.floor(position);
        int high = (int) Math.ceil(position);
        TextColor c = TextColor.lerp(position - low, colors.get(low), colors.get(high));
        return Tag.styling(Style.style(c));
    }

    /** 자주 쓰는 깃발 줄무늬 색(위에서 아래). 정확한 공식 값이 아니라 널리 쓰이는 근사값이다. */
    private static final Map<String, int[]> FLAGS = Map.ofEntries(
            Map.entry("pride", new int[]{0xE40303, 0xFF8C00, 0xFFED00, 0x008026, 0x24408E, 0x732982}),
            Map.entry("progress", new int[]{0xFFFFFF, 0xFFAFC8, 0x74D7EE, 0x613915, 0x000000, 0xE40303, 0xFF8C00, 0xFFED00, 0x008026, 0x24408E, 0x732982}),
            Map.entry("trans", new int[]{0x5BCEFA, 0xF5A9B8, 0xFFFFFF, 0xF5A9B8, 0x5BCEFA}),
            Map.entry("bi", new int[]{0xD60270, 0xD60270, 0x9B4F96, 0x0038A8, 0x0038A8}),
            Map.entry("pan", new int[]{0xFF218C, 0xFFD800, 0x21B1FF}),
            Map.entry("nb", new int[]{0xFCF434, 0xFFFFFF, 0x9C59D1, 0x2C2C2C}),
            Map.entry("lesbian", new int[]{0xD52D00, 0xEF7627, 0xFF9A56, 0xFFFFFF, 0xD162A4, 0xB55690, 0xA30262}),
            Map.entry("ace", new int[]{0x000000, 0xA3A3A3, 0xFFFFFF, 0x800080}),
            Map.entry("agender", new int[]{0x000000, 0xBABABA, 0xFFFFFF, 0xBAF484, 0xFFFFFF, 0xBABABA, 0x000000}),
            Map.entry("demisexual", new int[]{0x000000, 0xFFFFFF, 0x6E0071, 0xD3D3D3}),
            Map.entry("genderqueer", new int[]{0xB57EDC, 0xFFFFFF, 0x4A8123}),
            Map.entry("genderfluid", new int[]{0xFE76A2, 0xFFFFFF, 0xBF12D7, 0x000000, 0x303CBE}),
            Map.entry("intersex", new int[]{0xFFD800, 0x7902AA, 0xFFD800}),
            Map.entry("aromantic", new int[]{0x3DA542, 0xA7D379, 0xFFFFFF, 0xA9A9A9, 0x000000}),
            Map.entry("polysexual", new int[]{0xF714BA, 0x01D66A, 0x1594F6}),
            Map.entry("omnisexual", new int[]{0xFE9ACE, 0xFF53BF, 0x200044, 0x6760FE, 0x8EA6FF}));

    static Tag pride(ArgumentQueue args, Context ctx) {
        String flag = "pride";
        float phase = 0;
        if (args.hasNext()) {
            ArgumentQueue.Argument a = args.pop();
            if (a.asDouble().isPresent() && !FLAGS.containsKey(a.lowerValue())) {
                phase = (float) a.asDouble().getAsDouble();
            } else {
                flag = a.lowerValue();
                if (args.hasNext()) {
                    OptionalDouble p = args.pop().asDouble();
                    if (p.isEmpty()) throw ctx.newException("pride phase 는 숫자여야 한다");
                    phase = (float) p.getAsDouble();
                }
            }
        }
        int[] rgb = FLAGS.get(flag.toLowerCase(Locale.ROOT));
        if (rgb == null) throw ctx.newException("알 수 없는 pride 깃발: '" + flag + "'");
        TextColor[] stripes = new TextColor[rgb.length];
        for (int i = 0; i < rgb.length; i++) stripes[i] = TextColor.color(rgb[i]);
        final float ph = phase;
        return modifying((index, size) -> {
            float frac = (index / (float) size + ph) % 1f;
            if (frac < 0) frac += 1f;
            return stripes[Math.min(stripes.length - 1, (int) (frac * stripes.length))];
        });
    }

    // ------------------------------------------------------------------ 입히기

    static Tag modifying(Palette palette) { return Tag.modifying(kids -> colorize(kids, palette)); }

    /** 자식 목록의 글자 단위를 세어 팔레트로 칠한 새 목록. */
    static List<Text> colorize(List<Text> kids, Palette palette) {
        int size = 0;
        for (Text t : kids) size += count(t);
        if (size == 0) return kids;
        Run run = new Run(palette, size);
        List<Text> out = new ArrayList<>(kids.size() + size);
        for (Text t : kids) apply(t, false, run, out);
        return out;
    }

    private static final class Run {
        final Palette palette;
        final int size;
        int index;

        Run(Palette palette, int size) {
            this.palette = palette;
            this.size = size;
        }

        TextColor next() { return palette.at(index++, size); }
    }

    private static int count(Text t) {
        int n = t.content() instanceof Content.Literal l ? l.text().codePointCount(0, l.text().length()) : 1;
        for (Text c : t.children()) n += count(c);
        return n;
    }

    private static void apply(Text t, boolean colored, Run run, List<Text> out) {
        boolean here = colored || t.style().color() != null;
        List<Text> pieces = null;
        Style style = t.style();
        if (t.content() instanceof Content.Literal l) {
            String s = l.text();
            if (!s.isEmpty()) {
                if (here) {
                    run.index += s.codePointCount(0, s.length());
                } else {
                    pieces = new ArrayList<>();
                    TextColor cur = null;
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < s.length(); ) {
                        int cp = s.codePointAt(i);
                        i += Character.charCount(cp);
                        TextColor col = run.next();
                        if (cur != null && !col.equals(cur)) {
                            pieces.add(Text.text(sb.toString(), cur));
                            sb.setLength(0);
                        }
                        cur = col;
                        sb.appendCodePoint(cp);
                    }
                    pieces.add(Text.text(sb.toString(), cur));
                }
            }
        } else if (here) {
            run.index++;
        } else {
            style = style.color(run.next());
        }
        List<Text> kids = new ArrayList<>(t.children().size());
        for (Text c : t.children()) apply(c, here, run, kids);
        if (pieces != null) {
            if (style.isEmpty()) {
                out.addAll(pieces);
                out.addAll(kids);
            } else {
                pieces.addAll(kids);
                out.add(Text.of(new Content.Literal(""), style, pieces));
            }
            return;
        }
        out.add(Text.of(t.content(), style, kids));
    }
}
