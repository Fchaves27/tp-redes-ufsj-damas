package pitiupi.net.tcp;

import pitiupi.models.Messages.InfoType;
import pitiupi.models.User;
import pitiupi.control.PeerListener;
import pitiupi.models.Messages.InfoMessage;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 *  Gerencia as conexões TCP estabelecidas com os peers da aplicação.
 *
 *  <p>As conexões públicas são armazenadas individualmente por endereço IP,
 *  enquanto as conexões privadas são agrupadas por endereço, permitindo que
 *  um mesmo peer mantenha múltiplas conexões privadas simultaneamente.</p>
 *
 *  <p>Além de manter o registro das conexões ativas, esta classe é responsável
 *  por encerrá-las quando necessário e por remover do gerenciamento as
 *  conexões que forem fechadas. Também acompanha as alterações na lista de
 *  peers ativos para encerrar conexões associadas a peers que deixaram de
 *  estar disponíveis.</p>
 */
class TcpConnectionManager implements PeerListener {
    private final Logger logger;
    private final Map<InetAddress, TcpConnection> publicConnections;
    private final Map<InetAddress, List<TcpConnection>> privateConnections;

    /**
     * Cria um gerenciador de conexões TCP vazio.
     */
    public TcpConnectionManager() {
        publicConnections = new ConcurrentHashMap<>();
        privateConnections = new ConcurrentHashMap<>();

        logger = Logger.getLogger(getClass().getName());
    }

    /**
     * Obtém a conexão pública registrada para um determinado peer.
     *
     * @param address endereço IP do peer.
     * @return a conexão pública registrada para o endereço ou {@code null}
     * caso não exista uma conexão pública registrada.
     */

    public TcpConnection getPublicConnection(InetAddress address) {
        return publicConnections.get(address);
    }

    /**
     * Registra uma conexão privada associada a um peer.
     *
     * <p>Um mesmo peer pode possuir múltiplas conexões privadas. Por isso,
     * as conexões são armazenadas em uma lista associada ao endereço do
     * peer.</p>
     *
     * @param connection conexão privada a ser registrada.
     */
    public void addPrivateConnection(TcpConnection connection) {
        List<TcpConnection> connections =
                privateConnections.computeIfAbsent(connection.getAddress(), e -> new ArrayList<>());

        synchronized (connections) {
            connections.add(connection);
        }
    }

    /**
     * Registra uma conexão pública associada a um peer.
     *
     * <p>Cada peer pode possuir apenas uma conexão pública registrada.
     * Quando já existe uma conexão pública para o endereço da nova conexão,
     * a conexão anterior é encerrada e substituída pela nova.</p>
     *
     * @param connection nova conexão pública a ser registrada.
     */

    public void addPublicConnection(TcpConnection connection) {
        TcpConnection oldConnection = publicConnections.put(connection.getAddress(), connection);
        if (oldConnection != null && oldConnection != connection) {
            closeConnection(oldConnection);
        }
    }

    /**
     * Envia uma mensagem de encerramento ao peer e fecha a conexão.
     *
     * <p>A mensagem enviada informa o motivo do encerramento antes que a
     * conexão seja removida do gerenciamento e fechada.</p>
     *
     * @param connection conexão TCP a ser encerrada.
     * @param reason motivo do encerramento que será informado ao peer.
     */
    public void closeConnection(TcpConnection connection, String reason) {
        InfoMessage.sendSystemInfo(connection, InfoType.CONNECTION_CLOSED, reason);
        closeConnection(connection);
    }

    /**
     * Remove uma conexão do gerenciamento e encerra seu socket.
     *
     * <p>Para conexões privadas, a conexão é removida da lista associada ao
     * endereço do peer. Caso essa seja a última conexão privada do endereço,
     * a lista também é removida do mapa.</p>
     *
     * <p>Para conexões públicas, a conexão é removida do mapa somente se
     * ainda for a conexão registrada para seu endereço.</p>
     *
     * <p>Se a conexão já estiver fechada ou for {@code null}, nenhuma ação
     * adicional de fechamento é realizada.</p>
     *
     * @param connection conexão TCP a ser removida e encerrada.
     */

    public void closeConnection(TcpConnection connection) {
        if (connection == null) return;
        if (connection.isPrivate()) {
            List<TcpConnection> connections = privateConnections.get(connection.getAddress());

            if (connections != null) {
                synchronized (connections) {
                    connections.remove(connection);

                    if (connections.isEmpty()) privateConnections.remove(connection.getAddress(), connections);
                }
            }
        }
        else publicConnections.remove(connection.getAddress(), connection);

        try {
            if (!connection.isClosed()) {
                connection.close();
            }
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error closing TCP connection", ex);
        }
    }

    /**
     * Envia uma mensagem de encerramento para todas as conexões de um peer
     * e, em seguida, fecha essas conexões.
     *
     * @param address endereço IP do peer cujas conexões serão encerradas.
     * @param reason motivo do encerramento informado ao peer.
     */
    public void closeByAdress(InetAddress address, String reason) {
        List<TcpConnection> connections = privateConnections.get(address);
        for (TcpConnection connection : connections) {
            InfoMessage.sendSystemInfo(connection, InfoType.CONNECTION_CLOSED, reason);
        }
        closeByAdress(address);
    }

    /**
     * Fecha e remove todas as conexões associadas a um determinado endereço.
     *
     * <p>Tanto a conexão pública quanto todas as conexões privadas associadas
     * ao endereço são encerradas e removidas do gerenciamento.</p>
     *
     * @param address endereço IP do peer cujas conexões serão encerradas.
     */
    public void closeByAdress(InetAddress address) {
        TcpConnection publicConnection = publicConnections.remove(address);

        if (publicConnection != null) {
            try {
                publicConnection.close();
            }
            catch (IOException ex) {
                logger.log(Level.SEVERE, "Error closing TCP connection", ex);
            }

        }

        List<TcpConnection> connections = privateConnections.remove(address);

        if (connections != null) {
            synchronized (connections) {
                for (TcpConnection connection : connections) {
                    try {
                        connection.close();
                    }
                    catch (IOException ex) {
                        logger.log(Level.SEVERE, "Error closing TCP connection", ex);
                    }
                }
                connections.clear();
            }
        }
    }

    /**
     * Envia uma mensagem de encerramento para todas as conexões privadas
     * gerenciadas e, em seguida, fecha todas elas.
     *
     * @param reason motivo do encerramento informado aos peers.
     */
    public void closeAllConnections(String reason) {
        for (List<TcpConnection> connections : privateConnections.values()) {
            for (TcpConnection connection : connections) {
                InfoMessage.sendSystemInfo(connection, InfoType.CONNECTION_CLOSED, reason);
            }
        }
        closeAllConnections();
    }

    /**
     * Fecha e remove todas as conexões públicas e privadas gerenciadas.
     *
     * <p>Após a execução, não devem permanecer conexões registradas nos
     * mapas de conexões deste gerenciador.</p>
     */

    public void closeAllConnections() {
        for (TcpConnection connection : publicConnections.values()) {
            closeConnection(connection);
        }

        synchronized (privateConnections) {
            for (List<TcpConnection> connectionsList : privateConnections.values()) {
                for (TcpConnection connection : connectionsList) {
                    closeConnection(connection);
                }
                privateConnections.clear();
            }
        }
    }

    /**
     * Atualiza as conexões de acordo com os peers atualmente ativos.
     *
     * <p>Os endereços presentes na lista de peers são utilizados como
     * referência para determinar quais conexões ainda devem permanecer
     * ativas. Conexões públicas e privadas associadas a endereços que não
     * estão mais presentes na lista de peers são encerradas e removidas.</p>
     *
     * @param peers lista atualizada de peers ativos.
     */

    @Override
    public void onPeersChanged(List<User> peers) {
        Set<InetAddress> activePeers = peers.stream().map(User::getAddress).collect(Collectors.toSet());

        for (InetAddress address : publicConnections.keySet()) {
            if (!activePeers.contains(address)) {
                closeByAdress(address);
            }
        }
        for (InetAddress address : privateConnections.keySet()) {
            if (!activePeers.contains(address)) {
                closeByAdress(address);
            }
        }
    }
}
