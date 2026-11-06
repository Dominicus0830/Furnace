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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

public class LatestLogAppender extends FileAppender<ILoggingEvent> {

    private static final int MAX_INDEX = 1000;

    private LocalDate currentDay;

    @Override
    public void start() {
        Path latest = Path.of(getFile());
        try {
            if (Files.exists(latest) && Files.size(latest) > 0) {
                LocalDate day = LocalDate.ofInstant(Files.getLastModifiedTime(latest).toInstant(), ZoneId.systemDefault());
                archive(latest, day);
            }
        } catch (IOException e) {
            addError("latest.log 보관 실패", e);
        }
        setAppend(false);
        currentDay = LocalDate.now();
        super.start();
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
                        archive(Path.of(getFile()), prev);
                        openFile(getFile());
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
        Path dir = latest.toAbsolutePath().getParent();
        String prefix = day.toString();
        Pattern p = Pattern.compile(Pattern.quote(prefix) + "-(\\d+)\\.log\\.gz");
        int max = 0;
        try (var s = Files.list(dir)) {
            for (Path f : (Iterable<Path>) s::iterator) {
                Matcher m = p.matcher(f.getFileName().toString());
                if (m.matches()) max = Math.max(max, Integer.parseInt(m.group(1)));
            }
        }
        int idx = Math.min(max + 1, MAX_INDEX);
        Path target = dir.resolve(prefix + "-" + idx + ".log.gz");
        Path tmp = dir.resolve(prefix + "-" + idx + ".log.gz.tmp");
        try (InputStream in = Files.newInputStream(latest);
             OutputStream out = new GZIPOutputStream(Files.newOutputStream(tmp))) {
            in.transferTo(out);
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        Files.delete(latest);
        return target;
    }
}
