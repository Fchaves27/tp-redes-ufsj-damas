package pitiupi.net.tcp;

import pitiupi.models.Messages.Message;
import pitiupi.models.Messages.InfoMessage;
import pitiupi.models.Messages.InfoType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Fábrica responsável pela criação e configuração de conexões TCP.
 *
 * <p>Esta classe centraliza o estabelecimento de conexões de saída e a
 * configuração de conexões recebidas. Antes de uma conexão ser considerada
 * válida, é realizado um handshake entre os peers para identificar o remetente,
 * o destinatário e se a conexão é privada.</p>
 *
 * <p>Durante o handshake é utilizado um timeout menor, definido por
 * {@link #HANDSHAKE_TIMEOUT}. Após a validação bem-sucedida, a conexão passa
 * a utilizar o timeout padrão definido por {@link #STANDARD_TIMEOUT}.</p>
 */
public class ConnectionFactory {
    private static final int HANDSHAKE_TIMEOUT = 1000;
    private static final int STANDARD_TIMEOUT = 300_000;

    /**
     *  Cria uma conexão TCP de saída com o peer de destino e realiza o handshake
     *  necessário para validá-la.
     *
     *  <p>A conexão é configurada com os identificadores do remetente e do
     *  destinatário e com o tipo indicado pelo parâmetro {@code isPrivate}.
     *  Após o estabelecimento do socket, o método envia um
     *  {@link HandshakeMessage} e aguarda uma resposta válida.</p>
     *
     *  <p>Se o handshake for validado com sucesso, o timeout da conexão é
     *  alterado de {@link #HANDSHAKE_TIMEOUT} para {@link #STANDARD_TIMEOUT}
     *  e a conexão é retornada. Caso contrário, {@code null} é retornado.</p>
     *
     *  @param address endereço IP do peer de destino.
     *  @param port porta TCP na qual o peer de destino está aguardando conexões.
     *  @param sender identificador do remetente da conexão.
     *  @param receiver identificador do destinatário da conexão.
     *  @param isPrivate {@code true} se a conexão deve ser privada;
     *  {@code false} se deve ser pública.
     *  @return a conexão TCP estabelecida e validada ou {@code null} caso
     *  não seja possível estabelecer ou validar a conexão.
     */
    public static TcpConnection createConnection(InetAddress address, int port, String sender, String receiver, boolean isPrivate) {
        try {
            TcpConnection connection;

            Socket socket = new Socket(address, port);
            connection = new TcpConnection(socket);
            connection.setSender(sender);
            connection.setReceiver(receiver);
            connection.setPrivate(isPrivate);

            HandshakeMessage handshake = new HandshakeMessage(isPrivate, sender, receiver);

            connection.send(handshake.toByteArray());
            HandshakeMessage handshakeResponse = validateConnection(connection);

            if (handshakeResponse == null) {
                return null;
            }

            connection.setTimeout(STANDARD_TIMEOUT);

            return connection;
        }
        catch (IOException ex) {
            Logger.getLogger(ConnectionFactory.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    /**
     * Configura uma conexão TCP recebida e valida o handshake enviado pelo
     * peer remoto.
     *
     * <p>O handshake recebido é utilizado para obter e configurar os
     * identificadores do remetente e do destinatário, bem como o tipo da
     * conexão. Após a validação bem-sucedida, o timeout da conexão é alterado
     * para {@link #STANDARD_TIMEOUT}.</p>
     *
     * <p>O {@link Socket} recebido deve representar uma conexão TCP já aceita
     * pelo servidor.</p>
     *
     * @param socket socket TCP correspondente à conexão recebida.
     * @return a conexão TCP configurada e validada ou {@code null} caso
     * o handshake seja inválido ou não possa ser processado.
     */
    public static TcpConnection receiveConnection(Socket socket) {
        try {
            TcpConnection connection;

            connection = new TcpConnection(socket);

            HandshakeMessage handshake = validateConnection(connection);

            if (handshake == null) return null;

            connection.send(handshake.createResponse().toByteArray());

            connection.setSender(handshake.getSender());
            connection.setReceiver(handshake.getReceiver());
            connection.setPrivate(handshake.isPrivate());

            connection.setTimeout(STANDARD_TIMEOUT);

            return connection;
        }
        catch (IOException ex) {
            Logger.getLogger(ConnectionFactory.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    /**
     * Aguarda e valida a mensagem de handshake de uma conexão TCP.
     *
     * <p>Durante a validação, a conexão utiliza {@link #HANDSHAKE_TIMEOUT}
     * como limite para o recebimento do handshake. A mensagem recebida deve
     * ser uma instância de {@link Message} e, especificamente, de
     * {@link HandshakeMessage}. Mensagens de sistema ou mensagens de outros
     * tipos são rejeitadas.</p>
     *
     * <p>Quando o handshake é recebido, seus campos de remetente e destinatário
     * são validados por {@link #validateHandshake(HandshakeMessage, TcpConnection)}.
     * Caso a validação seja bem-sucedida, o próprio handshake é retornado.</p>
     *
     * <p>Em caso de timeout, mensagem inválida ou erro durante o processamento,
     * uma mensagem de sistema apropriada pode ser enviada ao peer e
     * {@code null} é retornado.</p>
     *
     * @param connection conexão TCP da qual o handshake será recebido.
     * @return o {@link HandshakeMessage} validado ou {@code null} caso o
     * handshake não seja recebido ou seja inválido.
     */
    private static HandshakeMessage validateConnection(TcpConnection connection) {
        try {
            connection.setTimeout(HANDSHAKE_TIMEOUT);
            byte[] bytes = connection.receive();

            ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
            ObjectInputStream ois = new ObjectInputStream(bis);

            Object object = ois.readObject();

            if (object instanceof Message message) {
                InfoMessage sysInfo = InfoMessage.parseSystemInfo(bytes);
                if (sysInfo != null) return null;


                if (message instanceof HandshakeMessage handshake) {
                    return (validateHandshake(handshake, connection)) ? handshake : null;
                }
            }

            InfoMessage.sendSystemInfo(
                    connection,
                    InfoType.CONNECTION_REJECTED,
                    "The message was not from an allowed type."
            );
        }
        catch (SocketTimeoutException ex) {
            InfoMessage.sendSystemInfo(
                    connection,
                    InfoType.CONNECTION_REJECTED,
                    "No handshake was received within the allowed time."
            );
        }
        catch (ClassNotFoundException | IOException ex) {
            Logger.getLogger(ConnectionFactory.class.getName()).log(Level.SEVERE, null, ex);

            InfoMessage.sendSystemInfo(
                    connection,
                    InfoType.INTERNAL_ERROR,
                    "The server could not process the handshake."
            );
        }

        return null;
    }

    /**
     * Valida os identificadores presentes em uma mensagem de handshake.
     *
     * <p>O remetente e o destinatário devem estar presentes e não podem ser
     * vazios ou conter apenas espaços em branco. Quando um dos identificadores
     * é inválido, uma mensagem de rejeição é enviada ao peer e a validação
     * falha.</p>
     *
     * @param handshake mensagem de handshake a ser validada.
     * @param connection conexão associada ao handshake, utilizada para enviar
     * uma mensagem de rejeição quando necessário.
     * @return {@code true} se os identificadores do remetente e do destinatário
     * forem válidos; {@code false} caso contrário.
     */
    private static boolean validateHandshake(HandshakeMessage handshake, TcpConnection connection) {
        String sender = handshake.getSender();
        String receiver = handshake.getReceiver();

        if (sender == null || sender.isBlank()) {
            InfoMessage.sendSystemInfo(
                    connection,
                    InfoType.CONNECTION_REJECTED,
                    "Invalid Sender: " + sender + "."
            );
            return false;
        }
        if (receiver == null || receiver.isBlank()) {
            InfoMessage.sendSystemInfo(
                    connection,
                    InfoType.CONNECTION_REJECTED,
                    "Invalid Receiver: " + receiver + "."
            );
            return false;
        }

        return true;
    }
}
