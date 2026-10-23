package pitiupi.models;

import java.net.InetAddress;
/**
 * Representa um usuário identificado na rede e as informações necessárias
 * para acompanhar sua presença através de mensagens de heartbeat.
 *
 * <p>Armazena o nome do usuário, seu endereço de rede e o instante da
 * última mensagem recebida.</p>
 *
 * @author tony
 */
public class User {

    private final String userName;
    private final InetAddress address;
    private volatile long lastSeen;

    public User(String userName, InetAddress address, long lastSeen) {
        this.userName = userName;
        this.address = address;
        this.lastSeen = lastSeen;
    }

    public String getUserName() {
        return userName;
    }

    public InetAddress getAddress() {
        return address;
    }

    public long getLastSeen() {
        return lastSeen;
    }

    /**
     * Atualiza o instante da última atividade do usuário.
     *
     * @param timestamp novo instante de atividade.
     */
    public void touch(long timestamp) {
        this.lastSeen = timestamp;
    }

    @Override
    public String toString() {
        return userName + " (" + address.getHostAddress() + ")";
    }
}
