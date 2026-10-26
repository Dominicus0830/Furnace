// /locate structure: 이름 해석(id·태그·없는 것)과 거리 계산(바닐라 int 곱·float 제곱근·내림).
package org.domi.furnace.server.command.builtin;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.domi.furnace.server.command.arguments.ResourceOrTagKeyArgument;
import org.domi.furnace.server.command.brigadier.StringReader;
import org.domi.furnace.server.data.ResourceId;
import org.junit.jupiter.api.Test;

class LocateCommandTest {

    @Test
    void resolve() throws Exception {
        var arg = ResourceOrTagKeyArgument.resourceOrTagKey("minecraft:worldgen/structure");
        var id = arg.parse(new StringReader("swamp_hut"));
        assertFalse(id.tag());
        assertEquals(List.of("minecraft:swamp_hut"), LocateCommand.resolve(id));
        var tag = arg.parse(new StringReader("#minecraft:mineshaft"));
        assertTrue(tag.tag());
        assertEquals("#minecraft:mineshaft", tag.printable());
        assertEquals(List.of("minecraft:mineshaft", "minecraft:mineshaft_mesa"), LocateCommand.resolve(tag));
        assertNull(LocateCommand.resolve(new ResourceOrTagKeyArgument.Result(ResourceId.parse("minecraft:nope"), false)));
        assertNull(LocateCommand.resolve(new ResourceOrTagKeyArgument.Result(ResourceId.parse("minecraft:nope"), true)));
    }

    @Test
    void distance() {
        assertEquals(5, LocateCommand.dist(0, 0, 3, 4));
        assertEquals(1414, LocateCommand.dist(0, 0, 1000, -1000));
        assertEquals(0, LocateCommand.dist(7, 7, 7, 7));
    }
}
