// Bukkit BukkitObjectInputStream — 서명·예외 문구만 Bukkit 에 맞추고 몸통은 Furnace 가 새로 썼다.
package org.bukkit.util.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import org.bukkit.configuration.serialization.ConfigurationSerialization;

/** {@link BukkitObjectOutputStream} 이 쓴 껍데기를 원래 ConfigurationSerializable 로 되돌려 읽는 객체 입력 스트림. */
public class BukkitObjectInputStream extends ObjectInputStream {

    protected BukkitObjectInputStream() throws IOException, SecurityException {
        super();
        enableResolveObject(true);
    }

    public BukkitObjectInputStream(InputStream in) throws IOException {
        super(in);
        enableResolveObject(true);
    }

    /** 껍데기는 그 맵으로 되읽는다. 되읽지 못하면(별칭 모름·결과 null 포함) "Failed to deserialize object" IOException. */
    @Override
    protected Object resolveObject(Object obj) throws IOException {
        if (!(obj instanceof Wrapper<?> wrapper)) return super.resolveObject(obj);
        Object restored;
        try {
            restored = ConfigurationSerialization.deserializeObject(wrapper.map);
        } catch (Throwable t) {
            throw new IOException("Failed to deserialize object", t);
        }
        if (restored == null) throw new IOException("Failed to deserialize object", new NullPointerException());
        return super.resolveObject(restored);
    }
}
