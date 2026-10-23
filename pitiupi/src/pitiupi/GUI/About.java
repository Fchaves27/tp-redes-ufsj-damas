/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pitiupi.GUI;

import javax.swing.*;

/**
 * Janela que exibe informações sobre a aplicação, incluindo versão,
 * descrição e autores.
 *
 * @author gustavo
 */

public class About extends javax.swing.JFrame {
    public About () {
        this.setDefaultCloseOperation(javax.swing.WindowConstants.HIDE_ON_CLOSE);
        initComponents();
    }

    private void initComponents() {
        this.setTitle("About");
        this.setBounds(new java.awt.Rectangle(0, 0, 800, 600));
        this.getContentPane().setLayout(new java.awt.BorderLayout(5, 5));
        this.setPreferredSize(new java.awt.Dimension(800, 600));

        JScrollPane scroll = getJScrollPane();

        this.add(scroll);
    }

    private static JScrollPane getJScrollPane() {
        JEditorPane texto = new JEditorPane();
        texto.setContentType("text/html");
        texto.setEditable(false);

        texto.setText("""
            <html>
            <body>
            <h1>Share My Sheet</h1>
           \s
            <p>
            <b>Versão:</b> 1.0
            </p>
           \s
            <p>
            Plataforma P2P baseada em plugins para o desenvolvimento de aplicações
            distribuídas. Criada para apoiar as práticas da disciplina de Redes de
            Computadores e servir como base para projetos acadêmicos e TCCs.
           \s
            </p>
           \s
            <p>
            <b>Autores:</b><br>
            Flávio<br>
            Tony<br>
            Gustavo Henrique
            </p>
           \s
            <p>
            Universidade Federal de São João del-Rei (UFSJ)
            </p>
           \s
            </body>
            </html>
           \s""");

        return new JScrollPane(texto);
    }
}