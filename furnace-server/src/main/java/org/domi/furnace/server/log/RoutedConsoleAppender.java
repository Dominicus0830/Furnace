// logback 콘솔 어펜더: ConsoleAppender 와 같은 일을 하되 출력을 {@link ConsoleBridge} 로 보낸다(JLine 입력 줄을 깨지 않으려고).
package org.domi.furnace.server.log;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class RoutedConsoleAppender extends OutputStreamAppender<ILoggingEvent> {

    @Override
    public void start() {
        setOutputStream(new OutputStream() {
            @Override
            public void write(int b) {
                ConsoleBridge.print(new String(new byte[]{(byte) b}, StandardCharsets.UTF_8));
            }

            // 인코더는 한 건을 통째로 한 번에 쓴다 — 줄 도중에 UTF-8 이 잘릴 일이 없다.
            @Override
            public void write(byte[] b, int off, int len) {
                ConsoleBridge.print(new String(b, off, len, StandardCharsets.UTF_8));
            }
        });
        super.start();
    }
}
