package pitiupi.control.plugins;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;

public class PluginObjectInputStream extends ObjectInputStream {
    private final ClassLoader classLoader;

    public PluginObjectInputStream(InputStream input, ClassLoader classLoader) throws IOException {
        super(input);
        this.classLoader = classLoader;
    }

    @Override
    protected Class<?> resolveClass(ObjectStreamClass description) throws ClassNotFoundException {
        return Class.forName(description.getName(), false, classLoader);
    }
}