package net.minecraft;

/**
 * 바닐라 오류 보고(Mojang 이름 — 제목과 원인만, NMS shim 5e-3: NBT 읽기 오류를 {@link ReportedException} 으로 감쌀 때 쓴다).
 * 바닐라의 항목(category)·보고서 글·파일 저장은 두지 않았다(없는 멤버 — 부르는 곳에서 링크 오류).
 */
public class CrashReport {

    private final String title;
    private final Throwable exception;

    public CrashReport(String title, Throwable exception) {
        this.title = title;
        this.exception = exception;
    }

    @SuppressWarnings("removal")
    public static CrashReport forThrowable(Throwable cause, String title) {
        if (cause instanceof ThreadDeath death) throw death;   // Paper: 스레드 끝내기는 감싸지 않고 다시 던진다
        // 바닐라: ReportedException 을 다시 감싸면 안쪽 보고를 그대로 쓴다
        while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) cause = cause.getCause();
        if (cause instanceof ReportedException r) return r.getReport();
        return new CrashReport(title, cause);
    }

    public String getTitle() { return title; }

    public Throwable getException() { return exception; }
}
