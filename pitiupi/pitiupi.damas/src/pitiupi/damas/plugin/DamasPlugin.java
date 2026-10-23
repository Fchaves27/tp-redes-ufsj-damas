package pitiupi.damas.plugin;

import pitiupi.control.ConnectionManager;
import pitiupi.control.GuiManager;
import pitiupi.models.Menus.MenuCategory;
import pitiupi.models.Menus.MulticastMenus.MulticastActionMenu;
import pitiupi.models.Messages.Message;
import pitiupi.models.Plugin;
import pitiupi.damas.net.DamasMessage;

import javax.swing.JOptionPane;
import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DamasPlugin extends Plugin {
    private static final Logger LOGGER = Logger.getLogger(DamasPlugin.class.getName());
    private static final int WEB_PORT = 8765;

    private final String nodeId = UUID.randomUUID().toString();
    private final ConcurrentMap<String, RoomRole> rooms = new ConcurrentHashMap<>();

    private ConnectionManager connectionManager;
    private DamasBridgeServer bridge;

    @Override
    public void createPlugin(ConnectionManager connectionManager, GuiManager guiManager) {
        this.connectionManager = connectionManager;
        bridge = new DamasBridgeServer(WEB_PORT, this::handleBrowserCommand);
        try {
            bridge.start();
        } catch (IOException exception) {
            throw new IllegalStateException("Não foi possível iniciar o servidor local de damas.", exception);
        }

        guiManager.createMenu(MenuCategory.Jogos, new MulticastActionMenu() {
            @Override
            public String getText() {
                return "Abrir jogo de damas";
            }

            @Override
            public void onClick() {
                openGame();
            }
        });
    }

    private void openGame() {
        try {
            URI page = URI.create("http://127.0.0.1:" + WEB_PORT + "/");
            if (!Desktop.isDesktopSupported()) {
                throw new IllegalStateException("Este ambiente não permite abrir o navegador automaticamente.");
            }
            Desktop.getDesktop().browse(page);
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "Não foi possível abrir o jogo de damas.", exception);
            JOptionPane.showMessageDialog(
                    null,
                    "Não foi possível abrir o jogo. A página local deveria estar em http://127.0.0.1:"
                            + WEB_PORT + "/",
                    "Jogo de damas",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void handleBrowserCommand(String command) {
        String[] fields = command.split("\\|", -1);
        if (fields.length < 2) {
            sendToBrowser("ERROR|Comando inválido.");
            return;
        }

        String type = fields[0];
        String room = fields[1].toUpperCase();
        if (!room.matches("[A-Z0-9]{6,12}")) {
            sendToBrowser("ERROR|Código de sala inválido.");
            return;
        }

        switch (type) {
            case "CREATE" -> createRoom(room);
            case "JOIN" -> joinRoom(room);
            case "MOVE" -> sendMove(room, fields);
            case "RESET" -> resetRoom(room);
            default -> sendToBrowser("ERROR|Comando desconhecido.");
        }
    }

    private void createRoom(String room) {
        if (rooms.putIfAbsent(room, RoomRole.HOST) != null) {
            sendToBrowser("ERROR|Já existe uma partida ativa com esse código.");
            return;
        }
        if (!sendNetwork("INVITE", room, "")) {
            rooms.remove(room, RoomRole.HOST);
            return;
        }
        sendToBrowser("STATUS|Sala " + room + " criada. Envie o código ao outro jogador.");
    }

    private void joinRoom(String room) {
        if (rooms.putIfAbsent(room, RoomRole.GUEST) != null) {
            sendToBrowser("ERROR|Você já está usando esse código de sala.");
            return;
        }
        if (!sendNetwork("JOIN", room, "")) {
            rooms.remove(room, RoomRole.GUEST);
            return;
        }
        sendToBrowser("STATUS|Pedido enviado. Aguardando resposta do outro jogador.");
    }

    private void sendMove(String room, String[] fields) {
        if (rooms.containsKey(room) && fields.length == 4
                && fields[2].matches("[0-7],[0-7]")
                && fields[3].matches("[0-7],[0-7](;[0-7],[0-7])*")) {
            sendNetwork("MOVE", room, fields[2] + "|" + fields[3]);
        }
    }

    private void resetRoom(String room) {
        if (rooms.containsKey(room) && sendNetwork("RESET", room, "")) {
            sendToBrowser("RESET|" + room);
        }
    }

    private boolean sendNetwork(String type, String room, String payload) {
        DamasMessage message = new DamasMessage(
                nodeId,
                safeName(connectionManager.getUserName()),
                type,
                room,
                payload
        );
        boolean sent = connectionManager.sendUdpMulticast(message);
        if (!sent) {
            sendToBrowser("ERROR|Falha ao enviar pela rede Pitiupi.");
        }
        return sent;
    }

    private void sendToBrowser(String event) {
        if (bridge != null) {
            bridge.send(event);
        }
    }

    private static String safeName(String value) {
        if (value == null || value.isBlank()) {
            return "Jogador";
        }
        return value.replace('|', ' ').replace('\r', ' ').replace('\n', ' ');
    }

    @Override
    public List<Class<? extends Message>> getMessages() {
        return List.of(DamasMessage.class);
    }

    @Override
    public void receiveMessage(Message message) {
        if (!(message instanceof DamasMessage gameMessage)
                || nodeId.equals(gameMessage.getSenderId())
                || gameMessage.getType() == null
                || gameMessage.getRoom() == null
                || !gameMessage.getRoom().matches("[A-Z0-9]{6,12}")) {
            return;
        }

        String room = gameMessage.getRoom();
        switch (gameMessage.getType()) {
            case "INVITE" -> sendToBrowser("INVITE|" + room + "|" + safeName(gameMessage.getSenderName()));
            case "JOIN" -> acceptJoin(room, gameMessage);
            case "START" -> startAsGuest(room, gameMessage);
            case "MOVE", "RESET" -> forwardActiveRoomEvent(room, gameMessage);
            default -> {
            }
        }
    }

    private void acceptJoin(String room, DamasMessage gameMessage) {
        if (rooms.get(room) != RoomRole.HOST) {
            return;
        }
        if (sendNetwork("START", room, "")) {
            sendToBrowser("START|" + room + "|w|" + safeName(gameMessage.getSenderName()));
        }
    }

    private void startAsGuest(String room, DamasMessage gameMessage) {
        if (rooms.get(room) == RoomRole.GUEST) {
            sendToBrowser("START|" + room + "|b|" + safeName(gameMessage.getSenderName()));
        }
    }

    private void forwardActiveRoomEvent(String room, DamasMessage gameMessage) {
        if (!rooms.containsKey(room)) {
            return;
        }
        if ("MOVE".equals(gameMessage.getType())) {
            String payload = gameMessage.getPayload();
            if (payload != null && payload.matches("[0-7],[0-7]\\|[0-7],[0-7](;[0-7],[0-7])*")) {
                sendToBrowser("MOVE|" + room + "|" + payload);
            }
        } else {
            sendToBrowser("RESET|" + room);
        }
    }

    @Override
    public String getName() {
        return "Damas online";
    }

    @Override
    public String getAuthor() {
        return "Projeto TP Redes UFSJ";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public String getIdentifier() {
        return "Pitiupi.Damas.Online";
    }

    private enum RoomRole {
        HOST,
        GUEST
    }
}
