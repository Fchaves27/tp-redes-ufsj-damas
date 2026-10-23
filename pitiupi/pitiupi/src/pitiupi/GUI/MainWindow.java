/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pitiupi.GUI;

import pitiupi.control.ConnectionManager;
import pitiupi.control.GuiManager;
import pitiupi.control.plugins.PluginManager;

import javax.swing.*;

import java.awt.*;

/**
 * Janela principal da aplicação.
 *
 * <p>Responsável por inicializar a interface gráfica e coordenar os
 * principais componentes da aplicação, incluindo comunicação de rede,
 * gerenciamento de heartbeat e carregamento de plugins.</p>
 *
 * <p>A instância desta classe é compartilhada com os plugins para permitir
 * integração com a aplicação principal.</p>
 *
 * @author flavio
 * @author tony
 * @author Gustavo
 */
public class MainWindow extends JFrame {

    private MenuBar menuBar;
    private JLabel status;
    OnlineUsersPanel onlineUsersPanel;

    private final ConnectionManager connectionManager;
    private final PluginManager pluginManager;
    private final GuiManager guiManager;

    /**
     * Cria a janela principal da aplicação.
     *
     * @param userName nome utilizado para identificação na rede.
     * @param port porta utilizada na comunicação multicast.
     */
    public MainWindow(String userName, int port) {
        initMenu();

        pluginManager = new PluginManager();
        connectionManager = new ConnectionManager(this, userName, pluginManager, port);

        initUserPanel();
        initStatusPanel();

        guiManager = new GuiManager(this, menuBar, onlineUsersPanel);

        loadPlugins();

        this.pack();

        this.setSize(new java.awt.Dimension(1024, 768));
    }

    private void initMenu() {
        menuBar = new MenuBar(this);

        this.setJMenuBar(menuBar);
        this.setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        this.setTitle("Pitiupi");

        javax.swing.JScrollPane jScrollPane1 = new javax.swing.JScrollPane();
        javax.swing.JTabbedPane tabbedPane = new javax.swing.JTabbedPane();

        jScrollPane1.setViewportView(tabbedPane);

        this.getContentPane().add(jScrollPane1, java.awt.BorderLayout.CENTER);
    }

    private void initUserPanel() {
        onlineUsersPanel = new OnlineUsersPanel();
        this.connectionManager.addPeerListener(onlineUsersPanel);
        this.getContentPane().add(onlineUsersPanel, java.awt.BorderLayout.LINE_END);
    }

    private void initStatusPanel() {
        JPanel statusPanel = new JPanel();
        status = new JLabel();

        statusPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));
        updateStatus();
        statusPanel.add(status);

        this.getContentPane().add(statusPanel, BorderLayout.PAGE_END);
    }

    public void exit() {
        this.setVisible(false);
        this.dispose();
        System.exit(0);
    }

    public ConnectionManager getConnectionManager() {
        return connectionManager;
    }

    public void loadPlugins() {
        pluginManager.loadPlugins(connectionManager, guiManager);
    }

    public void setUserName(String userName) {
        connectionManager.setUserName(userName);
        updateStatus();
    }

    private void updateStatus() {
        this.status.setText("Connected on port " + connectionManager.getPort() + " name " + connectionManager.getUserName());
    }

}
