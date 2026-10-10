// 텍스트 목록을 이을 때의 모양(구분자·마지막 구분자·접두/접미·원소 변환) — Adventure JoinConfiguration 과 같은 사용법. 불변.
package org.domi.furnace.api.text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

public final class JoinConfiguration {

    private static final JoinConfiguration NONE = new JoinConfiguration(null, null, null, null, null, null);

    private final Text separator, lastSeparator, lastSeparatorIfSerial, prefix, suffix;
    private final Function<Text, Text> convertor;

    private JoinConfiguration(Text separator, Text lastSeparator, Text lastSeparatorIfSerial, Text prefix, Text suffix,
                              Function<Text, Text> convertor) {
        this.separator = separator;
        this.lastSeparator = lastSeparator;
        this.lastSeparatorIfSerial = lastSeparatorIfSerial;
        this.prefix = prefix;
        this.suffix = suffix;
        this.convertor = convertor;
    }

    /** 구분자 없이 이어 붙인다. */
    public static JoinConfiguration noSeparators() { return NONE; }

    /** 구분자만 정한 설정. */
    public static JoinConfiguration separator(Text separator) { return new JoinConfiguration(separator, null, null, null, null, null); }

    /** 구분자를 글자로 정한 설정. */
    public static JoinConfiguration separator(String separator) { return separator(Text.text(separator)); }

    /** ", " 또는 "," 로 잇는다. */
    public static JoinConfiguration commas(boolean spaces) { return separator(Text.text(spaces ? ", " : ",")); }

    /** 줄바꿈으로 잇는다. */
    public static JoinConfiguration newlines() { return separator(Text.newline()); }

    /** 공백으로 잇는다. */
    public static JoinConfiguration spaces() { return separator(Text.space()); }

    /** "[a, b, c]" 모양. */
    public static JoinConfiguration arrayLike() { return new JoinConfiguration(Text.text(", "), null, null, Text.text("["), Text.text("]"), null); }

    public static Builder builder() { return new Builder(NONE); }

    public Builder toBuilder() { return new Builder(this); }

    public Text separator() { return separator; }

    public Text lastSeparator() { return lastSeparator; }

    public Text lastSeparatorIfSerial() { return lastSeparatorIfSerial; }

    public Text prefix() { return prefix; }

    public Text suffix() { return suffix; }

    public Function<Text, Text> convertor() { return convertor; }

    /**
     * 목록을 이은 텍스트. 원소가 둘이면 lastSeparator, 셋 이상이면 마지막 틈에 lastSeparatorIfSerial(없으면 lastSeparator, 그것도 없으면 separator).
     * 원소가 없으면 빈 텍스트다(접두/접미도 붙이지 않는다).
     */
    public Text join(Iterable<? extends Text> parts) {
        List<Text> items = new ArrayList<>();
        for (Text t : parts) items.add(convertor != null ? convertor.apply(t) : t);
        int n = items.size();
        if (n == 0) return Text.empty();
        if (n == 1 && prefix == null && suffix == null) return items.get(0);
        List<Text> out = new ArrayList<>(n * 2 + 2);
        if (prefix != null) out.add(prefix);
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                Text sep;
                if (i == n - 1) {
                    if (n > 2 && lastSeparatorIfSerial != null) sep = lastSeparatorIfSerial;
                    else if (lastSeparator != null) sep = lastSeparator;
                    else sep = separator;
                } else {
                    sep = separator;
                }
                if (sep != null) out.add(sep);
            }
            out.add(items.get(i));
        }
        if (suffix != null) out.add(suffix);
        return Text.of(new Content.Literal(""), Style.empty(), out);
    }

    public Text join(Text... parts) { return join(Arrays.asList(parts)); }

    /** 가변 빌더. */
    public static final class Builder {
        private Text separator, lastSeparator, lastSeparatorIfSerial, prefix, suffix;
        private Function<Text, Text> convertor;

        Builder(JoinConfiguration c) {
            separator = c.separator;
            lastSeparator = c.lastSeparator;
            lastSeparatorIfSerial = c.lastSeparatorIfSerial;
            prefix = c.prefix;
            suffix = c.suffix;
            convertor = c.convertor;
        }

        public Builder separator(Text t) { separator = t; return this; }

        public Builder lastSeparator(Text t) { lastSeparator = t; return this; }

        public Builder lastSeparatorIfSerial(Text t) { lastSeparatorIfSerial = t; return this; }

        public Builder prefix(Text t) { prefix = t; return this; }

        public Builder suffix(Text t) { suffix = t; return this; }

        public Builder convertor(Function<Text, Text> f) { convertor = f; return this; }

        public JoinConfiguration build() {
            return new JoinConfiguration(separator, lastSeparator, lastSeparatorIfSerial, prefix, suffix, convertor);
        }
    }
}
