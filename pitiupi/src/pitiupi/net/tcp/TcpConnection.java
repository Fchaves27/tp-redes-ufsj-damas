package pitiupi.net.tcp;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.Arrays;

/**
 * Representa uma conexão TCP ativa com um peer remoto.
 *
 * <p>A conexão permite o envio e recebimento de mensagens, além de manter
 * informações sobre o endereço remoto e metadados da conexão, como remetente,
 * destinatário e se a conexão é privada.</p>
 *
 * @author Gustavo
 */
public class TcpConnection {

    private final Socket socket;
    private final DataInputStream input;
    private final DataOutputStream output;
    private final InetAddress address;
    private String sender;
    private String receiver;
    private boolean isPrivate;
    private volatile long lastActivity;
    private long timeout;

    /**
     * Cria uma conexão TCP a partir de um socket existente.
     *
     * @param socket socket utilizado para a comunicação
     * @throws IOException se não for possível obter os fluxos de entrada ou saída
     * do socket
     */
    TcpConnection(Socket socket) throws IOException {
        this.socket = socket;
        this.input = new DataInputStream(socket.getInputStream());
        this.output = new DataOutputStream(socket.getOutputStream());
        this.address = socket.getInetAddress();
        this.lastActivity = System.currentTimeMillis();
        this.timeout = 300_000; // 5 minutos
    }

    /**
     * Envia uma mensagem pela conexão.
     *
     * <p>A mensagem é enviada precedida pelo seu tamanho, permitindo que o
     * receptor determine quantos bytes devem ser lidos.</p>
     *
     * @param message mensagem a ser enviada
     * @throws IOException se ocorrer um erro durante o envio
     */
    public synchronized void send(byte[] message) throws IOException {
        output.writeInt(message.length);
        output.write(message);
        output.flush();
        updateLastActivity();
    }

    /**
     * Recebe uma mensagem da conexão.
     *
     * <p>Primeiro lê o tamanho da mensagem e, em seguida, lê aquantidade
     * correspondente de bytes.</p>
     *
     * @return mensagem recebida
     * @throws IOException se o tamanho da mensagem for inválido ou se a conexão
     * for encerrada antes que a mensagem seja completamente recebida
     */
    byte[] receive() throws IOException {
        int length = input.readInt();

        if (length < 0) throw new IOException("Invalid message length.");

        byte[] message = input.readNBytes(length);


        if (message.length != length) {
            throw new IOException("Connection closed before receiving the complete message.");
        }

        updateLastActivity();

        return message;
    }

    /**
     * Verifica se o socket da conexão está fechado.
     *
     * @return {@code true} se o socket estiver fechado;
     * {@code false} caso contrário
     */
    public boolean isClosed() {
        return socket.isClosed();
    }

    /**
     * Fecha o socket da conexão.
     *
     * @throws IOException se ocorrer um erro ao fechar o socket
     */
    void close() throws IOException {
        socket.close();
    }

    /**
     * Retorna o endereço do peer remoto conectado.
     *
     * @return endereço IP do peer remoto
     */
    public InetAddress getAddress() {
        return address;
    }

    /**
     * Atualiza o instante da última atividade da conexão.
     */
    private void updateLastActivity() {
        lastActivity = System.currentTimeMillis();
    }

    /**
     * Define o tempo limite de leitura do socket.
     *
     * @param timeout tempo limite, em milissegundos
     * @throws SocketException se ocorrer um erro ao definir o tempo limite
     */
    void setTimeout(int timeout) throws SocketException {
        socket.setSoTimeout(timeout);
        this.timeout = timeout;
    }

    /**
     * Verifica se a conexão ficou inativa pelo tempo limite configurado.
     *
     * <p>A verificação é baseada no instante da última atividade registrada
     * durante o envio ou recebimento de uma mensagem.</p>
     *
     * @return {@code true} se a conexão estiver inativa há pelo menos 5 minutos;
     * {@code false} caso contrário
     */
    boolean hasTimedOut() {
        return System.currentTimeMillis() - lastActivity >= timeout;
    }

    public String getReceiver() {
        return receiver;
    }

    public String getSender() {
        return sender;
    }

    public boolean isPrivate() {
        return isPrivate;
    }

    void setSender(String sender) {
        this.sender = sender;
    }

    void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    void setPrivate(boolean aPrivate) {
        isPrivate = aPrivate;
    }
}
