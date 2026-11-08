package org.bukkit.util.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import org.bukkit.configuration.serialization.ConfigurationSerialization;

/** {@link BukkitObjectOutputStream} 이 쓴 껍데기를 원래 ConfigurationSerializable 로 되돌려 읽는 객체 입력 스트림. */
public class BukkitObjectInputStream extends ObjectInputStream {

    protected BukkitObjectInputStream() throws IOException, SecurityException {
        super();
        super.enableResolveObject(true);
    }

    public BukkitObjectInputStream(InputStream in) throws IOException {
        super(in);
        super.enableResolveObject(true);
    }

    @Override
    protected Object resolveObject(Object obj) throws IOException {
        if (obj instanceof Wrapper) {
            try {
                // 되읽지 못해 null 이면 NPE 로 떨어져 IOException 이 된다(Bukkit 과 같다)
                (obj = ConfigurationSerialization.deserializeObject(((Wrapper<?>) obj).map)).getClass();
            } catch (Throwable ex) {
                IOException exception = new IOException("Failed to deserialize object");
                exception.initCause(ex);
                throw exception;
            }
        }
        return super.resolveObject(obj);
    }
}
