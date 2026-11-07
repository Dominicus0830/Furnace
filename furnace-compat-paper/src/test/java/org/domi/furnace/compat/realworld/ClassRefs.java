package org.domi.furnace.compat.realworld;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 클래스 파일 하나가 밖에 거는 참조(클래스·필드·메서드)를 상수 풀에서 읽는다.
 *
 * <p>실제 Bukkit·Paper API 로 컴파일된 jar 가 "이 서버에 무엇이 있어야 도는지"를 바이트코드 그대로 알려 주므로,
 * Bukkit 원문 없이도 호환 계층의 빈틈을 잴 수 있다(H4). 외부 라이브러리 없이 상수 풀만 읽는다.
 */
public final class ClassRefs {

    /** 멤버 참조 한 건. {@code owner} 는 내부 이름(a/b/C). */
    public record Member(String owner, String name, String descriptor, boolean field) {
        @Override
        public String toString() {
            return owner.replace('/', '.') + "#" + name + (field ? "" : descriptor);
        }
    }

    public final String name;
    public final String superName;
    public final List<String> interfaces = new ArrayList<>();
    /** 상수 풀의 클래스 참조(자기 자신 포함, 배열은 원소 형식). */
    public final Set<String> classes = new LinkedHashSet<>();
    public final Set<Member> members = new LinkedHashSet<>();
    /** 상수 풀의 문자열 리터럴(H5: 리플렉션으로 NMS·CraftBukkit 이름을 문자열로 만드는 코드를 찾는다). */
    public final Set<String> strings = new LinkedHashSet<>();

    private ClassRefs(String name, String superName) {
        this.name = name;
        this.superName = superName;
    }

    public static ClassRefs parse(byte[] bytes) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        if (in.readInt() != 0xCAFEBABE) throw new IOException("클래스 파일이 아니다");
        in.readUnsignedShort();
        in.readUnsignedShort();
        int count = in.readUnsignedShort();
        int[] tag = new int[count];
        String[] utf = new String[count];
        int[] a = new int[count];
        int[] b = new int[count];
        for (int i = 1; i < count; i++) {
            int t = in.readUnsignedByte();
            tag[i] = t;
            switch (t) {
                case 1 -> utf[i] = in.readUTF();
                case 3, 4 -> in.readInt();
                case 5, 6 -> {
                    in.readLong();
                    i++;
                }
                case 7, 8, 16, 19, 20 -> a[i] = in.readUnsignedShort();
                case 9, 10, 11, 12, 17, 18 -> {
                    a[i] = in.readUnsignedShort();
                    b[i] = in.readUnsignedShort();
                }
                case 15 -> {
                    in.readUnsignedByte();
                    in.readUnsignedShort();
                }
                default -> throw new IOException("알 수 없는 상수 태그 " + t);
            }
        }
        in.readUnsignedShort(); // access
        int thisIdx = in.readUnsignedShort();
        int superIdx = in.readUnsignedShort();
        String self = utf[a[thisIdx]];
        ClassRefs r = new ClassRefs(self, superIdx == 0 ? null : utf[a[superIdx]]);
        int ifaceCount = in.readUnsignedShort();
        for (int i = 0; i < ifaceCount; i++) r.interfaces.add(utf[a[in.readUnsignedShort()]]);
        for (int i = 1; i < count; i++) {
            switch (tag[i]) {
                case 7 -> r.addClass(utf[a[i]]);
                case 8 -> r.strings.add(utf[a[i]]);
                case 9, 10, 11 -> {
                    String owner = utf[a[a[i]]];
                    int nt = b[i];
                    r.addClass(owner);
                    String name = utf[a[nt]];
                    String desc = utf[b[nt]];
                    if (owner.startsWith("[")) continue; // 배열 메서드(clone 등)
                    r.members.add(new Member(owner, name, desc, tag[i] == 9));
                }
                default -> {
                }
            }
        }
        return r;
    }

    private void addClass(String internal) {
        String n = internal;
        while (n.startsWith("[")) n = n.substring(1);
        if (n.startsWith("L") && n.endsWith(";")) n = n.substring(1, n.length() - 1);
        else if (n.length() == 1) return; // 기본형 배열
        classes.add(n);
    }
}
