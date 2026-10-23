/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pitiupi.GUI;

import pitiupi.models.Menus.MenuCategory;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;

/**
 * Barra de menus principal da aplicação.
 *
 * <p>Gerencia os menus padrões da interface e disponibiliza um espaço
 * para que plugins adicionem seus próprios menus.</p>
 *
 * @author flavio
 * @author Gustavo
 * @author tony
 *
 */
public class MenuBar extends JMenuBar {

    private JMenuItem preferencesMenu;
    private JMenuItem aboutMenu;
    private JMenu fileMenu;
    private JMenu helpMenu;
    private JMenu pluginMenu;
    private Map<MenuCategory, JMenu> categories;

    private JMenuItem exitMenu;
    private final MainWindow mainWindow;

    /**
     * Cria a barra de menus principal da aplicação.
     *
     * @param mainWindow mainWindow janela principal da aplicação utilizada para executar
     */
    public MenuBar(MainWindow mainWindow) {
        this.mainWindow = mainWindow;
        this.initComponents();
    }

    private void initComponents() {
        initFileMenu();
        initPluginMenu();
        initHelpMenu();
    }

    private void initFileMenu() {
        fileMenu = new JMenu("File");
        exitMenu = new JMenuItem("Exit");

        exitMenu.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F4, InputEvent.ALT_DOWN_MASK));
        exitMenu.addActionListener(this::exitMenuActionPerformed);
        fileMenu.add(exitMenu);
        this.add(fileMenu);
    }

    private void initPluginMenu() {
        pluginMenu = new JMenu("Plugin");
        categories = new HashMap<>();

        for (MenuCategory category : MenuCategory.values()) {
            JMenu newCategoryMenu = new JMenu(category.toString());

            pluginMenu.add(newCategoryMenu);
            categories.put(category, newCategoryMenu);
        }

        this.add(pluginMenu);
    }

    private void initHelpMenu() {
        helpMenu = new JMenu("Help");
        preferencesMenu = new JMenuItem("Preferences");
        aboutMenu = new JMenuItem("About");

        preferencesMenu.addActionListener(this::setupMenuActionPerformed);
        helpMenu.add(preferencesMenu);

        aboutMenu.addActionListener(this::aboutMenuActionPerformed);
        helpMenu.add(aboutMenu);

        this.add(helpMenu);
    }

    private void exitMenuActionPerformed(ActionEvent evt) {
        mainWindow.exit();
    }

   private void setupMenuActionPerformed(ActionEvent evt) {
        Login login = new Login();
        login.setVisible(true);

        mainWindow.setUserName(login.getName());
        mainWindow.getConnectionManager().setPort(login.getPort());

        login.dispose();
    }

    private void aboutMenuActionPerformed(ActionEvent evt) {
        About about = new About();
        about.setVisible(true);
    }

    public void addPluginMenu(MenuCategory category, JMenuItem menu){
        categories.get(category).add(menu);
    }
}
