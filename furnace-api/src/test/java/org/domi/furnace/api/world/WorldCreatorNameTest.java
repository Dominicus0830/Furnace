// WorldCreator.isValidName: 생성자의 이름 검사와 같은 판정이어야 한다.
package org.domi.furnace.api.world;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WorldCreatorNameTest {

    @Test
    void validNamesAreAccepted() {
        for (String n : new String[] {"world", "my_world-2", "a.b", "A", "x".repeat(64), "...x"}) {
            assertTrue(WorldCreator.isValidName(n), n);
            assertDoesNotThrow(() -> WorldCreator.named(n).build(), n);
        }
    }

    @Test
    void invalidNamesAreRejectedAndMatchTheConstructor() {
        for (String n : new String[] {"", ".", "..", "x".repeat(65), "bad name", "월드", "a/b", "a\\b"}) {
            assertFalse(WorldCreator.isValidName(n), n);
            assertThrows(IllegalArgumentException.class, () -> WorldCreator.named(n).build(), n);
        }
        assertFalse(WorldCreator.isValidName(null));
    }
}
