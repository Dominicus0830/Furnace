// 콘솔 입력(바닐라 DedicatedServer "Server console handler") — 표준 입력의 각 줄을 콘솔 권한(4)으로 전역 스레드에서 실행한다.
package org.domi.furnace.server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.domi.furnace.server.command.CommandSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConsoleInput {

    private static final Logger log = LoggerFactory.getLogger(ConsoleInput.class);

    private ConsoleInput() {}

    public static void start(ServerCore core) {
        Thread t = new Thread(() -> {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    String command = line.strip();
                    if (command.isEmpty()) continue;
                    core.scheduler().global().execute(() -> core.runConsoleCommand(command));
                }
            } catch (Exception e) {
                log.error("Exception handling console input", e);
            }
        }, "Server console handler");
        t.setDaemon(true);
        t.start();
    }
}
