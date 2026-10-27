// 처리되지 않은 예외를 crash-reports/crash-yyyy-MM-dd_HH.mm.ss-server.txt 로 남긴다(바닐라 CrashReport 형식에 가깝게).
package org.domi.furnace.server;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CrashReport {

    private static final Logger log = LoggerFactory.getLogger("Furnace");
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss");
    private static final DateTimeFormatter BODY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private CrashReport() {}

    /** 모든 스레드의 처리되지 않은 예외를 리포트 파일로 남기는 핸들러를 건다(서버를 끄지는 않는다). */
    public static void install(Path dir) {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            log.error("처리되지 않은 예외 (스레드 {})", t.getName(), e);
            write(dir, "Exception in thread \"" + t.getName() + "\"", e, t);
        });
    }

    /** 리포트를 쓰고 경로를 돌려준다. 쓰기에 실패하면 null(이미 죽어 가는 중이라 던지지 않는다). */
    public static Path write(Path dir, String description, Throwable cause, Thread thread) {
        LocalDateTime now = LocalDateTime.now();
        try {
            Files.createDirectories(dir);
            Path file = dir.resolve("crash-" + FILE_TIME.format(now) + "-server.txt");
            for (int i = 1; Files.exists(file); i++) file = dir.resolve("crash-" + FILE_TIME.format(now) + "-server-" + i + ".txt");
            Files.writeString(file, render(now, description, cause, thread), StandardCharsets.UTF_8);
            log.error("이 크래시 리포트를 저장했다: {}", file.toAbsolutePath());
            return file;
        } catch (IOException | RuntimeException e) {
            log.error("크래시 리포트를 쓰지 못했다", e);
            return null;
        }
    }

    static String render(LocalDateTime now, String description, Throwable cause, Thread thread) {
        StringBuilder sb = new StringBuilder();
        sb.append("---- Minecraft Crash Report ----\n");
        sb.append("// Furnace 서버가 예기치 않게 실패했다.\n\n");
        sb.append("Time: ").append(BODY_TIME.format(now)).append('\n');
        sb.append("Description: ").append(description).append("\n\n");
        StringWriter sw = new StringWriter();
        cause.printStackTrace(new PrintWriter(sw));
        sb.append(sw.toString().replace("\r\n", "\n"));
        sb.append("\nA detailed walkthrough of the error, its code path and all known details is as follows:\n");
        sb.append("---------------------------------------------------------------------------------------\n\n");
        sb.append("-- System Details --\nDetails:\n");
        sb.append("\tMinecraft Version: 1.21.1\n");
        sb.append("\tServer Type: Furnace\n");
        String v = CrashReport.class.getPackage().getImplementationVersion();
        sb.append("\tFurnace Version: ").append(v != null ? v : "dev").append('\n');
        sb.append("\tOperating System: ").append(System.getProperty("os.name")).append(" (").append(System.getProperty("os.arch"))
                .append(") version ").append(System.getProperty("os.version")).append('\n');
        sb.append("\tJava Version: ").append(System.getProperty("java.version")).append(", ").append(System.getProperty("java.vendor")).append('\n');
        Runtime rt = Runtime.getRuntime();
        sb.append("\tMemory: ").append(rt.freeMemory()).append(" bytes free / ").append(rt.totalMemory()).append(" bytes total / ")
                .append(rt.maxMemory()).append(" bytes max\n");
        sb.append("\tCPUs: ").append(rt.availableProcessors()).append('\n');
        if (thread != null) sb.append("\tThread: ").append(thread.getName()).append('\n');
        return sb.toString();
    }
}
