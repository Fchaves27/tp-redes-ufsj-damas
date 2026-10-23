package pitiupi.control;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import pitiupi.models.User;
import pitiupi.models.Messages.HeartbeatMessage;
import java.util.UUID;
/**
 * Gerencia o mecanismo de heartbeat da aplicação.
 *
 * <p>Responsável por anunciar a presença da aplicação na rede, receber
 * heartbeats de outros usuários, controlar o tempo desde a última atividade
 * dos peers e notificar listeners quando a lista de usuários ativos muda.</p>
 *
 * @author tony
 */
public class HeartbeatManager {

    private static final long HEARTBEAT_INTERVAL_MS = 5000;
    private static final long PEER_TIMEOUT_MS = 15000; // ~3 batimentos perdidos
    private final ConnectionManager connectionManager;
    private final Map<String, User> peers = new ConcurrentHashMap<>();
    private final List<PeerListener> listeners = new CopyOnWriteArrayList<>();
    private ScheduledExecutorService scheduler;
    private final String instanceId = UUID.randomUUID().toString();

    /**
     * Cria um gerenciador de heartbeat associado à janela principal da aplicação.
     *
     */
    public HeartbeatManager(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    /**
     * Inicia o mecanismo de heartbeat.
     */
    public void start() {
        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(this::announce, 0, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(this::checkTimeouts, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Para o mecanismo de heartbeat e encerra as tarefas agendadas.
     */
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private void announce() {
        HeartbeatMessage msg = new HeartbeatMessage(connectionManager.getUserName(), instanceId);
        connectionManager.sendUdpMulticast(msg);
    }

    // Chamado pelo Socket quando um pacote é identificado como heartbeat
    /**
     * Processa uma mensagem de heartbeat recebida de outro usuário.
     *
     * <p>Atualiza o momento da última atividade do peer correspondente e notifica os listeners
     * caso outra instância da aplicação seja identificada.</p>
     *
     * <p>Mensagens de heartbeat enviadas pela própria aplicação são ignoradas.</p>
     *
     * @param msg mensagem de heartbeat recebida.
     * @param from endereço de rede do usuário que enviou a mensagem.
     */

    public void receiveHeartbeat(HeartbeatMessage msg, InetAddress from) {
        // ignora o próprio heartbeat 
        if (msg.getInstanceId().equals(instanceId) ) {
            return;
        }

        String key =  msg.getInstanceId();
        boolean isNew = !peers.containsKey(key);

        User peer = peers.computeIfAbsent(key, k -> new User(msg.getUserName(), from, msg.getTimestamp()));
        peer.touch(System.currentTimeMillis());

        if (isNew) {
            notifyListeners();
        }
    }

    private void checkTimeouts() {
        long now = System.currentTimeMillis();
        boolean changed = peers.values().removeIf(p -> now - p.getLastSeen() > PEER_TIMEOUT_MS);
        if (changed) {
            notifyListeners();
        }
    }

    private void notifyListeners() {
        List<User> snapshot = new ArrayList<>(peers.values());
        for (PeerListener l : listeners) {
            l.onPeersChanged(snapshot);
        }
    }

    /**
     * Adiciona um listener para receber atualizações sobre os peers conhecidos.
     *
     * <p>O listener recebe imediatamente o estado atual dos peers ao ser
     * registrado.</p>
     *
     * @param l listener que será notificado sobre alterações.
     */
    public void addPeerListener(PeerListener l) {
        listeners.add(l);
        l.onPeersChanged(new ArrayList<>(peers.values()));
    }

    /**
     * Remove um listener previamente registrado.
     *
     * @param l listener que deixará de receber atualizações.
     */
    public void removePeerListener(PeerListener l) {
        listeners.remove(l);
    }

    /**
     * Retorna uma cópia da lista atual de peers conhecidos.
     *
     * @return lista contendo os peers atualmente conhecidos.
     */
    public List<User> getPeers() {
        return new ArrayList<>(peers.values());
    }
}
