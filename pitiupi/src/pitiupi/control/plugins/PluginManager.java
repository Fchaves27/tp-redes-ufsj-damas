package pitiupi.control.plugins;

import pitiupi.Main;
import pitiupi.control.ConnectionManager;
import pitiupi.control.GuiManager;
import pitiupi.models.Messages.Message;
import pitiupi.net.tcp.TcpConnection;
import pitiupi.models.Plugin;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PluginManager {
    private final List<LoadedPlugin> loadedPlugins;
    private final HashSet<String> loadedJars;

    private final Logger logger;

    public PluginManager() {
        this.loadedPlugins = new ArrayList<>();
        this.loadedJars = new HashSet<>();

        logger = Logger.getLogger(getClass().getName());
    }

    public void handleMessage(byte[] msg) {
        Message message = null;

        for (LoadedPlugin loadedPlugin : loadedPlugins) {
            message = deserialize(msg, loadedPlugin.getClassLoader());
            if (message != null) break;
        }
        if (message == null) return;

        for (LoadedPlugin loadedPlugin : loadedPlugins) {
            Plugin plugin = loadedPlugin.getPlugin();

            if (plugin.receiveAllTypes() && message.isPublic()) {
                plugin.receiveMessage(message);
                continue;
            }

            for (Class<? extends Message> messageType : plugin.getMessages()) {
                if (messageType.isInstance(message)) {
                    plugin.receiveMessage(message);
                    break;
                }
            }
        }
    }

    private Plugin getPlugin(String pluginId) {
        for (LoadedPlugin loadedPlugin : loadedPlugins) {
            if (loadedPlugin.getPlugin().getIdentifier().equals(pluginId)) {
                return loadedPlugin.getPlugin();
            }
        }
        return null;
    }

    public void handleMessageFromConnection(TcpConnection connection, byte[] msg) {
        Plugin plugin = getPlugin(connection.getReceiver());
        if (plugin == null) return;

        plugin.receiveBytes(connection, msg);
    }

    public boolean handleConnection(TcpConnection connection, String receiverId) {
        Plugin plugin = getPlugin(receiverId);
        if (plugin == null) return false;

        plugin.receivePrivateConnection(connection);

        return true;
    }

    public void handleDisconnection(TcpConnection connection, String receiverId) {
        Plugin plugin = getPlugin(receiverId);
        if (plugin == null) return;

        plugin.onSocketClosed(connection);
    }

    public void loadPlugins(ConnectionManager connectionManager, GuiManager guiManager) {
        File pluginDir = new File("plugins");
        File[] jarFiles = pluginDir.listFiles((dir, name) -> name.endsWith(".jar"));

        if (jarFiles == null || jarFiles.length == 0) {
            System.out.println("No plugins found in the " + pluginDir.getAbsolutePath() + " directory.");
            return;
        }

        for (File file : jarFiles) {

            if (loadedJars.contains(file.getName())) {
                continue;
            }

            System.out.println("Discovered plugin package: " + file.getName());

            try {
                URLClassLoader classLoader = new URLClassLoader(
                        new URL[]{file.toURI().toURL()},
                        Main.class.getClassLoader()
                );

                ServiceLoader<Plugin> serviceLoader = ServiceLoader.load(Plugin.class, classLoader);

                for (Plugin plugin : serviceLoader) {
                    LoadedPlugin loadedPlugin = new LoadedPlugin(plugin, classLoader);

                    plugin.createPlugin(connectionManager, guiManager);
                    loadedPlugins.add(loadedPlugin);
                    loadedJars.add(file.getName());
                }

            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to load plugin " + file.getName(), e);
            }
        }
    }

    private Message deserialize(byte[] data, ClassLoader classLoader) {
        try {
            ByteArrayInputStream input = new ByteArrayInputStream(data);

            PluginObjectInputStream objectInput = new PluginObjectInputStream(input, classLoader);

            Object object = objectInput.readObject();

            if (!(object instanceof Message)) {
                return null;
            }

            return (Message) object;
        }
        catch (ClassNotFoundException e) {
            return null;
        }
        catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to load plugin package", e);
            return null;
        }
    }
}
