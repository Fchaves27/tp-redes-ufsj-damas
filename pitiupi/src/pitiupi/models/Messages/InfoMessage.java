package pitiupi.models.Messages;

import pitiupi.net.tcp.TcpConnection;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

public class InfoMessage extends Message {
    private final InfoType type;
    private final String message;

    public InfoMessage(String message, InfoType type) {
        this.message = message;
        this.type = type;
    }

    public String getMessage() {
        return message;
    }

    public InfoType getType() {
        return type;
    }

    public static void sendSystemInfo(TcpConnection connection, InfoType type, String description) {
        try {
            InfoMessage error = new InfoMessage(description, type);
            connection.send(error.toByteArray());
        } catch (IOException ex) {
            Logger.getLogger(InfoMessage.class.getName()).log(Level.WARNING, null, ex);
        }
    }

    public static InfoMessage parseSystemInfo(byte[] bytes) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             ObjectInputStream ois = new ObjectInputStream(bis)) {

            Object object = ois.readObject();
            if (object instanceof InfoMessage) {
                return (InfoMessage) object;
            }
        } catch (IOException | ClassNotFoundException ex) {
            Logger.getLogger(InfoMessage.class.getName()).log(Level.WARNING, null, ex);
        }
        return null;
    }
}
