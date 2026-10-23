package pitiupi.GUI;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.util.prefs.Preferences;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;

/**
 * Janela de preferencias da aplicação.
 *
 * <p>Permite ao usuário informar o nome utilizado na rede e a porta
 * multicast que será utilizada pela comunicação.</p>
 *
 * @author flavio
 * @author tony
 */
public final class Login extends JDialog {

    private static final Preferences prefs = Preferences.userNodeForPackage(Login.class);
    private static final String PREF_NOME = "lastUserName";
    private static final String PREF_PORTA = "lastPort";

    JLabel nameLabel = new JLabel("Name: ");
    JLabel portLabel = new JLabel("Port: ");

    JTextField nameField = new JTextField();
    JSpinner portField = new JSpinner();

    JButton okButton = new JButton("OK");
    JButton cancelButton = new JButton("Cancel");

    public Login() {
        initComponents();
        this.setSize(400, 150);
        this.setModal(true);
    }

    public void initComponents() {
        this.setTitle("Login");

        String nomeSalvo = prefs.get(PREF_NOME, System.getProperty("user.name"));
        int portaSalva = prefs.getInt(PREF_PORTA, 8000);

        portField.setModel(new javax.swing.SpinnerNumberModel(portaSalva, 8000, 9000, 1));
        nameField.setText(nomeSalvo);

        JPanel topPanel = new JPanel(new GridBagLayout());
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);

        GridBagConstraints gbc = new GridBagConstraints();

        gbc.insets = new Insets(4, 4, 4, 4);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        topPanel.add(nameLabel, gbc);

        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        topPanel.add(nameField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        topPanel.add(portLabel, gbc);

        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 1;
        topPanel.add(portField, gbc);

        this.add(topPanel);
        this.add(buttonPanel, BorderLayout.SOUTH);

        okButton.addActionListener((ActionEvent e) -> {
            try {
                portField.commitEdit();
            } catch (java.text.ParseException ex) {
                return;
            }

            prefs.put(PREF_NOME, nameField.getText());
            prefs.putInt(PREF_PORTA, (Integer) portField.getValue());

            Login.this.setVisible(false);
        });

        cancelButton.addActionListener((ActionEvent e) -> {
            System.exit(0);
        });
    }

    @Override
    public String getName() {
        return nameField.getText();
    }

    public int getPort() {
        return ((Integer) portField.getValue());
    }
}
