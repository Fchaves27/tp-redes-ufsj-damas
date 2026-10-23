package pitiupi.control;

import pitiupi.models.User;

import java.util.List;

/**
 * Interface para receber notificações sobre alterações nos usuários
 * conhecidos na rede.
 *
 * <p>Implementações desta interface são notificadas sempre que a lista
 * de usuários ativos é modificada.</p>
 *
 * @author tony
 */
public interface PeerListener {
    /**
     * Chamado quando a lista de usuários conhecidos na rede é alterada.
     *
     * @param peers lista atualizada de usuários ativos.
     */
    void onPeersChanged(List<User> peers);
}
