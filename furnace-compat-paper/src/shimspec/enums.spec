# 생성자 인자가 있는 열거형(T-230): 상수 줄을 `이름 = (인자)` 로 적는다. 생성자·필드·메서드는 src/shims/java 조각이 쓴다. 상수는 전부 바닐라 순서로.
class net.minecraft.network.protocol.PacketFlow
  SERVERBOUND = ("serverbound")
  CLIENTBOUND = ("clientbound")

# 직소 블록 엔티티: 바깥은 src/shims/java 조각, 안쪽 열거형 JointType 은 조각 안의 손 열거형(바닐라처럼 StringRepresentable 구현·이름 값 보유)
class net.minecraft.world.level.block.entity.JigsawBlockEntity
