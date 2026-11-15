package org.domi.furnace.tools.extract;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** 자식 프로세스 실행. 출력은 그대로 이어 붙인다. */
final class Procs {
    private Procs() {}

    static void run(List<String> cmd, Path workDir) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(cmd).inheritIO();
        if (workDir != null) pb.directory(workDir.toFile());
        try {
            int code = pb.start().waitFor();
            if (code != 0) throw new IOException("종료 코드 " + code + ": " + String.join(" ", cmd));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("중단: " + String.join(" ", cmd), e);
        }
    }

    static void run(List<String> cmd, Path workDir, java.util.Map<String, String> env) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(cmd).inheritIO();
        pb.environment().putAll(env);
        if (workDir != null) pb.directory(workDir.toFile());
        try {
            int code = pb.start().waitFor();
            if (code != 0) throw new IOException("종료 코드 " + code + ": " + String.join(" ", cmd));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("중단: " + String.join(" ", cmd), e);
        }
    }

    /** 표준 출력을 파일로 받는다(출력을 stdout 으로 내는 이전 도구용). */
    static void runToFile(List<String> cmd, Path out, java.util.Map<String, String> env) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectOutput(out.toFile()).redirectError(ProcessBuilder.Redirect.INHERIT);
        pb.environment().putAll(env);
        try {
            int code = pb.start().waitFor();
            if (code != 0) throw new IOException("종료 코드 " + code + ": " + String.join(" ", cmd));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("중단: " + String.join(" ", cmd), e);
        }
    }

    static String exe(Path javaHome, String name) {
        boolean win = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
        return javaHome.resolve("bin").resolve(win ? name + ".exe" : name).toString();
    }
}
