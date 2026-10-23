package pitiupi.GUI;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.*;

import pitiupi.models.Menus.MenuCategory;
import pitiupi.models.User;
import pitiupi.control.PeerListener;

/**
 * Painel da interface gráfica responsável por exibir os usuários ativos
 * na rede.
 *
 * @author tony
 */
public class OnlineUsersPanel extends JPanel implements PeerListener {

    private final DefaultListModel<User> listModel;
    private final JList<User> userList;
    JPopupMenu menu;
    private Map<MenuCategory, JMenu> categories;

    public OnlineUsersPanel() {
        menu = new JPopupMenu();
        categories = new HashMap<>();
        initMenu();

        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Online"));
        setPreferredSize(new Dimension(160, 0));

        listModel = new DefaultListModel<>();
        userList = new JList<>(listModel);
        add(new JScrollPane(userList), BorderLayout.CENTER);

        setupUserListActions();
    }

    private void initMenu() {
        for (MenuCategory category : MenuCategory.values()) {
            JMenu newCategory = new JMenu(category.toString());

            menu.add(newCategory);
            categories.put(category, newCategory);
        }
    }

    private void setupUserListActions() {
        userList.addMouseListener(new MouseAdapter() {

            @Override
            public void mouseClicked(MouseEvent e) {
                int index = userList.locationToIndex(e.getPoint());

                if (index == -1) {
                    return;
                }

                userList.setSelectedIndex(index);
                menu.show(userList, e.getX(), e.getY());
            }
        });
    }

    public void initUnicastMenu(JMenuItem menuItem, Consumer<User> action) {
        menuItem.addActionListener(e -> {
            User user = userList.getSelectedValue();

            if (user != null) {
                action.accept(user);
            }
        });
    }

    public void addUnicastMenu(MenuCategory category, JMenuItem menuItem) {
        categories.get(category).add(menuItem);
    }

    /**
     * Atualiza a lista de usuários exibida no painel.
     *
     * @param peers lista atualizada de usuários ativos na rede.
     */
    @Override
    public void onPeersChanged(List<User> peers) {
        SwingUtilities.invokeLater(() -> {
            listModel.clear();
            for (User peer : peers) {
                listModel.addElement(peer);
            }
        });
    }
}