package pitiupi.control.plugins;

import pitiupi.models.Plugin;

class LoadedPlugin {

    private final Plugin plugin;
    private final ClassLoader classLoader;

    public LoadedPlugin(Plugin plugin, ClassLoader classLoader) {
        this.plugin = plugin;
        this.classLoader = classLoader;
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }
}
