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

    /** 보안 보강(바닐라에는 없음): 동시 연결 상한(전체·IP당), 인증 전 읽기 제한 시간, 인증 실패 지연·연결당/IP당 실패 제한. */
    public static final int MAX_CLIENTS = RconLimits.DEFAULT_MAX_CLIENTS;
    /** 한 IP 가 동시에 붙잡을 수 있는 연결 수 — 한 주소가 전체 상한을 다 차지해 관리 도구가 못 붙는 일을 막는다. */
    public static final int MAX_CLIENTS_PER_IP = RconLimits.DEFAULT_MAX_CLIENTS_PER_IP;
    /** 기본값은 위 상수, furnace.properties 의 rcon.* 키로 바꾼다({@link RconLimits}). */
    private final RconLimits limits;
    private volatile int maxClientsPerIp;
    /**
     * 인증 전 제한 시간 — 수락한 때부터 재는 절대 마감이다(읽기마다 다시 세지 않는다). 그 안에 올바른 비밀번호를 보내지 않으면 끊고 IP 실패로 센다.
     * 마지막 읽기 기준이면 인증 없이 20초마다 아무 패킷이나 보내 자리를 무기한 붙잡을 수 있었다(A02-02).
     */
    public static final int AUTH_TIMEOUT_MILLIS = 30_000;
    private volatile long authTimeoutMillis = AUTH_TIMEOUT_MILLIS;
    public static final int MAX_FAILS_PER_CONNECTION = RconLimits.DEFAULT_MAX_FAILS_PER_CONNECTION;
    public static final int MAX_FAILS_PER_IP = RconLimits.DEFAULT_MAX_FAILS_PER_IP;
    public static final long IP_WINDOW_MILLIS = 60_000L;
    private volatile long failDelayMillis = 1000L;
    private final java.util.concurrent.atomic.AtomicInteger clientSeq = new java.util.concurrent.atomic.AtomicInteger();
    /** IP → {실패 수, 창 시작(ms)}. */
    private final java.util.concurrent.ConcurrentHashMap<String, long[]> ipFails = new java.util.concurrent.ConcurrentHashMap<>();

    /** 시험용: 인증 실패 뒤 지연(ms). */
    public void failDelayMillis(long ms) { this.failDelayMillis = ms; }

    /** 시험용: 인증 전 절대 마감(ms). */
    public void authTimeoutMillis(long ms) { this.authTimeoutMillis = ms; }

    /** 시험용: IP당 동시 연결 상한(시험은 모두 루프백 한 주소라 전체 상한 시험에서 올린다). */
    public void maxClientsPerIp(int n) { this.maxClientsPerIp = n; }

    /** 지금 살아 있는 연결 가운데 그 IP 의 수(수락 스레드에서만 부른다). */
    private int clientsFrom(String ip) {
        int n = 0;
        for (Client c : clients) if (c.ip.equals(ip)) n++;
        return n;
    }

    private boolean ipBlocked(String ip) {
        long[] f = ipFails.get(ip);
        return f != null && System.currentTimeMillis() - f[1] < IP_WINDOW_MILLIS && f[0] >= limits.maxFailsPerIp();
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

    private RconServer(ServerSocket server, String password, CommandRunner runner, RconLimits limits) {
        this.limits = limits;
        this.maxClientsPerIp = limits.maxClientsPerIp();
        this.server = server;
        this.password = password;
        this.runner = runner;
        this.acceptor = new Thread(this::acceptLoop, "RCON Listener");
        this.acceptor.setDaemon(true);
    }

    /** 바닐라 RconThread.create: 포트 범위 밖이거나 비밀번호가 비면 경고하고 null(꺼짐), 바인딩 실패도 null. */
    public static RconServer create(String ip, int port, String password, CommandRunner runner) {
        return create(ip, port, password, runner, RconLimits.DEFAULT);
    }

    public static RconServer create(String ip, int port, String password, CommandRunner runner, RconLimits limits) {
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
            RconServer r = open(s, password, runner, limits);
            log.info("RCON running on {}:{} (max {} clients, {} per IP)", ip, port, limits.maxClients(), limits.maxClientsPerIp());
            return r;
        } catch (IOException e) {
            log.warn("Unable to initialise RCON on {}:{}", ip, port, e);
            return null;
        }
    }

    /** 이미 연 소켓으로 시작한다(시험 — 포트 0). */
    public static RconServer open(ServerSocket socket, String password, CommandRunner runner) {
        return open(socket, password, runner, RconLimits.DEFAULT);
    }

    public static RconServer open(ServerSocket socket, String password, CommandRunner runner, RconLimits limits) {
        RconServer r = new RconServer(socket, password, runner, limits);
        r.acceptor.start();
        return r;
    }

    public int port() { return server.getLocalPort(); }

    /** 시험·관측용: 지금 붙어 있는(스레드가 살아 있는) 클라이언트 수. */
    public int clientCount() {
        int n = 0;
        for (Client c : clients) if (c.thread.isAlive()) n++;
        return n;
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket s = server.accept();
                clients.removeIf(x -> !x.thread.isAlive() && x.started);
                String ip = s.getInetAddress().getHostAddress();
                if (clientsFrom(ip) >= maxClientsPerIp || ipBlocked(ip)
                        || clients.size() >= limits.maxClients() && !evictOldestUnauthenticated()) {
                    try {
                        s.close();
                    } catch (IOException ignored) {
                        // 이미 닫힘
                    }
                    continue;
                }
                Client c = new Client(s, ip);
                clients.add(c);
                c.start();
            } catch (IOException e) {
                if (running) log.info("IO exception: ", e);
            }
        }
    }

    /**
     * 전체 자리가 찼을 때 가장 오래된 미인증 연결을 닫아 자리를 낸다(수락 스레드에서만 부른다). 모두 인증된 연결이면 false — 새 연결을 거절한다.
     * 거절만 하면 주소 몇 개가 미인증 연결로 자리를 채워 올바른 비밀번호의 관리자도 못 들어온다(A02-02). 밀려난 연결은 그 IP 의 실패로 세서,
     * 계속 밀어내며 자리를 돌리는 주소는 IP 차단에 걸린다. 같은 IP 의 상한·IP 차단 검사는 이보다 먼저다.
     */
    private boolean evictOldestUnauthenticated() {
        while (true) {
            Client oldest = null;
            for (Client c : clients) {
                if (c.state.get() == PENDING && (oldest == null || c.acceptedAt < oldest.acceptedAt)) oldest = c;
            }
            if (oldest == null) return false;
            if (!oldest.state.compareAndSet(PENDING, EVICTED)) continue;   // 방금 인증했다 — 다음 후보
            clients.remove(oldest);
            // 자리를 여럿 붙잡은 IP 이거나 오래 묵은 연결만 실패로 센다 — 로그인 직전에 밀려난 관리자의 연결 하나는 세지 않는다.
            if (pendingFrom(oldest.ip) >= 1 || System.nanoTime() - oldest.acceptedAt >= EVICT_CHARGE_AGE_NANOS) noteIpFail(oldest.ip);
            oldest.close();
            return true;
        }
    }

    private static final long EVICT_CHARGE_AGE_NANOS = 5_000_000_000L;

    private int pendingFrom(String ip) {
        int n = 0;
        for (Client c : clients) if (c.ip.equals(ip) && c.state.get() == PENDING) n++;
        return n;
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

    private static final int PENDING = 0, AUTHED = 1, EVICTED = 2;

    private final class Client implements Runnable {
        final Socket socket;
        final String ip;
        final Thread thread;
        final long acceptedAt = System.nanoTime();
        /** PENDING(인증 전) → AUTHED, 또는 수락 스레드가 밀어내면 EVICTED. 인증과 밀어내기가 겹쳐도 한쪽만 이긴다. */
        final java.util.concurrent.atomic.AtomicInteger state = new java.util.concurrent.atomic.AtomicInteger(PENDING);
        volatile boolean started;
        private boolean authed;
        private int fails;

        Client(Socket socket, String ip) {
            this.socket = socket;
            this.ip = ip;
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
                // 인증 전에는 수락 후 30초 안에 로그인을 끝내야 한다 — 읽기 호출마다 남은 시간으로 제한 시간을 다시 건다.
                long deadline = acceptedAt + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(authTimeoutMillis);
                DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(new DeadlineInput(socket, deadline)));
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
            } catch (java.net.SocketTimeoutException timeout) {
                if (state.get() == PENDING) noteIpFail(ip);   // 인증 마감 초과 — 자리만 붙잡는 연결
            } catch (EOFException | SocketException ignored) {
                // 정상 종료/연결 끊김
            } catch (IOException ignored) {
                // 읽기/쓰기 실패 — 끊는다
            } catch (RuntimeException e) {
                log.error("Exception whilst parsing RCON input", e);
            } finally {
                state.compareAndSet(PENDING, EVICTED);   // 끝난 연결은 밀어내기·실패 계산 후보가 아니다
                log.info("Thread {} shutting down", thread.getName());
            }
        }

        private void handle(RconProtocol.Request req, OutputStream out) throws IOException {
            switch (req.type()) {
                case RconProtocol.TYPE_COMMAND -> {
                    if (!authed) {
                        authFailed(out);   // 바닐라와 같은 응답(-1). 응답이 비밀번호 실패와 같으니 실패로도 센다(A02-02)
                        preAuthFail();
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
                        if (state.get() != AUTHED && !state.compareAndSet(PENDING, AUTHED)) throw new IOException("evicted");
                        authed = true;
                        out.write(RconProtocol.encode(req.id(), RconProtocol.TYPE_AUTH_RESPONSE, ""));
                        out.flush();
                    } else {
                        authed = false;
                        noteIpFail(ip);   // 응답을 쓰기 전에 센다(바로 재접속하는 클라이언트가 차단 검사에서 앞서지 않게)
                        authFailed(out);
                        if (++fails >= limits.maxFailsPerConnection()) throw new IOException("too many auth failures");
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
                    if (!authed) preAuthFail();
                }
            }
        }

        /** 인증 전 로그인이 아닌 패킷: 연결당·IP당 실패로 센다(응답은 바닐라 그대로). 지연은 두지 않는다(비밀번호 추측이 아니다). */
        private void preAuthFail() throws IOException {
            noteIpFail(ip);
            if (++fails >= limits.maxFailsPerConnection()) throw new IOException("too many unauthenticated requests");
        }

        private void authFailed(OutputStream out) throws IOException {
            out.write(RconProtocol.encode(RconProtocol.AUTH_FAILED_ID, RconProtocol.TYPE_AUTH_RESPONSE, ""));
            out.flush();
        }

        /** 인증 전에는 읽기마다 남은 시간을 소켓 제한 시간으로 건다(바이트를 조금씩 흘려도 마감은 그대로). 인증 뒤에는 제한 없음(바닐라). */
        private final class DeadlineInput extends java.io.InputStream {
            private final Socket s;
            private final java.io.InputStream raw;
            private final long deadline;
            private int applied = -1;

            DeadlineInput(Socket s, long deadline) throws IOException {
                this.s = s;
                this.raw = s.getInputStream();
                this.deadline = deadline;
            }

            private void arm() throws IOException {
                int want;
                if (state.get() == AUTHED) {
                    want = 0;
                } else {
                    long left = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
                    if (left <= 0) throw new java.net.SocketTimeoutException("RCON authentication timed out");
                    want = (int) Math.min(Integer.MAX_VALUE, left);
                }
                if (want != applied) {
                    s.setSoTimeout(want);
                    applied = want;
                }
            }

            @Override
            public int read() throws IOException {
                arm();
                return raw.read();
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                arm();
                return raw.read(b, off, len);
            }
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
