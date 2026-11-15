// V-08 시험 영향 표: 시험 클래스 하나가 실제로 실행한 클래스 목록을 JaCoCo 에이전트에서 꺼내 파일로 남긴다.
// 켜는 조건은 시스템 속성 furnace.testImpact.dir 하나다(furnace-server/build.gradle.kts 가 -PtestImpact 일 때만 시험 JVM 에 준다).
// 속성이 없으면(평소 시험·IDE) 이 리스너는 아무것도 하지 않는다 — 시험 시간·결과에 영향이 없다. ServiceLoader 로 자동 등록된다(META-INF/services).
//
// 정확성의 전제: 시험 클래스 하나가 새 JVM 하나에서 돈다(-PtestImpact 가 forkEvery=1 을 건다). 같은 JVM 에서 앞 클래스가 만든 싱글턴·정적 초기화는
// 뒤 클래스 구간에 안 잡히기 때문이다(앞 구간에만 잡히면 그 코드를 고쳤을 때 뒤 클래스가 선택되지 않는다). 한 JVM 에 시험 클래스가 둘 이상 들어오면
// 모두 "믿을 수 없음"(unreliable)으로 적고, 선택 도구는 믿을 수 없는 시험을 항상 돌린다.
// 실패·중단(ABORTED)한 시험도 믿을 수 없음이다: 일찍 끝나 일부 코드를 안 밟았을 수 있다(에이전트가 시험을 느리게 해 제한시간으로 실패하는 경우 포함).
// 표를 만드는 쪽은 tools/ci/test-impact.py 다(docs/ci.md "시험 영향 선택").
package org.domi.furnace.server;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public final class TestImpactListener implements TestExecutionListener {

    static final String DIR_PROPERTY = "furnace.testImpact.dir";
    static final String OK = "ok";
    static final String UNRELIABLE = "unreliable";

    /** 최상위 시험 클래스(경로 꼴 a/b/FooTest) → 상태. */
    private final Map<String, String> classes = new LinkedHashMap<>();

    private static Path dir() {
        String p = System.getProperty(DIR_PROPERTY);
        return p == null || p.isBlank() ? null : Path.of(p);
    }

    /** 시험 식별자가 속한 최상위 클래스의 경로 꼴 이름. 클래스와 상관없으면 null. */
    private static String topLevel(TestIdentifier id) {
        String name = id.getSource().map(s -> {
            if (s instanceof ClassSource c) return c.getClassName();
            if (s instanceof MethodSource m) return m.getClassName();
            return null;
        }).orElse(null);
        if (name == null) return null;
        int d = name.indexOf('$');
        return (d < 0 ? name : name.substring(0, d)).replace('.', '/');
    }

    @Override
    public void executionStarted(TestIdentifier id) {
        if (dir() == null) return;
        if (id.isContainer() && id.getSource().filter(ClassSource.class::isInstance).isPresent()) {
            String top = topLevel(id);
            if (top != null) classes.putIfAbsent(top, OK);
        }
    }

    @Override
    public void executionFinished(TestIdentifier id, TestExecutionResult result) {
        if (dir() == null) return;
        if (result.getStatus() == TestExecutionResult.Status.SUCCESSFUL) return;
        String top = topLevel(id);
        if (top != null) classes.put(top, UNRELIABLE);   // 실패·중단: 일부 코드를 안 밟았을 수 있다
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        Path dir = dir();
        if (dir == null) return;
        try {
            Map<String, String> result = new LinkedHashMap<>(classes);
            if (result.size() != 1) result.replaceAll((k, v) -> UNRELIABLE);   // 한 JVM 에 둘 이상: 구간을 나눌 수 없다
            if (result.isEmpty()) return;
            byte[] data = agentData();
            StringBuilder sb = new StringBuilder("V1\n");
            result.forEach((c, s) -> sb.append("T\t").append(c).append('\t').append(s).append('\n'));
            for (String c : executedClasses(data)) sb.append("C\t").append(c).append('\n');
            Files.createDirectories(dir);
            Path tmp = Files.createTempFile(dir, "impact-", ".tmp");
            Files.writeString(tmp, sb.toString(), StandardCharsets.UTF_8);
            Files.move(tmp, dir.resolve("impact-" + ProcessHandle.current().pid() + "-" + System.nanoTime() + ".imp"),
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            // 기록하지 못하면 이 시험은 표에 없다 → 선택 도구가 "모르는 시험"으로 보고 항상 돌린다(안전한 쪽).
            System.err.println("[TestImpactListener] 영향 기록을 쓰지 못했다: " + e);
        }
    }

    /** JaCoCo 에이전트(-javaagent)에서 지금까지의 실행 데이터를 .exec 바이트로 받는다. 에이전트가 없으면 예외. */
    private static byte[] agentData() throws Exception {
        Class<?> rt = Class.forName("org.jacoco.agent.rt.RT");
        Object agent = rt.getMethod("getAgent").invoke(null);
        Method get = Class.forName("org.jacoco.agent.rt.IAgent").getMethod("getExecutionData", boolean.class);
        return (byte[]) get.invoke(agent, false);
    }

    /**
     * JaCoCo .exec 바이트에서 "점검 칸이 하나라도 지나간" 클래스 이름(내부 이름, a/b/Foo$1)을 모은다.
     * 형식(0.8.x, 버전 0x1007): 블록 0x01 = 머리(char 0xC0C0, char 버전), 0x10 = 세션(UTF, long, long),
     * 0x11 = 실행 데이터(long id, UTF 이름, boolean 배열 = 가변 정수 길이 + 8칸씩 묶은 바이트, 낮은 비트부터).
     */
    static List<String> executedClasses(byte[] exec) throws IOException {
        TreeSet<String> out = new TreeSet<>();
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(exec));
        boolean header = false;
        while (in.available() > 0) {
            int type = in.readUnsignedByte();
            switch (type) {
                case 0x01 -> {
                    if (in.readChar() != 0xC0C0) throw new IOException("JaCoCo 실행 데이터 머리가 아니다");
                    int version = in.readChar();
                    if (version != 0x1007) throw new IOException("지원하지 않는 JaCoCo 실행 데이터 버전: 0x" + Integer.toHexString(version));
                    header = true;
                }
                case 0x10 -> {
                    in.readUTF();
                    in.readLong();
                    in.readLong();
                }
                case 0x11 -> {
                    in.readLong();
                    String name = in.readUTF();
                    int length = readVarInt(in);
                    boolean hit = false;
                    for (int i = 0; i < (length + 7) / 8; i++) if (in.readUnsignedByte() != 0) hit = true;
                    if (hit) out.add(name);
                }
                default -> throw new IOException("알 수 없는 JaCoCo 블록 0x" + Integer.toHexString(type));
            }
        }
        if (!header) throw new IOException("JaCoCo 실행 데이터가 비었다");
        return new ArrayList<>(out);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        for (int shift = 0; ; shift += 7) {
            int b = in.readUnsignedByte();
            value |= (b & 0x7f) << shift;
            if ((b & 0x80) == 0) return value;
        }
    }
}
