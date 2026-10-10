// 서식 플레이스홀더 — 숫자·날짜·개수에 따른 문구·참/거짓 문구·목록 잇기. 서식은 태그 인자로 고른다: <n:'ko-KR':'#.##'>.
package org.domi.furnace.api.text.markup;

import java.text.ChoiceFormat;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.domi.furnace.api.text.JoinConfiguration;
import org.domi.furnace.api.text.Text;

public final class Formatter {

    private Formatter() {}

    /** 숫자. 인자 없으면 기본 로케일 서식, '#.##' 같은 패턴 하나면 패턴, 'ko-KR' 하나면 로케일, 둘이면 <n:'로케일':'패턴'>. */
    public static TagResolver number(String name, Number number) { return number(name, number, Locale.getDefault()); }

    public static TagResolver number(String name, Number number, Locale defaultLocale) {
        return TagResolver.resolver(name, (args, ctx) -> {
            NumberFormat f;
            try {
                if (!args.hasNext()) {
                    f = NumberFormat.getInstance(defaultLocale);
                } else {
                    String first = args.pop().value();
                    if (args.hasNext()) {
                        Locale l = Locale.forLanguageTag(first);
                        f = new DecimalFormat(args.pop().value(), DecimalFormatSymbols.getInstance(l));
                    } else if (first.indexOf('#') >= 0 || first.indexOf('0') >= 0) {
                        f = new DecimalFormat(first, DecimalFormatSymbols.getInstance(defaultLocale));
                    } else {
                        f = NumberFormat.getInstance(Locale.forLanguageTag(first));
                    }
                }
            } catch (IllegalArgumentException e) {
                throw ctx.newException("숫자 서식이 잘못됐다: " + e.getMessage());
            }
            return Tag.selfClosingInserting(Text.text(f.format(number)));
        });
    }

    /** 날짜·시간. 인자는 DateTimeFormatter 패턴 하나: <d:'yyyy-MM-dd HH:mm'>. 인자가 없으면 값의 toString. */
    public static TagResolver date(String name, TemporalAccessor time) { return date(name, time, Locale.getDefault()); }

    public static TagResolver date(String name, TemporalAccessor time, Locale locale) {
        return TagResolver.resolver(name, (args, ctx) -> {
            if (!args.hasNext()) return Tag.selfClosingInserting(Text.text(String.valueOf(time)));
            try {
                return Tag.selfClosingInserting(Text.text(DateTimeFormatter.ofPattern(args.pop().value(), locale).format(time)));
            } catch (RuntimeException e) {
                throw ctx.newException("날짜 서식이 잘못됐다: " + e.getMessage());
            }
        });
    }

    /** 개수에 따른 문구. 인자는 ChoiceFormat 패턴: <c:'0#없음|1#하나|1&lt;{0}개'> ({0} 은 숫자). 고른 문구는 마크업으로 해석된다. */
    public static TagResolver choice(String name, Number number) {
        return TagResolver.resolver(name, (args, ctx) -> {
            String pattern = args.popOr("choice 에는 ChoiceFormat 패턴이 필요하다").value();
            String picked;
            try {
                picked = new ChoiceFormat(pattern).format(number.doubleValue());
            } catch (IllegalArgumentException e) {
                throw ctx.newException("ChoiceFormat 패턴이 잘못됐다: " + e.getMessage());
            }
            if (picked.indexOf("{0}") >= 0) picked = picked.replace("{0}", NumberFormat.getInstance().format(number));
            return Tag.preProcessParsed(picked);
        });
    }

    /** 참/거짓에 따른 문구: <b:'켜짐':'꺼짐'>. 고른 문구는 마크업으로 해석된다. */
    public static TagResolver booleanChoice(String name, boolean value) {
        return TagResolver.resolver(name, (args, ctx) -> {
            String t = args.popOr("booleanChoice 에는 참일 때 문구가 필요하다").value();
            String f = args.popOr("booleanChoice 에는 거짓일 때 문구가 필요하다").value();
            return Tag.preProcessParsed(value ? t : f);
        });
    }

    /** 텍스트 목록 잇기: <list:'구분자':'마지막 구분자'> (기본 ", "). 구분자는 마크업으로 해석된다. */
    public static TagResolver joining(String name, Iterable<? extends Text> items) {
        List<Text> list = new ArrayList<>();
        for (Text t : items) list.add(t);
        return TagResolver.resolver(name, (args, ctx) -> {
            Text sep = args.hasNext() ? ctx.deserialize(args.pop().value()) : Text.text(", ");
            Text last = args.hasNext() ? ctx.deserialize(args.pop().value()) : sep;
            JoinConfiguration cfg = JoinConfiguration.builder().separator(sep).lastSeparator(last).build();
            return Tag.selfClosingInserting(cfg.join(list));
        });
    }
}
