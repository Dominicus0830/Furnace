package net.minecraft;

/** 바닐라: 오류 보고를 실은 실행 중 예외. 문구는 보고 제목, 원인은 보고의 예외. */
public class ReportedException extends RuntimeException {

    private final CrashReport report;

    public ReportedException(CrashReport report) { this.report = report; }

    public CrashReport getReport() { return report; }

    @Override
    public synchronized Throwable getCause() { return report.getException(); }

    @Override
    public String getMessage() { return report.getTitle(); }
}
