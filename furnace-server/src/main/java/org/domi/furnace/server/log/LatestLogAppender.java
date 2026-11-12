// 바닐라 log4j2 설정(RollingRandomAccessFile logs/latest.log, filePattern logs/%d{yyyy-MM-dd}-%i.log.gz,
// OnStartup + TimeBased 트리거, DefaultRolloverStrategy max=1000)과 같은 파일 배치를 logback 으로 재현한다.
// 기동 시 기존 latest.log 를 그 파일의 마지막 수정 날짜로 압축 보관하고, 실행 중 날짜가 바뀌면 같은 방식으로 넘긴다.
package org.domi.furnace.server.log;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

public class LatestLogAppender extends FileAppender<ILoggingEvent> {

    private static final int MAX_INDEX = 1000;

    private static final String PENDING = ".pending";

    private LocalDate currentDay;
    /** 날짜 변경 롤오버의 gzip 압축을 락 밖에서 하는 단일 스레드(로깅 스레드를 막지 않는다). */
    private ExecutorService compressor;

    @Override
    public void start() {
        Path latest = Path.of(getFile());
        try {
            recoverPending(latest);
            if (Files.exists(latest) && Files.size(latest) > 0) {
                LocalDate day = LocalDate.ofInstant(Files.getLastModifiedTime(latest).toInstant(), ZoneId.systemDefault());
                archive(latest, day);
            }
        } catch (IOException e) {
            addError("latest.log 보관 실패", e);
        }
        setAppend(false);
        currentDay = LocalDate.now();
        compressor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "log-rollover-gzip");
            t.setDaemon(true);
            return t;
        });
        super.start();
    }

    @Override
    public void stop() {
        super.stop();
        if (compressor != null) {
            compressor.shutdown();
            try {
                if (!compressor.awaitTermination(60, TimeUnit.SECONDS)) addWarn("로그 압축이 60초 안에 끝나지 않음");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    protected void subAppend(ILoggingEvent event) {
        LocalDate day = LocalDate.ofInstant(Instant.ofEpochMilli(event.getTimeStamp()), ZoneId.systemDefault());
        if (!day.equals(currentDay)) {
            streamWriteLock.lock();
            try {
                if (!day.equals(currentDay)) {
                    LocalDate prev = currentDay;
                    currentDay = day;
                    closeOutputStream();
                    try {
                        // 락 안에서는 이름 고르기·이름 바꾸기·새 파일 열기만 한다(빠름). gzip 은 락 밖에서.
                        Path pending = prepare(Path.of(getFile()), prev);
                        openFile(getFile());
                        if (pending != null) compressor.execute(() -> compressQuietly(pending));
                    } catch (IOException e) {
                        addError("날짜 변경 로그 롤링 실패", e);
                    }
                }
            } finally {
                streamWriteLock.unlock();
            }
        }
        super.subAppend(event);
    }

    /** dir/<day>-<i>.log.gz 로 압축(i = 그날 기존 최대 번호 + 1, 1..1000). */
    static Path archive(Path latest, LocalDate day) throws IOException {
        Path pending = prepare(latest, day);
        return pending == null ? null : compress(pending);
    }

    /**
     * 번호를 정하고 latest 를 dir/<day>-<i>.log.gz.pending 으로 옮긴다(같은 폴더 이름 바꾸기). 번호 계산은
     * 압축 중인 .pending·.tmp 도 세므로 연속 롤오버에서도 번호가 겹치지 않는다.
     */
    static Path prepare(Path latest, LocalDate day) throws IOException {
        Path dir = latest.toAbsolutePath().getParent();
        String prefix = day.toString();
        Pattern p = Pattern.compile(Pattern.quote(prefix) + "-(\\d+)\\.log\\.gz(?:\\.tmp|" + Pattern.quote(PENDING) + ")?");
        int max = 0;
        try (var s = Files.list(dir)) {
            for (Path f : (Iterable<Path>) s::iterator) {
                Matcher m = p.matcher(f.getFileName().toString());
                if (m.matches()) max = Math.max(max, Integer.parseInt(m.group(1)));
            }
        }
        int idx = Math.min(max + 1, MAX_INDEX);
        Path pending = dir.resolve(prefix + "-" + idx + ".log.gz" + PENDING);
        Files.move(latest, pending, StandardCopyOption.REPLACE_EXISTING);
        return pending;
    }

    /** .pending 를 gzip 으로 압축해 .log.gz 로 만들고 원본을 지운다. */
    static Path compress(Path pending) throws IOException {
        String name = pending.getFileName().toString();
        String base = name.substring(0, name.length() - PENDING.length());
        Path target = pending.resolveSibling(base);
        Path tmp = pending.resolveSibling(base + ".tmp");
        try (InputStream in = Files.newInputStream(pending);
             OutputStream out = new GZIPOutputStream(Files.newOutputStream(tmp))) {
            in.transferTo(out);
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        Files.delete(pending);
        return target;
    }

    /** 이전 실행이 압축 도중 끝났다면 남은 .pending 을 마저 압축한다. */
    private static void recoverPending(Path latest) throws IOException {
        Path dir = latest.toAbsolutePath().getParent();
        if (dir == null || !Files.isDirectory(dir)) return;
        try (var s = Files.list(dir)) {
            for (Path f : (Iterable<Path>) s::iterator) {
                if (f.getFileName().toString().endsWith(".log.gz" + PENDING)) compress(f);
            }
        }
    }

    private void compressQuietly(Path pending) {
        try {
            compress(pending);
        } catch (IOException e) {
            addError("로그 압축 실패: " + pending, e);
        }
    }
}
