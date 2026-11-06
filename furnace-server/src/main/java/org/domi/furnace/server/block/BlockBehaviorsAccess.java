// types 패키지가 동작을 등록하는 통로.
package org.domi.furnace.server.block;

import java.util.function.IntFunction;

public final class BlockBehaviorsAccess {
    private BlockBehaviorsAccess() {}

    public static void register(String type, IntFunction<BlockBehavior> f) { BlockBehaviors.register(type, f); }
}
