# M0 — 토대 + 스레딩 골격 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Furnace의 멀티스레드 토대(스레드 소유권 + region 비동기 스케줄러 + 20 TPS 틱 루프)를 Gradle 멀티모듈로 세운다.

**Architecture:** `furnace-api`에 스레드 소유권(`ThreadOwner`/`Owned`)과 비동기 스케줄러 형태(`TaskScheduler` → `CompletableFuture` 반환)를 인터페이스로 고정한다. `furnace-server`가 단일 스레드 큐 드레인 루프(`RegionThread`, M0는 region 1개)와 전역 스레드(`GlobalThread`)로 구현한다. 모든 스케줄 연산은 M0부터 비동기 시그니처라 region이 N개로 늘어도 시그니처가 안 바뀐다(retrofit 회피).

**Tech Stack:** Java 21, Gradle (Kotlin DSL) 멀티모듈, JUnit 5, SLF4J + Logback.

**핵심 설계 결정 (설계서 4.4 정련):** EntityScheduler를 M0에서 추측 구현하지 않는다. 대신 공통 `TaskScheduler`(execute/submit/executeDelayed, 전부 `CompletableFuture` 반환) 형태를 고정하고 global·region·(미래)entity를 같은 형태를 공유하는 "owner"로 둔다. M3에서 `FurnaceScheduler.entity(...)` 추가 시 시그니처 불변.

**전제:** Gradle을 실행하는 JDK가 21이다. (`./gradlew -version`으로 확인) 테스트는 assertion 활성(`-ea`)으로 돈다 — `Owned` 가드 검증에 필수, 각 모듈 build에 명시한다.

**최종 패키지 맵:**
- `org.domi.furnace.api.concurrent` — `ThreadOwner`, `Owned`
- `org.domi.furnace.api.sched` — `RegionPos`, `TaskScheduler`, `GlobalScheduler`, `FurnaceScheduler`
- `org.domi.furnace.server.sched` — `TickTimer`, `GlobalThread`, `RegionThread`, `FurnaceSchedulerImpl`
- `org.domi.furnace.server.config` — `Config`, `ConfigLoader`
- `org.domi.furnace.server` — `FurnaceMain`
- `org.domi.furnace.plugintest` — `SamplePlugin`

---

## File Structure

| 파일 | 책임 |
|---|---|
| `settings.gradle.kts` | 3개 모듈 include |
| `build.gradle.kts` (root) | 빈 aggregator |
| `furnace-api/build.gradle.kts` | api 빌드, JUnit, `-ea` |
| `furnace-server/build.gradle.kts` | server 빌드, api 의존, logback, application 플러그인 |
| `furnace-plugin-test/build.gradle.kts` | api 의존만 |
| `furnace-api/.../concurrent/ThreadOwner.java` | 소유 스레드 판별 추상 |
| `furnace-api/.../concurrent/Owned.java` | 비소유 스레드 접근 차단 래퍼 |
| `furnace-api/.../sched/RegionPos.java` | region 좌표 값 타입 |
| `furnace-api/.../sched/TaskScheduler.java` | 비동기 스케줄 공통 형태 |
| `furnace-api/.../sched/GlobalScheduler.java` | 전역 스케줄러 마커 |
| `furnace-api/.../sched/FurnaceScheduler.java` | 스케줄러 파사드 |
| `furnace-server/.../sched/TickTimer.java` | 드리프트 보정 틱 타이밍(순수) |
| `furnace-server/.../sched/GlobalThread.java` | 전역 단일 스레드 owner |
| `furnace-server/.../sched/RegionThread.java` | 틱 루프 + 큐 드레인 region owner |
| `furnace-server/.../sched/FurnaceSchedulerImpl.java` | 파사드 구현 |
| `furnace-server/.../config/Config.java` | 설정 값 레코드 |
| `furnace-server/.../config/ConfigLoader.java` | properties 로더(없으면 기본값) |
| `furnace-server/.../FurnaceMain.java` | 부팅·배선·셧다운 |
| `furnace-plugin-test/.../SamplePlugin.java` | api만으로 컴파일됨 증명 |

---

## Task 1: Gradle 멀티모듈 재구성 + 로깅

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `build.gradle.kts` (root)
- Create: `furnace-api/build.gradle.kts`
- Create: `furnace-server/build.gradle.kts`
- Create: `furnace-plugin-test/build.gradle.kts`
- Create: `furnace-server/src/main/resources/logback.xml`
- Delete: `src/` (루트의 기존 `src/main/java/org/domi/furnace/Main.java` 포함)

- [ ] **Step 1: 루트 `settings.gradle.kts` 교체**

```kotlin
rootProject.name = "Furnace"
include("furnace-api", "furnace-server", "furnace-plugin-test")
```

- [ ] **Step 2: 루트 `build.gradle.kts` 비우기 (aggregator)**

```kotlin
// 멀티모듈 루트. 빌드 로직은 각 서브모듈이 보유한다.
```

- [ ] **Step 3: 루트 `src/` 삭제**

Run: `git rm -r src`
Expected: `src/main/java/org/domi/furnace/Main.java` 가 삭제 스테이징됨.

- [ ] **Step 4: `furnace-api/build.gradle.kts` 생성**

```kotlin
plugins {
    id("java-library")
}

repositories { mavenCentral() }

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    enableAssertions = true
}
```

- [ ] **Step 5: `furnace-server/build.gradle.kts` 생성**

```kotlin
plugins {
    id("java")
    id("application")
}

repositories { mavenCentral() }

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    implementation(project(":furnace-api"))
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("ch.qos.logback:logback-classic:1.5.12")

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass = "org.domi.furnace.server.FurnaceMain"
}

tasks.test {
    useJUnitPlatform()
    enableAssertions = true
}
```

- [ ] **Step 6: `furnace-plugin-test/build.gradle.kts` 생성**

```kotlin
plugins {
    id("java")
}

repositories { mavenCentral() }

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    implementation(project(":furnace-api"))
}
```

- [ ] **Step 7: `furnace-server/src/main/resources/logback.xml` 생성**

```xml
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{20} - %msg%n</pattern>
        </encoder>
    </appender>
    <root level="INFO">
        <appender-ref ref="STDOUT"/>
    </root>
</configuration>
```

- [ ] **Step 8: 빌드 검증**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL. 3개 모듈 구성(소스 없어도 통과).

- [ ] **Step 9: 커밋**

```bash
git add -A
git commit -m "chore: restructure into furnace-api/server/plugin-test modules" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: API 동시성 코어 — ThreadOwner + Owned

**Files:**
- Create: `furnace-api/src/main/java/org/domi/furnace/api/concurrent/ThreadOwner.java`
- Create: `furnace-api/src/main/java/org/domi/furnace/api/concurrent/Owned.java`
- Test: `furnace-api/src/test/java/org/domi/furnace/api/concurrent/OwnedTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`OwnedTest.java`:
```java
// 소유 스레드 가드(Owned)의 접근 허용/차단 동작 테스트
package org.domi.furnace.api.concurrent;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class OwnedTest {

    private static ThreadOwner fixedOwner(boolean isOwner) {
        return new ThreadOwner() {
            public boolean isOwnerThread() { return isOwner; }
            public String ownerName() { return "test-owner"; }
        };
    }

    @Test
    void get_returnsValue_whenCalledByOwner() {
        Owned<String> owned = new Owned<>("hello", fixedOwner(true));
        assertEquals("hello", owned.get());
    }

    @Test
    void get_throwsAssertionError_whenCalledByNonOwner() {
        Owned<String> owned = new Owned<>("hello", fixedOwner(false));
        assertThrows(AssertionError.class, owned::get);
    }

    @Test
    void owner_isExposed() {
        ThreadOwner owner = fixedOwner(true);
        Owned<String> owned = new Owned<>("hello", owner);
        assertSame(owner, owned.owner());
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew :furnace-api:test`
Expected: 컴파일 실패 — `ThreadOwner`/`Owned` 없음.

- [ ] **Step 3: `ThreadOwner.java` 작성**

```java
// 어떤 객체/작업을 소유한 스레드를 나타내는 추상. 소유 스레드에서만 접근 허용.
package org.domi.furnace.api.concurrent;

public interface ThreadOwner {

    /** 현재 실행 중인 스레드가 이 owner의 소유 스레드인가. */
    boolean isOwnerThread();

    /** 진단/로그용 owner 이름. */
    String ownerName();
}
```

- [ ] **Step 4: `Owned.java` 작성**

```java
// 특정 스레드가 소유한 값을 감싸 비소유 스레드의 직접 접근을 막는 래퍼
package org.domi.furnace.api.concurrent;

public final class Owned<T> {

    private final T value;
    private final ThreadOwner owner;

    public Owned(T value, ThreadOwner owner) {
        this.value = value;
        this.owner = owner;
    }

    /**
     * 소유 스레드에서만 값 반환. 비소유 스레드 접근은 assertion 활성 빌드에서 AssertionError.
     * (운영 빌드에서 assertion 비활성 시 가드는 비용 0으로 사라진다 — 디버그 전용 불변식.)
     */
    public T get() {
        assert owner.isOwnerThread()
                : "Owned 접근 위반: 소유자=" + owner.ownerName()
                + ", 현재 스레드=" + Thread.currentThread().getName();
        return value;
    }

    public ThreadOwner owner() {
        return owner;
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :furnace-api:test`
Expected: PASS (3 tests).

- [ ] **Step 6: 커밋**

```bash
git add furnace-api
git commit -m "feat(api): add ThreadOwner and Owned guard" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: API 스케줄러 인터페이스

**Files:**
- Create: `furnace-api/src/main/java/org/domi/furnace/api/sched/RegionPos.java`
- Create: `furnace-api/src/main/java/org/domi/furnace/api/sched/TaskScheduler.java`
- Create: `furnace-api/src/main/java/org/domi/furnace/api/sched/GlobalScheduler.java`
- Create: `furnace-api/src/main/java/org/domi/furnace/api/sched/FurnaceScheduler.java`

이 Task는 인터페이스(컴파일 계약)만 정의한다. 동작 검증은 구현 Task(6·7·9)에서 한다.

- [ ] **Step 1: `RegionPos.java` 작성**

```java
// 월드를 나누는 지역(region)을 식별하는 좌표 값 타입
package org.domi.furnace.api.sched;

public record RegionPos(int x, int z) {}
```

- [ ] **Step 2: `TaskScheduler.java` 작성**

```java
// 작업을 특정 owner 스레드에서 비동기 실행하는 스케줄러 공통 형태
package org.domi.furnace.api.sched;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public interface TaskScheduler {

    /** owner 스레드에서 task 실행. 완료 시 완료되는 future. */
    CompletableFuture<Void> execute(Runnable task);

    /** owner 스레드에서 task 실행 후 결과를 담은 future. */
    <T> CompletableFuture<T> submit(Supplier<T> task);

    /** delayTicks 틱 후 owner 스레드에서 task 실행. */
    CompletableFuture<Void> executeDelayed(Runnable task, long delayTicks);
}
```

- [ ] **Step 3: `GlobalScheduler.java` 작성**

```java
// 특정 region에 속하지 않는 전역 작업 스케줄러
package org.domi.furnace.api.sched;

public interface GlobalScheduler extends TaskScheduler {}
```

- [ ] **Step 4: `FurnaceScheduler.java` 작성**

```java
// 스케줄러 진입점 파사드. global / region(추후 entity) owner로 분기.
package org.domi.furnace.api.sched;

public interface FurnaceScheduler {

    /** 전역 스케줄러. */
    GlobalScheduler global();

    /**
     * 주어진 region 좌표를 소유한 스레드의 스케줄러.
     * M0에서는 모든 RegionPos가 단일 region에 매핑된다.
     */
    TaskScheduler region(RegionPos pos);
}
```

- [ ] **Step 5: 컴파일 확인**

Run: `./gradlew :furnace-api:build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: 커밋**

```bash
git add furnace-api
git commit -m "feat(api): add async scheduler interfaces (TaskScheduler/Global/Furnace)" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 4: TickTimer (드리프트 보정, 순수 단위 테스트)

**Files:**
- Create: `furnace-server/src/main/java/org/domi/furnace/server/sched/TickTimer.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/sched/TickTimerTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`TickTimerTest.java`:
```java
// 틱 타이머의 드리프트 보정 동작 테스트(주입된 가상 시간 사용, 실시간 의존 없음)
package org.domi.furnace.server.sched;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class TickTimerTest {

    private static final long T = 50_000_000L; // 50ms

    @Test
    void remaining_isFullInterval_afterStart() {
        TickTimer timer = new TickTimer(T);
        timer.start(0);
        assertEquals(T, timer.remaining(0));
    }

    @Test
    void advance_keepsBoundary_whenTickRanLate() {
        TickTimer timer = new TickTimer(T);
        timer.start(0);            // 다음 경계 = 50ms
        timer.advance(60_000_000); // 틱이 60ms에 끝남(10ms 지연) → 다음 경계 100ms
        // 드리프트 보정: 100ms 경계 유지, 60ms 시점에서 40ms 남음
        assertEquals(40_000_000L, timer.remaining(60_000_000));
    }

    @Test
    void advance_resyncs_whenFarBehind() {
        TickTimer timer = new TickTimer(T);
        timer.start(0);             // 다음 경계 = 50ms
        timer.advance(200_000_000); // 200ms에 끝남(스파이럴) → 경계 100ms 이미 지남
        // anti-spiral 재동기: 200ms 기준 한 틱 뒤
        assertEquals(T, timer.remaining(200_000_000));
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*TickTimerTest"`
Expected: 컴파일 실패 — `TickTimer` 없음.

- [ ] **Step 3: `TickTimer.java` 작성**

```java
// 고정 틱 간격을 유지하며 다음 틱 경계까지 남은 시간을 계산하는 드리프트 보정 타이머
package org.domi.furnace.server.sched;

public final class TickTimer {

    private final long tickNanos;
    private long nextTick;

    public TickTimer(long tickNanos) {
        this.tickNanos = tickNanos;
    }

    /** now 기준으로 첫 틱 경계를 한 틱 뒤로 잡는다. */
    public void start(long now) {
        nextTick = now + tickNanos;
    }

    /** now에서 다음 틱 경계까지 남은 나노초(음수면 0). 스케줄을 진행시키지 않음. */
    public long remaining(long now) {
        return Math.max(0, nextTick - now);
    }

    /**
     * 다음 틱 경계로 스케줄을 한 칸 진행. 너무 뒤처졌으면(now가 새 경계보다 뒤) 재동기해
     * 따라잡기 폭주(spiral of death)를 막는다.
     */
    public void advance(long now) {
        nextTick += tickNanos;
        if (nextTick < now) {
            nextTick = now + tickNanos;
        }
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :furnace-server:test --tests "*TickTimerTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: 커밋**

```bash
git add furnace-server
git commit -m "feat(server): add drift-correcting TickTimer" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 5: Config + ConfigLoader

**Files:**
- Create: `furnace-server/src/main/java/org/domi/furnace/server/config/Config.java`
- Create: `furnace-server/src/main/java/org/domi/furnace/server/config/ConfigLoader.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/config/ConfigLoaderTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`ConfigLoaderTest.java`:
```java
// 설정 로더의 기본값/오버라이드 동작 테스트
package org.domi.furnace.server.config;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class ConfigLoaderTest {

    @Test
    void load_returnsDefaults_whenFileMissing(@TempDir Path dir) {
        Config c = ConfigLoader.load(dir.resolve("absent.properties"));
        assertEquals("Furnace Server", c.serverName());
        assertEquals(50_000_000L, c.tickNanos());
    }

    @Test
    void load_overridesFromFile(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("furnace.properties");
        Files.writeString(f, "server-name=My Server\ntick-nanos=25000000\n");
        Config c = ConfigLoader.load(f);
        assertEquals("My Server", c.serverName());
        assertEquals(25_000_000L, c.tickNanos());
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*ConfigLoaderTest"`
Expected: 컴파일 실패 — `Config`/`ConfigLoader` 없음.

- [ ] **Step 3: `Config.java` 작성**

```java
// 서버 런타임 설정 값
package org.domi.furnace.server.config;

public record Config(String serverName, long tickNanos) {

    public static final Config DEFAULT = new Config("Furnace Server", 50_000_000L);
}
```

- [ ] **Step 4: `ConfigLoader.java` 작성**

```java
// properties 파일에서 설정을 읽되 없거나 누락된 키는 기본값으로 채우는 로더
package org.domi.furnace.server.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class ConfigLoader {

    private ConfigLoader() {}

    public static Config load(Path path) {
        if (!Files.exists(path)) {
            return Config.DEFAULT;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
        } catch (IOException e) {
            throw new RuntimeException("설정 로드 실패: " + path, e);
        }
        String name = props.getProperty("server-name", Config.DEFAULT.serverName());
        long tick = Long.parseLong(
                props.getProperty("tick-nanos", Long.toString(Config.DEFAULT.tickNanos())));
        return new Config(name, tick);
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :furnace-server:test --tests "*ConfigLoaderTest"`
Expected: PASS (2 tests).

- [ ] **Step 6: 커밋**

```bash
git add furnace-server
git commit -m "feat(server): add Config and ConfigLoader" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 6: GlobalThread (전역 단일 스레드 owner)

**Files:**
- Create: `furnace-server/src/main/java/org/domi/furnace/server/sched/GlobalThread.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/sched/GlobalThreadTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`GlobalThreadTest.java`:
```java
// 전역 owner 스레드의 실행/소유권/결과 future 동작 테스트
package org.domi.furnace.server.sched;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class GlobalThreadTest {

    @Test
    void submit_runsOnOwnerThread_andReturnsResult() throws Exception {
        try (GlobalThread g = new GlobalThread("global")) {
            Boolean onOwner = g.submit(g::isOwnerThread).get(2, TimeUnit.SECONDS);
            assertTrue(onOwner);
        }
    }

    @Test
    void callerThread_isNotOwner() throws Exception {
        try (GlobalThread g = new GlobalThread("global")) {
            assertFalse(g.isOwnerThread());
        }
    }

    @Test
    void execute_completesFuture() throws Exception {
        try (GlobalThread g = new GlobalThread("global")) {
            g.execute(() -> {}).get(2, TimeUnit.SECONDS); // 예외 없으면 통과
        }
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*GlobalThreadTest"`
Expected: 컴파일 실패 — `GlobalThread` 없음.

- [ ] **Step 3: `GlobalThread.java` 작성**

```java
// 단일 스레드에서 전역 작업을 비동기 실행하며 그 스레드의 소유권을 보유하는 스케줄러
package org.domi.furnace.server.sched;

import org.domi.furnace.api.concurrent.ThreadOwner;
import org.domi.furnace.api.sched.GlobalScheduler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class GlobalThread implements GlobalScheduler, ThreadOwner, AutoCloseable {

    private final String name;
    private final ExecutorService exec;
    private volatile Thread thread;

    public GlobalThread(String name) {
        this.name = name;
        this.exec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, name);
            this.thread = t;
            return t;
        });
        // 스레드를 미리 생성해 isOwnerThread가 즉시 신뢰 가능하도록 prime.
        try {
            exec.submit(() -> {}).get();
        } catch (Exception e) {
            throw new RuntimeException("GlobalThread prime 실패", e);
        }
    }

    @Override
    public boolean isOwnerThread() {
        return Thread.currentThread() == thread;
    }

    @Override
    public String ownerName() {
        return name;
    }

    @Override
    public CompletableFuture<Void> execute(Runnable task) {
        return CompletableFuture.runAsync(task, exec);
    }

    @Override
    public <T> CompletableFuture<T> submit(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, exec);
    }

    @Override
    public CompletableFuture<Void> executeDelayed(Runnable task, long delayTicks) {
        Executor delayed = CompletableFuture.delayedExecutor(
                delayTicks * 50, TimeUnit.MILLISECONDS, exec);
        return CompletableFuture.runAsync(task, delayed);
    }

    @Override
    public void close() {
        exec.shutdown();
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :furnace-server:test --tests "*GlobalThreadTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: 커밋**

```bash
git add furnace-server
git commit -m "feat(server): add GlobalThread single-thread owner scheduler" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 7: RegionThread (틱 루프 + 큐 드레인 owner)

**Files:**
- Create: `furnace-server/src/main/java/org/domi/furnace/server/sched/RegionThread.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/sched/RegionThreadTest.java`

RegionThread는 자기 스레드에서 `while(running)` 루프를 돈다. 매 틱: (1) 제출된 태스크 큐 드레인, (2) due가 된 지연 태스크 실행, (3) perTick 훅 실행, (4) tickCount 증가, (5) TickTimer로 다음 경계까지 sleep. 실제 MC의 서버 스레드 모델과 동일하다. (Task 6의 `GlobalThread`와 달리 region은 틱 단위로 큐를 비운다.)

- [ ] **Step 1: 실패하는 테스트 작성**

`RegionThreadTest.java`:
```java
// region owner 스레드의 틱/큐드레인/소유권 동작 테스트
package org.domi.furnace.server.sched;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RegionThreadTest {

    @Test
    void submit_runsOnOwnerThread() throws Exception {
        try (RegionThread r = new RegionThread("region-0", 50_000_000L, () -> {})) {
            Boolean onOwner = r.submit(r::isOwnerThread).get(2, TimeUnit.SECONDS);
            assertTrue(onOwner);
        }
    }

    @Test
    void perTick_runsRepeatedly() throws Exception {
        AtomicInteger ticks = new AtomicInteger();
        try (RegionThread r = new RegionThread("region-0", 50_000_000L, ticks::incrementAndGet)) {
            Thread.sleep(300); // ~6틱
            assertTrue(ticks.get() >= 3, "perTick 호출 수=" + ticks.get());
        }
    }

    @Test
    void tickCount_increases() throws Exception {
        try (RegionThread r = new RegionThread("region-0", 50_000_000L, () -> {})) {
            Thread.sleep(300);
            assertTrue(r.tickCount() >= 3, "tickCount=" + r.tickCount());
        }
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*RegionThreadTest"`
Expected: 컴파일 실패 — `RegionThread` 없음.

- [ ] **Step 3: `RegionThread.java` 작성**

```java
// 단일 스레드에서 20 TPS 틱 루프를 돌며 자기 region의 작업 큐를 매 틱 드레인하는 owner
package org.domi.furnace.server.sched;

import org.domi.furnace.api.concurrent.ThreadOwner;
import org.domi.furnace.api.sched.TaskScheduler;

import java.util.PriorityQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.concurrent.locks.LockSupport;

public final class RegionThread implements TaskScheduler, ThreadOwner, AutoCloseable {

    private final String name;
    private final long tickNanos;
    private final Runnable perTick;
    private final Thread thread;

    private final ConcurrentLinkedQueue<Runnable> inbox = new ConcurrentLinkedQueue<>();
    // delayed는 region 스레드에서만 접근(단일 스레드 소유) — 동기화 불필요.
    private final PriorityQueue<Delayed> delayed =
            new PriorityQueue<>((a, b) -> Long.compare(a.dueTick, b.dueTick));

    private final AtomicLong tickCount = new AtomicLong();
    private volatile double measuredTps;
    private volatile boolean running = true;

    public RegionThread(String name, long tickNanos, Runnable perTick) {
        this.name = name;
        this.tickNanos = tickNanos;
        this.perTick = perTick;
        this.thread = new Thread(this::loop, name);
        this.thread.start();
    }

    @Override
    public boolean isOwnerThread() {
        return Thread.currentThread() == thread;
    }

    @Override
    public String ownerName() {
        return name;
    }

    public long tickCount() {
        return tickCount.get();
    }

    public double measuredTps() {
        return measuredTps;
    }

    @Override
    public CompletableFuture<Void> execute(Runnable task) {
        CompletableFuture<Void> f = new CompletableFuture<>();
        inbox.add(() -> {
            try { task.run(); f.complete(null); }
            catch (Throwable t) { f.completeExceptionally(t); }
        });
        return f;
    }

    @Override
    public <T> CompletableFuture<T> submit(Supplier<T> task) {
        CompletableFuture<T> f = new CompletableFuture<>();
        inbox.add(() -> {
            try { f.complete(task.get()); }
            catch (Throwable t) { f.completeExceptionally(t); }
        });
        return f;
    }

    @Override
    public CompletableFuture<Void> executeDelayed(Runnable task, long delayTicks) {
        CompletableFuture<Void> f = new CompletableFuture<>();
        long due = tickCount.get() + Math.max(0, delayTicks);
        inbox.add(() -> delayed.add(new Delayed(due, task, f)));
        return f;
    }

    private void loop() {
        TickTimer timer = new TickTimer(tickNanos);
        timer.start(System.nanoTime());
        long lastSecond = System.nanoTime();
        int ticksThisSecond = 0;

        while (running) {
            long tick = tickCount.get();
            drainInbox();
            runDueDelayed(tick);
            perTick.run();
            tickCount.incrementAndGet();

            ticksThisSecond++;
            long now = System.nanoTime();
            if (now - lastSecond >= 1_000_000_000L) {
                measuredTps = ticksThisSecond * 1e9 / (now - lastSecond);
                ticksThisSecond = 0;
                lastSecond = now;
            }

            long sleep = timer.remaining(System.nanoTime());
            if (sleep > 0) {
                LockSupport.parkNanos(sleep);
            }
            timer.advance(System.nanoTime());
        }
    }

    private void drainInbox() {
        Runnable r;
        while ((r = inbox.poll()) != null) {
            r.run();
        }
    }

    private void runDueDelayed(long tick) {
        while (!delayed.isEmpty() && delayed.peek().dueTick <= tick) {
            Delayed d = delayed.poll();
            try { d.task.run(); d.future.complete(null); }
            catch (Throwable t) { d.future.completeExceptionally(t); }
        }
    }

    @Override
    public void close() {
        running = false;
        thread.interrupt();
        try { thread.join(1000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }

    private record Delayed(long dueTick, Runnable task, CompletableFuture<Void> future) {}
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :furnace-server:test --tests "*RegionThreadTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: 커밋**

```bash
git add furnace-server
git commit -m "feat(server): add RegionThread ticked queue-draining owner" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 8: RegionThread 20 TPS 검증 (타이밍 통합 테스트)

**Files:**
- Test: `furnace-server/src/test/java/org/domi/furnace/server/sched/RegionThreadTpsTest.java`

이 Task는 코드를 추가하지 않고 Task 7의 `measuredTps`가 실제로 ~20 TPS인지 실시간으로 검증한다. (M0 verify 기준 #1)

- [ ] **Step 1: 타이밍 테스트 작성**

`RegionThreadTpsTest.java`:
```java
// RegionThread가 실제로 ~20 TPS를 유지하는지 실시간 측정으로 검증
package org.domi.furnace.server.sched;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class RegionThreadTpsTest {

    @Test
    void maintainsAbout20Tps() throws Exception {
        try (RegionThread r = new RegionThread("region-0", 50_000_000L, () -> {})) {
            Thread.sleep(1500); // measuredTps가 최소 한 번 갱신되도록 1초 이상
            double tps = r.measuredTps();
            assertTrue(tps >= 17.0 && tps <= 23.0, "measuredTps=" + tps);
        }
    }
}
```

- [ ] **Step 2: 테스트 실행 (통과 기대)**

Run: `./gradlew :furnace-server:test --tests "*RegionThreadTpsTest"`
Expected: PASS. (CI 부하로 흔들리면 perTick이 빈 상태이므로 거의 항상 통과. 실패 시 환경 부하 의심 — 재실행.)

- [ ] **Step 3: 커밋**

```bash
git add furnace-server
git commit -m "test(server): verify RegionThread sustains ~20 TPS" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 9: FurnaceSchedulerImpl (파사드 구현 + 소유권 통합 검증)

**Files:**
- Create: `furnace-server/src/main/java/org/domi/furnace/server/sched/FurnaceSchedulerImpl.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/sched/FurnaceSchedulerImplTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`FurnaceSchedulerImplTest.java`:
```java
// 파사드가 global/region 스케줄러를 올바른 owner 스레드에 연결하고
// Owned 가드가 실제 스레드 경계에서 동작하는지 통합 검증
package org.domi.furnace.server.sched;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.concurrent.Owned;
import org.domi.furnace.api.sched.RegionPos;
import org.junit.jupiter.api.Test;

class FurnaceSchedulerImplTest {

    @Test
    void global_runsOnGlobalOwner() throws Exception {
        try (FurnaceSchedulerImpl s = new FurnaceSchedulerImpl(50_000_000L)) {
            Boolean onOwner = s.global().submit(
                    () -> ((GlobalThread) s.global()).isOwnerThread()).get(2, TimeUnit.SECONDS);
            assertTrue(onOwner);
        }
    }

    @Test
    void region_taskRunsOnRegionThread() throws Exception {
        try (FurnaceSchedulerImpl s = new FurnaceSchedulerImpl(50_000_000L)) {
            RegionThread owner = s.regionThread();
            Boolean onOwner = s.region(new RegionPos(0, 0))
                    .submit(owner::isOwnerThread).get(2, TimeUnit.SECONDS);
            assertTrue(onOwner);
        }
    }

    @Test
    void ownedBoundToRegion_throwsFromOtherThread_butOkInsideRegion() throws Exception {
        try (FurnaceSchedulerImpl s = new FurnaceSchedulerImpl(50_000_000L)) {
            RegionThread owner = s.regionThread();
            Owned<String> data = s.region(new RegionPos(0, 0))
                    .submit(() -> new Owned<>("blocks", owner)).get(2, TimeUnit.SECONDS);

            // region 스레드 안에서는 접근 OK
            String inside = s.region(new RegionPos(0, 0))
                    .submit(data::get).get(2, TimeUnit.SECONDS);
            assertEquals("blocks", inside);

            // 테스트(메인) 스레드에서 직접 접근 → AssertionError
            assertThrows(AssertionError.class, data::get);
        }
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :furnace-server:test --tests "*FurnaceSchedulerImplTest"`
Expected: 컴파일 실패 — `FurnaceSchedulerImpl` 없음.

- [ ] **Step 3: `FurnaceSchedulerImpl.java` 작성**

```java
// FurnaceScheduler 파사드 구현. M0는 전역 owner 1개 + region owner 1개(월드 전체).
package org.domi.furnace.server.sched;

import org.domi.furnace.api.sched.FurnaceScheduler;
import org.domi.furnace.api.sched.GlobalScheduler;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.api.sched.TaskScheduler;

public final class FurnaceSchedulerImpl implements FurnaceScheduler, AutoCloseable {

    private final GlobalThread global;
    private final RegionThread region; // M0: 모든 RegionPos가 이 하나에 매핑

    public FurnaceSchedulerImpl(long tickNanos) {
        this.global = new GlobalThread("furnace-global");
        this.region = new RegionThread("furnace-region-0", tickNanos, () -> {});
    }

    @Override
    public GlobalScheduler global() {
        return global;
    }

    @Override
    public TaskScheduler region(RegionPos pos) {
        return region; // M0: pos 무시, 단일 region
    }

    /** M0 내부/테스트용 — 단일 region 스레드 직접 접근. */
    public RegionThread regionThread() {
        return region;
    }

    @Override
    public void close() {
        region.close();
        global.close();
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :furnace-server:test --tests "*FurnaceSchedulerImplTest"`
Expected: PASS (3 tests). 마지막 테스트가 M0 verify 기준 #2(올바른 스레드 실행)와 #3(소유권 위반 검출)을 함께 증명한다.

- [ ] **Step 5: 커밋**

```bash
git add furnace-server
git commit -m "feat(server): add FurnaceSchedulerImpl facade with ownership integration" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 10: 부팅 배선 + plugin-test 모듈 + 수동 실행

**Files:**
- Create: `furnace-server/src/main/java/org/domi/furnace/server/FurnaceMain.java`
- Create: `furnace-plugin-test/src/main/java/org/domi/furnace/plugintest/SamplePlugin.java`

- [ ] **Step 1: `SamplePlugin.java` 작성 (api만으로 컴파일됨 증명)**

```java
// furnace-api만 의존해 컴파일되는 샘플. API/구현 분리 증명용(M0).
package org.domi.furnace.plugintest;

import org.domi.furnace.api.sched.FurnaceScheduler;
import org.domi.furnace.api.sched.RegionPos;

public final class SamplePlugin {

    private final FurnaceScheduler scheduler;

    public SamplePlugin(FurnaceScheduler scheduler) {
        this.scheduler = scheduler;
    }

    /** region (0,0)에서 비동기 작업 예약. 구현 클래스는 전혀 모른다. */
    public void scheduleHello() {
        scheduler.region(new RegionPos(0, 0))
                .execute(() -> System.out.println("[SamplePlugin] hello from region thread"));
    }
}
```

- [ ] **Step 2: `FurnaceMain.java` 작성**

```java
// Furnace 서버 진입점. 설정 로드 → 스케줄러 기동 → 셧다운 훅 배선.
package org.domi.furnace.server;

import org.domi.furnace.server.config.Config;
import org.domi.furnace.server.config.ConfigLoader;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

public final class FurnaceMain {

    private static final Logger log = LoggerFactory.getLogger(FurnaceMain.class);

    public static void main(String[] args) throws InterruptedException {
        Config config = ConfigLoader.load(Path.of("furnace.properties"));
        log.info("Furnace 부팅: name='{}', tickNanos={}", config.serverName(), config.tickNanos());

        FurnaceSchedulerImpl scheduler = new FurnaceSchedulerImpl(config.tickNanos());
        CountDownLatch shutdown = new CountDownLatch(1);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("종료 중...");
            scheduler.close();
            shutdown.countDown();
        }, "furnace-shutdown"));

        // TPS 하트비트: 5초마다 측정 TPS 로그(M0 가시성).
        scheduler.global().executeDelayed(new TpsHeartbeat(scheduler), 100);

        log.info("Furnace 기동 완료. Ctrl+C로 종료.");
        shutdown.await();
    }

    /** 5초(100틱)마다 region TPS를 로그하고 자신을 재예약하는 하트비트. */
    private record TpsHeartbeat(FurnaceSchedulerImpl scheduler) implements Runnable {
        @Override
        public void run() {
            log.info("region TPS = {}", String.format("%.1f", scheduler.regionThread().measuredTps()));
            scheduler.global().executeDelayed(this, 100);
        }
    }
}
```

- [ ] **Step 3: 전체 빌드 + 테스트**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL. 모든 모듈 컴파일 + 전체 테스트 통과(api 3 + server: TickTimer 3, ConfigLoader 2, GlobalThread 3, RegionThread 3, RegionThreadTps 1, FurnaceSchedulerImpl 3).

- [ ] **Step 4: 수동 실행 검증 (M0 최종 verify)**

Run: `./gradlew :furnace-server:run`
Expected (수 초 관찰 후 Ctrl+C):
- `Furnace 부팅: name='Furnace Server', tickNanos=50000000`
- `Furnace 기동 완료. Ctrl+C로 종료.`
- 5초 후 `region TPS = 20.0` (대략, 19.x~20.x)
- Ctrl+C 시 `종료 중...` 출력 후 정상 종료

- [ ] **Step 5: 커밋**

```bash
git add furnace-server furnace-plugin-test
git commit -m "feat(server): wire FurnaceMain bootstrap and sample plugin" -m "Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## M0 완료 기준 (Definition of Done)

설계서 M0 verify 항목 매핑:
- ✅ **20 TPS 유지** → Task 8 `RegionThreadTpsTest` + Task 10 Step 4 수동 로그
- ✅ **태스크가 지정 스레드서 실행(스레드 id 단언)** → Task 6/7/9 `isOwnerThread` 단언
- ✅ **소유권 위반 테스트가 실제로 터짐** → Task 9 `ownedBoundToRegion_throwsFromOtherThread...`
- ✅ **API/구현 분리** → Task 10 `SamplePlugin`이 `furnace-api`만 의존해 컴파일
- ✅ **비동기 시그니처 고정** → 모든 스케줄 연산 `CompletableFuture` 반환(Task 3 계약)

## Self-Review 결과

- **Spec 커버리지:** M0 항목(멀티모듈·틱루프·스케줄러 3종 표면·소유권 프리미티브·설정·로깅) 전부 Task에 매핑됨. EntityScheduler는 설계서 4.4 정련대로 공통 `TaskScheduler` 형태로 흡수(별도 인터페이스 미생성) — 의도적.
- **Placeholder:** 없음. 모든 코드 단계에 실제 코드 포함.
- **타입 일관성:** `TaskScheduler`(execute/submit/executeDelayed), `ThreadOwner`(isOwnerThread/ownerName), `RegionPos(x,z)`, `Config(serverName,tickNanos)`, `RegionThread.measuredTps()/tickCount()` 가 Task 전반에서 동일하게 사용됨. `GlobalScheduler extends TaskScheduler` → `GlobalThread`가 구현. `FurnaceScheduler.region()` 반환은 `TaskScheduler`로 일관.
