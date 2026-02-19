package com.sh3d.mcp.plugin;

import com.sh3d.mcp.config.ClaudeDesktopConfigurator;
import com.sh3d.mcp.http.HttpMcpServer;
import com.sh3d.mcp.server.ServerState;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Диалог настроек MCP-сервера: управление сервером + автоконфигурация Claude Desktop.
 */
public class McpSettingsDialog extends JDialog {

    private static final Logger LOG = Logger.getLogger(McpSettingsDialog.class.getName());

    private final HttpMcpServer httpServer;

    private JLabel statusLabel;
    private JTextField portField;
    private JButton toggleButton;
    private JTextArea jsonArea;
    private JButton configureButton;

    public McpSettingsDialog(Frame owner, HttpMcpServer httpServer) {
        super(owner, "MCP Server Settings", false);
        this.httpServer = httpServer;

        initComponents();
        layoutComponents();
        updateState(httpServer.getState());

        httpServer.addStateListener((oldState, newState) ->
                SwingUtilities.invokeLater(() -> updateState(newState)));

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                setVisible(false);
            }
        });

        setDefaultCloseOperation(HIDE_ON_CLOSE);
        pack();
        setMinimumSize(getPreferredSize());
        setLocationRelativeTo(owner);
    }

    private void initComponents() {
        statusLabel = new JLabel();
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD));

        portField = new JTextField(String.valueOf(httpServer.getPort()), 8);

        toggleButton = new JButton();
        toggleButton.addActionListener(e -> onToggle());

        jsonArea = new JTextArea(8, 42);
        jsonArea.setEditable(false);
        jsonArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        updateJsonArea();

        // Обновлять JSON при изменении порта
        portField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { updateJsonArea(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { updateJsonArea(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { updateJsonArea(); }
        });

        configureButton = new JButton("Auto-configure");
        configureButton.setToolTipText("Write MCP config to Claude Desktop config file (with .bak backup)");
        configureButton.addActionListener(e -> onAutoConfigure());
    }

    private void layoutComponents() {
        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        // --- Server panel ---
        JPanel serverPanel = new JPanel(new GridBagLayout());
        serverPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Server",
                TitledBorder.LEFT, TitledBorder.TOP));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.anchor = GridBagConstraints.WEST;

        // Status row
        gbc.gridx = 0; gbc.gridy = 0;
        serverPanel.add(new JLabel("Status:"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL;
        serverPanel.add(statusLabel, gbc);

        // Port row
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1; gbc.fill = GridBagConstraints.NONE;
        serverPanel.add(new JLabel("Port:"), gbc);
        gbc.gridx = 1;
        serverPanel.add(portField, gbc);
        gbc.gridx = 2;
        serverPanel.add(toggleButton, gbc);

        content.add(serverPanel, BorderLayout.NORTH);

        // --- Claude Desktop panel ---
        JPanel claudePanel = new JPanel(new BorderLayout(0, 6));
        claudePanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Claude Desktop",
                TitledBorder.LEFT, TitledBorder.TOP));

        JScrollPane scrollPane = new JScrollPane(jsonArea);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        claudePanel.add(scrollPane, BorderLayout.CENTER);

        JPanel claudeButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton copyButton = new JButton("Copy to Clipboard");
        copyButton.addActionListener(e -> onCopyJson());
        claudeButtons.add(copyButton);
        claudeButtons.add(configureButton);

        // Показать путь к конфигу
        Path configPath = ClaudeDesktopConfigurator.resolveConfigPath();
        if (configPath != null) {
            JLabel pathLabel = new JLabel(configPath.toString());
            pathLabel.setFont(pathLabel.getFont().deriveFont(Font.ITALIC, 10f));
            pathLabel.setForeground(Color.GRAY);
            JPanel claudeBottom = new JPanel(new BorderLayout(0, 4));
            claudeBottom.add(claudeButtons, BorderLayout.NORTH);
            claudeBottom.add(pathLabel, BorderLayout.SOUTH);
            claudePanel.add(claudeBottom, BorderLayout.SOUTH);
        } else {
            claudePanel.add(claudeButtons, BorderLayout.SOUTH);
        }

        content.add(claudePanel, BorderLayout.CENTER);

        // --- Close button ---
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> setVisible(false));
        bottomPanel.add(closeButton);
        content.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(content);
    }

    private void updateState(ServerState state) {
        switch (state) {
            case RUNNING:
                statusLabel.setText("\u25CF Running on port " + httpServer.getPort());
                statusLabel.setForeground(new Color(0, 128, 0));
                toggleButton.setText("Stop");
                toggleButton.setEnabled(true);
                portField.setEnabled(false);
                break;
            case STOPPED:
                statusLabel.setText("\u25CF Stopped");
                statusLabel.setForeground(Color.GRAY);
                toggleButton.setText("Start");
                toggleButton.setEnabled(true);
                portField.setEnabled(true);
                // Показать ошибку при неудачном старте
                Exception error = httpServer.getLastStartupError();
                if (error != null) {
                    showError("Failed to start server on port " + httpServer.getPort()
                            + ":\n" + error.getMessage());
                }
                break;
            case STARTING:
                statusLabel.setText("\u25CF Starting...");
                statusLabel.setForeground(new Color(200, 150, 0));
                toggleButton.setEnabled(false);
                portField.setEnabled(false);
                break;
            case STOPPING:
                statusLabel.setText("\u25CF Stopping...");
                statusLabel.setForeground(new Color(200, 150, 0));
                toggleButton.setEnabled(false);
                portField.setEnabled(false);
                break;
        }
    }

    private void onToggle() {
        if (httpServer.getState() == ServerState.STOPPED) {
            int port = parsePort();
            if (port < 0) return;

            if (port != httpServer.getPort()) {
                httpServer.setPort(port);
            }
            httpServer.start();
        } else {
            httpServer.stop();
        }
    }

    private int parsePort() {
        String text = portField.getText().trim();
        try {
            int port = Integer.parseInt(text);
            if (port < 1 || port > 65535) {
                showError("Port must be between 1 and 65535.");
                return -1;
            }
            return port;
        } catch (NumberFormatException e) {
            showError("Invalid port number: " + text);
            return -1;
        }
    }

    private int getDisplayPort() {
        String text = portField.getText().trim();
        try {
            int port = Integer.parseInt(text);
            if (port >= 1 && port <= 65535) return port;
        } catch (NumberFormatException ignored) {
        }
        return httpServer.getPort();
    }

    private void updateJsonArea() {
        jsonArea.setText(ClaudeDesktopConfigurator.generateMcpJson(getDisplayPort()));
        jsonArea.setCaretPosition(0);
    }

    private void onCopyJson() {
        String json = jsonArea.getText();
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(json), null);
        JOptionPane.showMessageDialog(this, "JSON copied to clipboard.",
                "Copied", JOptionPane.INFORMATION_MESSAGE);
    }

    private void onAutoConfigure() {
        int port = getDisplayPort();
        int choice = JOptionPane.showConfirmDialog(this,
                "This will merge the MCP server config into Claude Desktop's\n"
                        + "configuration file. A .bak backup will be created.\n\n"
                        + "Proceed?",
                "Auto-configure Claude Desktop",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (choice != JOptionPane.OK_OPTION) return;

        try {
            ClaudeDesktopConfigurator.ConfigureResult result =
                    ClaudeDesktopConfigurator.configure(port);
            String msg = result.isCreated()
                    ? "Configuration file created:\n" + result.getConfigPath()
                    : "Configuration updated:\n" + result.getConfigPath()
                    + "\nBackup: " + result.getBackupPath();
            JOptionPane.showMessageDialog(this, msg,
                    "Success", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            LOG.log(Level.WARNING, "Auto-configure failed", ex);
            showError("Failed to configure Claude Desktop:\n" + ex.getMessage());
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message,
                "Error", JOptionPane.ERROR_MESSAGE);
    }
}
