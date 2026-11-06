// 바닐라 JfrProfiler(JvmProfiler.INSTANCE) 대응: /jfr start·stop 이 쓰는 JFR 녹화 하나를 관리한다.
// 녹화는 debug/server-<yyyy-MM-dd-HHmmss>.jfr 로 덤프한다(바닐라와 같은 이름·위치).
// 차이: 바닐라는 Mojang 설정(flightrecorder-config.jfc)과 마인크래프트 전용 JFR 이벤트(ServerTickTime·PacketSent·ChunkGeneration …)를 켜는데,
// 여기는 JDK 기본 "profile" 설정만 쓰고 전용 이벤트는 없다. 멈춘 뒤 바닐라가 덤프를 읽어 만드는 jfr-report-*.json 요약도 만들지 않는다.
// 전역 스레드(명령)만 부른다.
package org.domi.furnace.server.profiling;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import jdk.jfr.Configuration;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JfrProfiler {

    private static final Logger log = LoggerFactory.getLogger(JfrProfiler.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss", Locale.ROOT).withZone(ZoneId.systemDefault());
    /** 바닐라 JfrProfiler 의 녹화 이름에 들어가는 게임 버전(SharedConstants.getCurrentVersion().getName()). */
    private static final String VERSION = "1.21.1";

    private static final JfrProfiler INSTANCE = new JfrProfiler();

    public static JfrProfiler instance() { return INSTANCE; }

    private Recording recording;

    private JfrProfiler() { }

    /** 바닐라 JvmProfiler.isAvailable: jdk.jfr 모듈이 있고 FlightRecorder 가 쓸 수 있다. */
    public static boolean isAvailable() {
        try {
            return Runtime.class.getModule().getLayer().findModule("jdk.jfr").isPresent() && FlightRecorder.isAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    /** 이미 녹화 중이거나 시작에 실패하면 거짓(경고 로그만 남기고 명령이 start.failed 로 알린다). */
    public synchronized boolean start() {
        if (recording != null) {
            log.warn("Profiling already in progress");
            return false;
        }
        try {
            Configuration config = Configuration.getConfiguration("profile");
            String stamp = STAMP.format(Instant.now());
            Recording r = new Recording(config);
            r.setDumpOnExit(true);
            r.setToDisk(true);
            r.setName(String.format(Locale.ROOT, "%s-%s-%s", "server", VERSION, stamp));
            Path destination = Paths.get(String.format(Locale.ROOT, "debug/%s-%s.jfr", "server", stamp));
            Files.createDirectories(destination.getParent());
            r.setDestination(destination);
            r.start();
            recording = r;
            log.info("Started flight recorder profiling id({}):name({}) - will dump to {} on exit or stop command", r.getId(), r.getName(), destination);
            return true;
        } catch (ParseException | IOException | RuntimeException e) {
            log.warn("Failed to start jfr profiling", e);
            return false;
        }
    }

    /** 녹화를 멈추고 덤프 경로를 돌려준다. 녹화 중이 아니면 IllegalStateException("Not currently profiling"). */
    public synchronized Path stop() {
        Recording r = recording;
        if (r == null) throw new IllegalStateException("Not currently profiling");
        Path destination = r.getDestination();
        recording = null;
        try {
            r.stop();
        } finally {
            r.close();
        }
        log.info("Dumped flight recorder profiling to {}", destination);
        return destination;
    }

    public synchronized boolean isRunning() { return recording != null; }
}
