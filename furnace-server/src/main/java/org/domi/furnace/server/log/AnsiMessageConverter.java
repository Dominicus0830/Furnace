// logback %fmsg — 인자가 ConsoleText 하나뿐인 "{}" 로그면 ANSI 색으로, 아니면 보통 메시지로 찍는다.
// 대화형 터미널(System.console())일 때만 색을 쓰고, -Dfurnace.console.colors=false 나 NO_COLOR 로 끈다.
package org.domi.furnace.server.log;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.domi.furnace.api.text.serializer.AnsiTextSerializer;

public final class AnsiMessageConverter extends ClassicConverter {

    private static final AnsiTextSerializer ANSI = pick();

    private static AnsiTextSerializer pick() {
        if (!Boolean.parseBoolean(System.getProperty("furnace.console.colors", "true")) || System.console() == null) return null;
        AnsiTextSerializer.ColorMode mode = AnsiTextSerializer.ColorMode.detect();
        return mode == AnsiTextSerializer.ColorMode.NONE ? null : AnsiTextSerializer.builder().colorMode(mode).build();
    }

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
