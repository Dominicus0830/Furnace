// 호환 기준 장면의 증명 모드: 장면마다 금 파일(identity/scene-compat-*.txt)과 비교한다(결정성 자기 점검 포함).
// 금 파일이 바뀌면 어댑터가 읽는 값이 바뀐 것이다 — 최적화 PR 은 금 파일을 고치지 않는다(docs/testing/compat-scenes.md).
package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import org.domi.furnace.server.identity.Golden;
import org.domi.furnace.server.identity.SceneHash;
import org.domi.furnace.server.scene.LibraryScene;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class CompatSceneLibraryTest {

    static Stream<Named<LibraryScene>> scenes() { return CompatSceneLibrary.all().stream().map(s -> Named.of(s.name(), s)); }

    @Test
    void namesAreUniqueAndGoldenFilesExist() {
        Set<String> names = new HashSet<>(), goldens = new HashSet<>();
        for (LibraryScene s : CompatSceneLibrary.all()) {
            assertTrue(names.add(s.name()), "장면 이름이 겹친다: " + s.name());
            assertTrue(goldens.add(Golden.checkName(s.golden())), "금 파일 이름이 겹친다: " + s.golden());
            assertTrue(s.name().matches("compat-[a-z0-9][a-z0-9-]*"), "호환 장면 이름은 compat- 로 시작: " + s.name());
            assertFalse(s.covers().isBlank(), s.name() + ": covers 를 적는다");
            assertNotNull(Golden.expected(s.golden()), s.name() + ": 금 파일 identity/" + s.golden() + ".txt 가 없다 — -Dfurnace.identity.bless=" + s.golden() + " 로 만든다");
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenes")
    void proofMatchesGolden(LibraryScene scene) {
        if (scene.freshJvm()) SceneHash.assertGoldenInFreshJvm(scene.golden(), scene.getClass());
        else SceneHash.assertGolden(scene.golden(), scene);
    }
}
