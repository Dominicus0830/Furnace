package net.minecraft.world.level;

/**
 * 바닐라 CommonLevelAccessor(Mojang 이름): 레벨 읽기에 레지스트리 묶음을 더한 공통 입구. 바닐라는 엔티티·시뮬레이션 읽기쓰기 인터페이스도
 * 잇지만 shim 은 {@link LevelReader}(레지스트리 묶음 {@code registryAccess()} 가 거기 있다)까지만 잇는다. {@link LevelAccessor} 가 이것을 잇는다.
 */
public interface CommonLevelAccessor extends LevelReader {}
