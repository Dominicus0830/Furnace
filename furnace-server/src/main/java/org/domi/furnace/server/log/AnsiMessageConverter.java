// logback %fmsg — 인자가 ConsoleText 하나뿐인 "{}" 로그면 ANSI 색으로, 아니면 보통 메시지로 찍는다.
// 대화형 터미널(System.console())일 때만 색을 쓰고, -Dfurnace.console.colors=false 나 NO_COLOR 로 끈다.
package org.domi.furnace.server.log;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.domi.furnace.api.text.serializer.AnsiTextSerializer;

public final class AnsiMessageConverter extends ClassicConverter {

    private static final AnsiTextSerializer ANSI = pick();

    private static AnsiTextSerializer pick() {
        if (!Boolean.parseBoolean(System.getProperty("furnace.console.colors", "true")) || !interactive()) return null;
        AnsiTextSerializer.ColorMode mode = AnsiTextSerializer.ColorMode.detect();
        return mode == AnsiTextSerializer.ColorMode.NONE ? null : AnsiTextSerializer.builder().colorMode(mode).build();
    }

    /** 표준 출력이 대화형 터미널인가. JDK 22+ 는 출력을 파이프로 돌려도 System.console() 이 null 이 아니어서 Console.isTerminal() 로 한 번 더 본다. */
    private static boolean interactive() {
        java.io.Console console = System.console();
        if (console == null) return false;
        try {
            return (Boolean) java.io.Console.class.getMethod("isTerminal").invoke(console);   // JDK 22+
        } catch (ReflectiveOperationException | RuntimeException e) {
            return true;   // JDK 21 이하: console() 이 null 이 아니면 터미널이다
        }
    }

    /** 콘솔에 ANSI 색을 쓰는가(레벨 색 변환기도 같은 판단을 쓴다). */
    static boolean colorsEnabled() { return forced != null ? forced : ANSI != null; }

    /** 시험용: 터미널 여부와 상관없이 색을 켜거나 끈다(null = 원래 판단). */
    private static volatile Boolean forced;

    static void forceColorsForTest(Boolean on) { forced = on; }

    @Override
    public String convert(ILoggingEvent event) {
        if (ANSI != null) {
            Object[] args = event.getArgumentArray();
            if (args != null && args.length == 1 && args[0] instanceof ConsoleText ct && "{}".equals(event.getMessage())) {
                return ANSI.serialize(ct.text());
            }
        }
        return event.getFormattedMessage();
    }
}
