package pitiupi.control;

import pitiupi.GUI.MainWindow;
import pitiupi.GUI.MenuBar;
import pitiupi.GUI.OnlineUsersPanel;
import pitiupi.models.Menus.*;
import pitiupi.models.Menus.MulticastMenus.MulticastActionMenu;
import pitiupi.models.Menus.MulticastMenus.MulticastMenu;
import pitiupi.models.Menus.MulticastMenus.MulticastSubMenu;
import pitiupi.models.Menus.UnicastMenus.UnicastActionMenu;
import pitiupi.models.Menus.UnicastMenus.UnicastMenu;
import pitiupi.models.Menus.UnicastMenus.UnicastSubMenu;

import javax.swing.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GuiManager {
    private MainWindow mainWindow;
    private MenuBar menuBar;
    private OnlineUsersPanel onlineUsersPanel;

    private Logger logger;

    public GuiManager(MainWindow mainWindow, MenuBar menuBar,  OnlineUsersPanel onlineUsersPanel) {
        this.mainWindow = mainWindow;
        this.menuBar = menuBar;
        this.onlineUsersPanel = onlineUsersPanel;
        this.logger = Logger.getLogger(GuiManager.class.getName());
    }

    public void createMenu(MenuCategory category, Menu menu) {
        System.out.println(
                menu.getClass().getSuperclass().getSuperclass()
        );

        System.out.println(
                MulticastMenu.class
        );

        System.out.println(
                menu.getClass().getSuperclass().getSuperclass()
                        == MulticastMenu.class
        );
        try {
            JMenuItem newMenu = buildMenu(menu);
            if (menu instanceof MulticastMenu) {
                menuBar.addPluginMenu(category, newMenu);
            }
            if (menu instanceof UnicastMenu) {
                onlineUsersPanel.addUnicastMenu(category, newMenu);
            }
        }
        catch (IllegalArgumentException e) {
            logger.log(Level.WARNING, e.getMessage());
        }
    }

    private JMenuItem buildMenu(Menu menu) {
        if (menu instanceof MulticastSubMenu multicastSubMenu) {
            JMenu newMenu = new JMenu(menu.getText());

            for (Menu child : multicastSubMenu.getMenus()) {
                newMenu.add(buildMenu(child));
            }

            return newMenu;
        }

        if (menu instanceof UnicastSubMenu unicastSubMenu) {
            JMenu newMenu = new JMenu(menu.getText());

            for (Menu child : unicastSubMenu.getMenus()) {
                newMenu.add(buildMenu(child));
            }

            return newMenu;
        }

        if (menu instanceof MulticastActionMenu multicastActionMenu) {
            JMenuItem newMenu = new JMenuItem(menu.getText());
            newMenu.addActionListener(e -> multicastActionMenu.onClick());
            return newMenu;
        }

        if  (menu instanceof UnicastActionMenu unicastActionMenu) {
            JMenuItem newMenu = new JMenuItem(menu.getText());

            onlineUsersPanel.initUnicastMenu(newMenu, unicastActionMenu::onClick);
            return newMenu;
        }

        throw new IllegalArgumentException("Invalid menu type");
    }
}
