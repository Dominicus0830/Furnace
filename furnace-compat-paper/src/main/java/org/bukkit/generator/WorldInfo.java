package org.bukkit.generator;

import java.util.UUID;
import org.bukkit.World;

/** Paper 1.21.1 WorldInfo(월드 생성 중에도 쓸 수 있는 월드 정보). World 가 이것을 넓힌다. */
public interface WorldInfo {

    String getName();

    UUID getUID();

    World.Environment getEnvironment();

    long getSeed();

    int getMinHeight();

    int getMaxHeight();
}
