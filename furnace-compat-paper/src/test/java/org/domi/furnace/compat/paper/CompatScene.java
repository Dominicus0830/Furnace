// 호환 기준 장면(T-075)의 공통 뼈대. 한 장면 = 증명(PROOF, 금 파일 비교)·벤치(FULL, 시간 측정) 두 모드, 같은 코드(docs/testing/compat-scenes.md).
// 서버 쪽 장면 라이브러리(LibraryScene·Size·Probe·SceneHash·Golden)를 그대로 쓴다. 장면은 실제 헤드리스 서버(FurnaceTestServer)를 띄워
// Bukkit 어댑터를 부르므로 엔티티 id 카운터·정적 난수가 JVM 전역이다 → 기본은 새 JVM 모드(freshJvm = true).
// 금 파일 이름은 scene-compat-*(furnace-compat-paper/src/test/resources/identity/). 줄에는 정체 해시·엔티티 번호·UUID·시각을 넣지 않는다.
package org.domi.furnace.compat.paper;

import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.domi.furnace.server.identity.IdentityHash;
import org.domi.furnace.server.scene.LibraryScene;
import org.domi.furnace.test.FurnaceTestServer;

abstract class CompatScene implements LibraryScene {

    @Override
    public boolean freshJvm() { return true; }

    /** 서버 하나(플러그인 없음). Bukkit 정적 서버를 어댑터에 건다(시험 서버는 Bukkit.setServer 를 안 한다). */
    static FurnaceTestServer server() {
        FurnaceTestServer s = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(org.bukkit.craftbukkit.CraftServer.INSTANCE);
        return s;
    }

    /** 읽기 결과를 문자열로. 던지면 "!예외클래스" — 예외 종류도 결과의 일부라 해시에 들어간다(메시지는 뺀다: 문구 고침이 결과 변경이 아니다). */
    static String safe(Supplier<?> read) {
        try {
            return String.valueOf(read.get());
        } catch (RuntimeException | LinkageError e) {
            return "!" + e.getClass().getSimpleName();
        }
    }

    /** 실수 좌표를 비트가 아닌 글자로(금 파일을 사람이 읽을 수 있게) — 같은 값이면 같은 글자. */
    static String d(double v) { return Double.toString(v); }

    static IdentityHash hash() { return new IdentityHash(); }
}
