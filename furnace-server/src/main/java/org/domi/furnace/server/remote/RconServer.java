// 원격 콘솔(RCON) 서버 — 바닐라 RconThread/RconClient. 접속 수락과 클라이언트 읽기는 전용 IO 스레드(데몬)에서 하고,
// 명령 실행은 CommandRunner 가 전역 스레드로 넘긴다(여기서는 결과만 기다린다 — region/전역 스레드를 막지 않는다).
// 바닐라와의 차이: 바닐라는 소켓 read 한 번을 한 패킷으로 보지만(조각난 TCP 는 연결을 끊는다) 여기서는 길이 필드로 정확히 읽는다.
package org.domi.furnace.server.remote;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RconServer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RconServer.class);

    /** 명령을 실행하고 출력 문자열을 돌려준다(보통 전역 스레드로 넘기고 기다린다). 예외 메시지는 "Error executing" 응답이 된다. */
    @FunctionalInterface
    public interface CommandRunner {
        String run(String command) throws Exception;
    }

    private final ServerSocket server;
    private final String password;
    private final CommandRunner runner;
    private final Thread acceptor;
    private final List<Client> clients = new CopyOnWriteArrayList<>();
    private volatile boolean running = true;

    /** 보안 보강(바닐라에는 없음): 동시 연결 상한, 인증 전 읽기 제한 시간, 인증 실패 지연·연결당/IP당 실패 제한. */
    public static final int MAX_CLIENTS = 16;
    public static final int AUTH_TIMEOUT_MILLIS = 30_000;
    public static final int MAX_FAILS_PER_CONNECTION = 5;
    public static final int MAX_FAILS_PER_IP = 10;
    public static final long IP_WINDOW_MILLIS = 60_000L;
    private volatile long failDelayMillis = 1000L;
    private final java.util.concurrent.atomic.AtomicInteger clientSeq = new java.util.concurrent.atomic.AtomicInteger();
    /** IP → {실패 수, 창 시작(ms)}. */
    private final java.util.concurrent.ConcurrentHashMap<String, long[]> ipFails = new java.util.concurrent.ConcurrentHashMap<>();

    /** 시험용: 인증 실패 뒤 지연(ms). */
    public void failDelayMillis(long ms) { this.failDelayMillis = ms; }

    private boolean ipBlocked(String ip) {
        long[] f = ipFails.get(ip);
        return f != null && System.currentTimeMillis() - f[1] < IP_WINDOW_MILLIS && f[0] >= MAX_FAILS_PER_IP;
    }

    private void noteIpFail(String ip) {
        long now = System.currentTimeMillis();
        ipFails.compute(ip, (k, f) -> {
            if (f == null || now - f[1] >= IP_WINDOW_MILLIS) return new long[] {1, now};
            f[0]++;
            return f;
        });
        if (ipFails.size() > 4096) ipFails.values().removeIf(f -> now - f[1] >= IP_WINDOW_MILLIS);
    }

    private RconServer(ServerSocket server, String password, CommandRunner runner) {
        this.server = server;
        this.password = password;
        this.runner = runner;
        this.acceptor = new Thread(this::acceptLoop, "RCON Listener");
        this.acceptor.setDaemon(true);
    }

    /** 바닐라 RconThread.create: 포트 범위 밖이거나 비밀번호가 비면 경고하고 null(꺼짐), 바인딩 실패도 null. */
    public static RconServer create(String ip, int port, String password, CommandRunner runner) {
        if (ip == null || ip.isEmpty()) ip = "0.0.0.0";
        if (port <= 0 || port > 65535) {
            log.warn("Invalid rcon port {} found in server.properties, rcon disabled!", port);
            return null;
        }
        if (password.isEmpty()) {
            log.warn("No rcon password set in server.properties, rcon disabled!");
            return null;
        }
        try {
            ServerSocket s = new ServerSocket(port, 0, InetAddress.getByName(ip));
            if (ip.equals("0.0.0.0") || ip.equals("::")) {
                log.warn("RCON 은 암호화되지 않은 평문 프로토콜이다 — 모든 주소({})로 열렸다. 방화벽으로 막거나 server-ip 를 loopback 으로 제한하는 것을 권장한다", ip);
            }
            RconServer r = open(s, password, runner);
            log.info("RCON running on {}:{}", ip, port);
            return r;
        } catch (IOException e) {
            log.warn("Unable to initialise RCON on {}:{}", ip, port, e);
            return null;
        }
    }

    /** 이미 연 소켓으로 시작한다(시험 — 포트 0). */
    public static RconServer open(ServerSocket socket, String password, CommandRunner runner) {
        RconServer r = new RconServer(socket, password, runner);
        r.acceptor.start();
        return r;
    }

    public int port() { return server.getLocalPort(); }

    private void acceptLoop() {
        while (running) {
            try {
                Socket s = server.accept();
                clients.removeIf(x -> !x.thread.isAlive() && x.started);
                if (clients.size() >= MAX_CLIENTS || ipBlocked(s.getInetAddress().getHostAddress())) {
                    try {
                        s.close();
                    } catch (IOException ignored) {
                        // 이미 닫힘
                    }
                    continue;
                }
                Client c = new Client(s);
                clients.add(c);
                c.start();
            } catch (IOException e) {
                if (running) log.info("IO exception: ", e);
            }
        }
    }

    @Override
    public void close() {
        running = false;
        try {
            server.close();
        } catch (IOException e) {
            log.warn("Failed to close socket", e);
        }
        for (Client c : clients) c.close();
        clients.clear();
        try {
            acceptor.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private final class Client implements Runnable {
        final Socket socket;
        final Thread thread;
        volatile boolean started;
        private boolean authed;
        private int fails;

        Client(Socket socket) {
            this.socket = socket;
            this.thread = new Thread(this, "RCON Client #" + clientSeq.incrementAndGet());   // IP 는 이름에 넣지 않는다(크래시 리포트 공개 시 노출 방지)
            this.thread.setDaemon(true);
        }

        void start() {
            started = true;
            thread.start();
        }

        @Override
        public void run() {
            try (socket) {
                socket.setSoTimeout(AUTH_TIMEOUT_MILLIS);   // 인증 전에는 30초 안에 말을 해야 한다
                DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(socket.getInputStream()));
                OutputStream out = socket.getOutputStream();
                while (running) {
                    int length = Integer.reverseBytes(in.readInt());
                    if (!RconProtocol.validLength(length)) return;   // 바닐라: 길이가 맞지 않으면 연결을 끊는다
                    byte[] body = new byte[length];
                    in.readFully(body);
                    RconProtocol.Request req = RconProtocol.decode(body);
                    if (req == null) return;
                    handle(req, out);
                }
            } catch (EOFException | SocketException ignored) {
                // 정상 종료/연결 끊김
            } catch (IOException ignored) {
                // 읽기/쓰기 실패 — 끊는다
            } catch (RuntimeException e) {
                log.error("Exception whilst parsing RCON input", e);
            } finally {
                log.info("Thread {} shutting down", thread.getName());
            }
        }

        private void handle(RconProtocol.Request req, OutputStream out) throws IOException {
            switch (req.type()) {
                case RconProtocol.TYPE_COMMAND -> {
                    if (!authed) {
                        authFailed(out);
                        return;
                    }
                    String result;
                    try {
                        result = runner.run(req.payload());
                    } catch (Exception e) {
                        result = "Error executing: " + req.payload() + " (" + e.getMessage() + ")";
                    }
                    for (byte[] p : RconProtocol.encodeResponse(req.id(), result == null ? "" : result)) out.write(p);
                    out.flush();
                }
                case RconProtocol.TYPE_LOGIN -> {
                    if (!req.payload().isEmpty() && java.security.MessageDigest.isEqual(
                            req.payload().getBytes(java.nio.charset.StandardCharsets.UTF_8), password.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                        authed = true;
                        socket.setSoTimeout(0);
                        out.write(RconProtocol.encode(req.id(), RconProtocol.TYPE_AUTH_RESPONSE, ""));
                        out.flush();
                    } else {
                        authed = false;
                        authFailed(out);
                        noteIpFail(socket.getInetAddress().getHostAddress());
                        if (++fails >= MAX_FAILS_PER_CONNECTION) throw new IOException("too many auth failures");
                        try {
                            Thread.sleep(failDelayMillis);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IOException(e);
                        }
                    }
                }
                default -> {
                    for (byte[] p : RconProtocol.encodeResponse(req.id(),
                            String.format(Locale.ROOT, "Unknown request %s", Integer.toHexString(req.type())))) out.write(p);
                    out.flush();
                }
            }
        }

        private void authFailed(OutputStream out) throws IOException {
            out.write(RconProtocol.encode(RconProtocol.AUTH_FAILED_ID, RconProtocol.TYPE_AUTH_RESPONSE, ""));
            out.flush();
        }

        void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
                // 이미 닫힘
            }
        }
    }
}
