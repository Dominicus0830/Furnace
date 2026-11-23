package org.domi.furnace.compat.shimgen;

import java.util.ArrayList;
import java.util.List;

/** ShimGeneratorTest 의 생성 shim 이 부르는 대상(핵심 층 대역)과 소유 규칙 대역. */
public final class ShimGenFixtures {
    private ShimGenFixtures() {}

    public static volatile double x = 1.5;
    public static final List<String> calls = new ArrayList<>();
    /** 주인 스레드(대역). null 이면 아무 스레드나 주인이다. */
    public static volatile Thread owner;

    public static double x(Object self) { calls.add("x"); return x; }

    public static void setX(Object self, double v) { calls.add("setX"); x = v; }

    public static int peek(Object self) { calls.add("peek"); return 42; }

    public static int twice(int v) { return v * 2; }

    /** 상위 공통 연결(@each) 대상: 받는 객체의 클래스 이름. */
    public static String name(Object self) { calls.add("name"); return "named:" + self.getClass().getSimpleName(); }

    /** 소유 규칙 대역: 읽기·쓰기 검사. 주인이 아닌 스레드면 던진다(다른 region 스레드에서 쓰기는 던진다). */
    public static final class Owner {
        private Owner() {}

        public static void read(Object self, String what) { check("read " + what); }

        public static void write(Object self, String what) { check("write " + what); }

        private static void check(String what) {
            Thread o = owner;
            if (o != null && o != Thread.currentThread()) throw new IllegalStateException("not the owner thread: " + what);
            calls.add(what);
        }
    }
}
