/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pitiupi.net.udp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.net.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import pitiupi.control.ConnectionManager;
import pitiupi.control.plugins.PluginManager;
import pitiupi.models.Messages.HeartbeatMessage;
import pitiupi.models.Messages.Message;

/**
 * Gerencia a comunicação UDP multicast e unicast da aplicação.
 *
 * <p>Mantém a conexão com o grupo multicast da aplicação e permite o envio
 * de mensagens tanto para o grupo quanto diretamente para um peer.</p>
 *
 * <p>As mensagens recebidas são verificadas para identificar mensagens de
 * heartbeat. As demais mensagens são encaminhadas aos plugins, que são
 * responsáveis por interpretá-las.</p>
 *
 * @author flavio
 * @author tony
 * @author Gustavo
 */
public class SocketUDP extends Thread {

    private MulticastSocket multicastSocket;

    private InetAddress address;

    private final ConnectionManager connectionManager;
    private final PluginManager pluginManager;

    private volatile boolean running = true;
    public final static String INET_ADDR = "224.0.0.3";

    /**
     * Cria e inicializa o socket UDP utilizado pela aplicação.
     *
     * <p>O socket é associado à porta da aplicação e ingressa no grupo
     * multicast configurado.</p>
     *
     * @param connectionManager interface que gerencia toda conexão da aplicação.
     * @param pluginManager interface que gerencia os plugins da conexão.
     */
    public SocketUDP(ConnectionManager connectionManager, PluginManager pluginManager) {
        this.connectionManager = connectionManager;
        this.pluginManager = pluginManager;

        try {
            this.address = InetAddress.getByName(SocketUDP.INET_ADDR);

            multicastSocket = new MulticastSocket(connectionManager.getPort());
            multicastSocket.setSendBufferSize(256000);
            multicastSocket.setReceiveBufferSize(256000);
            multicastSocket.setReuseAddress(true);
            multicastSocket.joinGroup(address);
        }
        catch (UnknownHostException ex) {
            System.getLogger(SocketUDP.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }
        catch (IOException ex) {
            System.out.println("There is no socket connection. Sorry.");
            System.out.println(ex);
        }
    }

    /**
     * Envia uma mensagem para o grupo multicast da aplicação.
     *
     * @param msg mensagem a ser enviada.
     * @throws IOException caso ocorra um erro durante o envio.
     */
    public void sendMulticast(Message msg)  throws IOException {
        send(msg.toByteArray(), this.address);
    }

    /**
     * Envia uma mensagem diretamente para um peer utilizando UDP.
     *
     * @param msg mensagem a ser enviada.
     * @param destinationAddress endereço IP do peer destinatário.
     * @throws IOException caso ocorra um erro durante o envio.
     */
    public void sendUnicast(Message msg, InetAddress destinationAddress)  throws IOException {
        send(msg.toByteArray(), destinationAddress);
    }

    /**
     * Envia uma mensagem UDP para o endereço especificado.
     *
     * @param msg mensagem serializada a ser enviada.
     * @param destinationAddress endereço de destino.
     * @throws IOException caso ocorra um erro durante o envio.
     */
    private void send(byte[] msg, InetAddress destinationAddress) throws IOException {
        DatagramPacket msgPacket;
        msgPacket = new DatagramPacket(msg, msg.length, destinationAddress, connectionManager.getPort());
        multicastSocket.send(msgPacket);
    }

    /**
     * Encerra a comunicação multicast e interrompe a recepção de mensagens.
     */
    public void close() {
        running = false;
        if (multicastSocket != null) {
            try {
                multicastSocket.leaveGroup(address);
            } catch (IOException ex) {
                System.out.println(ex);
            }
            multicastSocket.close();
        }
    }


    /**
     * Recebe e processa mensagens UDP.
     *
     * <p>Mensagens de heartbeat são encaminhadas ao gerenciador de heartbeat.
     * As demais mensagens são encaminhadas aos plugins carregados pela
     * aplicação.</p>
     *
     * @throws UnknownHostException caso não seja possível resolver o endereço
     *         utilizado pela comunicação.
     * @throws IOException caso ocorra um erro durante a recepção.
     * @throws ClassNotFoundException caso ocorra um erro ao verificar uma
     *         mensagem de heartbeat.
     */
    public void receive() throws UnknownHostException, IOException, ClassNotFoundException {
        byte[] buf = new byte[256000];
        while (running) {
            DatagramPacket msgPacket = new DatagramPacket(buf, buf.length);
            try {
                multicastSocket.receive(msgPacket);
            } catch (IOException ex) {
                if (!running) {
                    break;
                }
                throw ex;
            }

            HeartbeatMessage heartbeat = tryParseHeartbeat(msgPacket.getData());
            if (heartbeat != null) {
                connectionManager.receiveHeartbeat(heartbeat, msgPacket.getAddress());
                continue;
            }

            pluginManager.handleMessage(msgPacket.getData());
        }
    }

    /**
     * Tenta identificar os dados recebidos como uma mensagem de heartbeat.
     *
     * @param data dados serializados recebidos.
     * @return a mensagem de heartbeat caso os dados correspondam a uma;
     *         {@code null} caso contrário.
     */
    private HeartbeatMessage tryParseHeartbeat(byte[] data) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            ObjectInput in = new ObjectInputStream(bais);
            Object obj = in.readObject();
            return (obj instanceof HeartbeatMessage) ? (HeartbeatMessage) obj : null;
        } catch (IOException | ClassNotFoundException | ClassCastException ex) {
            return null; 
        }
    }

    /**
     * Inicia a recepção de mensagens UDP.
     */
    @Override
    public void run() {
        try {
            this.receive();
        } catch (IOException | ClassNotFoundException ex) {
            Logger.getLogger(SocketUDP.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
