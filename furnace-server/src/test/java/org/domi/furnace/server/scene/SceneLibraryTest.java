// 장면 라이브러리의 증명 모드: 장면마다 금 파일(identity/scene-<이름>.txt)과 비교한다. 비교 전에 결정성 자기 점검(IdentityKit)을 한다.
// 금 파일이 바뀌면 결과가 바뀐 것이다 — docs/testing/identitykit.md "금 파일 갱신".
package org.domi.furnace.server.scene;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.domi.furnace.server.NoRetry;
import org.domi.furnace.server.identity.Golden;
import org.domi.furnace.server.identity.SceneHash;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@NoRetry   // 결정적 비교라 재시도해도 결과가 같다 — 비결정 실패를 재시도로 가리지 않는다
class SceneLibraryTest {

    /** 이름표를 붙여 시험 이름이 장면 이름("fluid" 등)이 되게 한다(객체 기본 toString 은 정체 해시라 실행마다 달라진다). */
    static Stream<Named<LibraryScene>> scenes() { return SceneLibrary.all().stream().map(s -> Named.of(s.name(), s)); }

    @Test
    void namesAreUniqueAndValid() {
        Set<String> names = new HashSet<>(), goldens = new HashSet<>();
        for (LibraryScene s : SceneLibrary.all()) {
            assertTrue(names.add(s.name()), "장면 이름이 겹친다: " + s.name());
            assertTrue(goldens.add(Golden.checkName(s.golden())), "금 파일 이름이 겹친다: " + s.golden());
            assertTrue(s.name().matches("[a-z0-9][a-z0-9-]*"), "장면 이름은 소문자·숫자·'-': " + s.name());
            assertFalse(s.covers().isBlank(), s.name() + ": covers 를 적는다");
            assertNotNull(Golden.expected(s.golden()), s.name() + ": 금 파일 identity/" + s.golden() + ".txt 가 없다 — -Dfurnace.identity.bless=" + s.golden() + " 로 만든다");
        }
        assertFalse(names.isEmpty());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenes")
    void proofMatchesGolden(LibraryScene scene) {
        if (scene.freshJvm()) SceneHash.assertGoldenInFreshJvm(scene.golden(), scene.getClass());
        else SceneHash.assertGolden(scene.golden(), scene);
    }
}
