// 순정 1.21.1 서버를 띄워 지정 청크 박스를 forceload 로 생성시키고 월드를 저장 후 종료하는 일회성 드라이버
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public class RunVanilla {
    static BlockingQueue<String> lines = new LinkedBlockingQueue<>();

    static boolean waitFor(String sub, long timeoutMs) throws Exception {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            String l = lines.poll(500, TimeUnit.MILLISECONDS);
            if (l != null && l.contains(sub)) return true;
        }
        return false;
    }

    static long regionBytes(Path dir) throws IOException {
        // RV_DIM=minecraft:the_nether|minecraft:the_end 이면 그 차원 폴더(DIM-1 / DIM1)의 region.
        String dim = System.getenv("RV_DIM");
        Path w = dir.resolve("world");
        if ("minecraft:the_nether".equals(dim)) w = w.resolve("DIM-1");
        else if ("minecraft:the_end".equals(dim)) w = w.resolve("DIM1");
        Path r = w.resolve("region");
        if (!Files.exists(r)) return 0;
        try (var s = Files.list(r)) {
            return s.mapToLong(p -> { try { return Files.size(p); } catch (IOException e) { return 0; } }).sum();
        }
    }

    // args: dir seed port jar java reverse(0/1) cxMin cxMax czMin czMax
    public static void main(String[] a) throws Exception {
        Path dir = Path.of(a[0]);
        long seed = Long.parseLong(a[1]);
        int port = Integer.parseInt(a[2]);
        String jar = a[3];
        String java = a[4];
        boolean reverse = a[5].equals("1");
        int cx0 = Integer.parseInt(a[6]), cx1 = Integer.parseInt(a[7]);
        int cz0 = Integer.parseInt(a[8]), cz1 = Integer.parseInt(a[9]);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("eula.txt"), "eula=true\n");
        Files.writeString(dir.resolve("server.properties"), String.join("\n",
            "level-seed=" + seed,
            "generate-structures=" + (a.length > 10 && a[10].equals("1")),
            "online-mode=false",
            "server-port=" + port,
            "view-distance=3",
            "simulation-distance=3",
            "spawn-monsters=false",
            "spawn-animals=false",
            "spawn-npcs=false",
            "allow-nether=false",
            "max-tick-time=-1",
            "spawn-protection=0",
            "enable-status=false",
            "sync-chunk-writes=false",
            "motd=furnace-parity-ground-truth",
            a.length > 11 ? "initial-enabled-packs=vanilla,file/" + Path.of(a[11]).getFileName() : "", ""));
        if (a.length > 11) {
            Path src = Path.of(a[11]);
            Path dst = dir.resolve("world").resolve("datapacks").resolve(src.getFileName());
            try (var w2 = Files.walk(src)) { for (Path p : (Iterable<Path>) w2::iterator) { Path t = dst.resolve(src.relativize(p).toString()); if (Files.isDirectory(p)) Files.createDirectories(t); else { Files.createDirectories(t.getParent()); Files.copy(p, t, StandardCopyOption.REPLACE_EXISTING); } } }
        }

        ProcessBuilder pb = new ProcessBuilder(java, "-Xmx3G", "-jar", jar, "nogui")
            .directory(dir.toFile()).redirectErrorStream(true);
        Process p = pb.start();
        PrintWriter w = new PrintWriter(new OutputStreamWriter(p.getOutputStream()), true);
        PrintWriter log = new PrintWriter(new FileWriter(dir.resolve("driver.log").toFile()), true);
        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String l;
                while ((l = r.readLine()) != null) { log.println(l); lines.add(l); }
            } catch (IOException e) { }
        });
        t.setDaemon(true);
        t.start();

        if (!waitFor("Done (", 300_000)) { System.err.println("server did not start"); p.destroyForcibly(); System.exit(2); }
        System.out.println("server up");
        w.println("tick freeze");
        Thread.sleep(1000);

        // RV_CHUNKLIST=파일("cx cz" 줄들)이면 사각형 대신 지정 청크만 1개씩 forceload — 서로 멀리 떨어진 청크의
        // 3x3 decoration이 겹치지 않게 해서 이웃 순서·경합에 오염되지 않은 "고립 대조군"을 만든다.
        String listFile = System.getenv("RV_CHUNKLIST");
        if (listFile != null && !listFile.isBlank()) {
            for (String line : Files.readAllLines(Path.of(listFile))) {
                String[] ct = line.trim().split("\\s+");
                if (ct.length != 2) continue;
                int lx = Integer.parseInt(ct[0]) * 16, lz = Integer.parseInt(ct[1]) * 16;
                String dimPrefix0 = System.getenv("RV_DIM") != null ? "execute in " + System.getenv("RV_DIM") + " run " : "";
                w.println(dimPrefix0 + "forceload add " + lx + " " + lz + " " + (lx + 15) + " " + (lz + 15));
                Thread.sleep(300);
            }
            System.out.println("forceload sent, chunk list");
        }
        // 16x16 청크 타일로 분할(forceload 한 번에 최대 256청크)
        List<int[]> tiles = new ArrayList<>();
        if (listFile != null && !listFile.isBlank()) { /* 목록 모드 — 타일 생략 */ } else
        for (int z = cz0; z <= cz1; z += 16)
            for (int x = cx0; x <= cx1; x += 16)
                tiles.add(new int[]{x, z, Math.min(x + 15, cx1), Math.min(z + 15, cz1)});
        if (reverse) Collections.reverse(tiles);
        for (int[] ti : tiles) {
            String dimPrefix = System.getenv("RV_DIM") != null ? "execute in " + System.getenv("RV_DIM") + " run " : "";
            w.println(dimPrefix + "forceload add " + (ti[0] * 16) + " " + (ti[1] * 16) + " " + (ti[2] * 16 + 15) + " " + (ti[3] * 16 + 15));
            Thread.sleep(1500);
        }
        System.out.println("forceload sent, tiles=" + tiles.size());

        long prev = -1;
        int stable = 0;
        for (int i = 0; i < 90 && stable < 3; i++) {
            Thread.sleep(20_000);
            w.println("save-all flush");
            waitFor("Saved the game", 120_000);
            long sz = regionBytes(dir);
            System.out.println("poll " + i + " region bytes=" + sz);
            if (sz == prev && sz > 0) stable++; else stable = 0;
            prev = sz;
        }
        w.println("stop");
        if (!p.waitFor(120, TimeUnit.SECONDS)) p.destroyForcibly();
        System.out.println("done exit=" + p.exitValue());
    }
}
