import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;
import java.util.Arrays;
import java.util.Base64;
import java.nio.file.Files;
import java.nio.file.Path;

public class Client extends JFrame implements ActionListener, Runnable {
    private final String serverAddress;
    private final int serverPort;
    private final String username;
    private final Color themeColor;
    private final String chatType;
    private String selectedUser = null;

    private Socket socket;
    private DataInputStream dis;
    private DataOutputStream dos;

    private JTextArea chatArea;
    private JTextField messageField;
    private JButton sendButton;
    private JButton imageButton;
    private JButton disconnectButton;
    private JComboBox<String> groupCombo;
    private JList<String> userList;
    private DefaultListModel<String> userListModel = new DefaultListModel<>();

    public Client(String serverAddress, int serverPort, String username, 
                 Color themeColor, String chatType) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.username = username;
        this.themeColor = themeColor;
        this.chatType = chatType;

        initializeGUI();
        connectToServer();
    }

    private void initializeGUI() {
        setTitle("Chat Client - " + username);
        setSize(800, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        getContentPane().setBackground(themeColor);

        // Main panel
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(themeColor);
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Chat mode indicator
        JLabel chatModeLabel = new JLabel("Chat Mode: " + 
            (chatType.equals("INDIVIDUAL") ? "One-to-One" : "Group"));
        chatModeLabel.setFont(new Font("Arial", Font.BOLD, 14));
        chatModeLabel.setForeground(themeColor.darker().darker());
        chatModeLabel.setBorder(new EmptyBorder(5, 5, 5, 5));

        // Chat area
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setBackground(themeColor.darker());
        chatArea.setForeground(Color.WHITE);
        chatArea.setFont(new Font("Arial", Font.PLAIN, 14));
        JScrollPane scrollPane = new JScrollPane(chatArea);
        scrollPane.setBorder(new LineBorder(themeColor.brighter(), 2));
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // User list panel
        JPanel userPanel = new JPanel(new BorderLayout());
        userList = new JList<>(userListModel);
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userList.setFixedCellHeight(25);
        userList.setFont(new Font("Arial", Font.PLAIN, 12));
        userList.setBackground(themeColor.brighter());
        userList.setForeground(Color.BLACK);
        
        JScrollPane userScroll = new JScrollPane(userList);
        userScroll.setPreferredSize(new Dimension(150, 0));
        userScroll.setBorder(new LineBorder(themeColor.brighter(), 2));
        
        JButton selectUserButton = new JButton("Select User");
        styleButton(selectUserButton, themeColor);
        selectUserButton.addActionListener(e -> {
            if (chatType.equals("INDIVIDUAL")) {
                selectedUser = userList.getSelectedValue();
                if (selectedUser != null) {
                    chatArea.append("--- Now chatting with: " + selectedUser + " ---\n");
                    messageField.requestFocus();
                    userList.setSelectionBackground(themeColor);
                    userList.setSelectionForeground(Color.WHITE);
                } else {
                    JOptionPane.showMessageDialog(this, 
                        "Please select a user from the list first!",
                        "No User Selected",
                        JOptionPane.WARNING_MESSAGE);
                }
            } else {
                JOptionPane.showMessageDialog(this, 
                    "You're in group chat mode.\nSwitch to one-to-one in CreateUser.",
                    "Wrong Chat Mode",
                    JOptionPane.INFORMATION_MESSAGE);
            }
        });
        
        userPanel.add(new JLabel("Online Users:"), BorderLayout.NORTH);
        userPanel.add(userScroll, BorderLayout.CENTER);
        userPanel.add(selectUserButton, BorderLayout.SOUTH);
        mainPanel.add(userPanel, BorderLayout.WEST);

        // Control panel
        JPanel controlPanel = new JPanel(new BorderLayout());
        controlPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        controlPanel.add(chatModeLabel, BorderLayout.NORTH);

        // Group selection
        JPanel groupPanel = new JPanel();
        groupCombo = new JComboBox<>();
        groupCombo.addItem("General");
        groupPanel.add(new JLabel("Group:"));
        groupPanel.add(groupCombo);
        controlPanel.add(groupPanel, BorderLayout.NORTH);

        // Message input
        JPanel inputPanel = new JPanel(new BorderLayout());
        messageField = new JTextField();
        messageField.setFont(new Font("Arial", Font.PLAIN, 14));
        inputPanel.add(messageField, BorderLayout.CENTER);

        // Buttons panel
        JPanel buttonPanel = new JPanel(new GridLayout(1, 3));
        sendButton = new JButton("Send");
        imageButton = new JButton("Send Image");
        disconnectButton = new JButton("Disconnect");

        sendButton.addActionListener(this);
        imageButton.addActionListener(this);
        disconnectButton.addActionListener(this);

        styleButton(sendButton, themeColor.darker());
        styleButton(imageButton, themeColor.darker());
        styleButton(disconnectButton, new Color(200, 50, 50));

        buttonPanel.add(sendButton);
        buttonPanel.add(imageButton);
        buttonPanel.add(disconnectButton);

        inputPanel.add(buttonPanel, BorderLayout.EAST);
        controlPanel.add(inputPanel, BorderLayout.CENTER);
        mainPanel.add(controlPanel, BorderLayout.SOUTH);

        add(mainPanel);
        setVisible(true);
    }

    private void styleButton(JButton button, Color color) {
        button.setBackground(color);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setFont(new Font("Arial", Font.BOLD, 12));
    }

    private void connectToServer() {
        try {
            socket = new Socket(serverAddress, serverPort);
            dis = new DataInputStream(socket.getInputStream());
            dos = new DataOutputStream(socket.getOutputStream());
            
            dos.writeUTF(username);
            new Thread(this).start();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Connection failed: " + e.getMessage());
        }
    }

    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == sendButton) {
            sendTextMessage();
        } else if (e.getSource() == imageButton) {
            sendImage();
        } else if (e.getSource() == disconnectButton) {
            disconnect();
        }
    }

    private void sendTextMessage() {
        String message = messageField.getText().trim();
        if (!message.isEmpty()) {
            try {
                if (chatType.equals("INDIVIDUAL")) {
                    if (selectedUser == null) {
                        JOptionPane.showMessageDialog(this, 
                            "Please select a user first!\nClick 'Select User' button");
                        return;
                    }
                    String formatted = "TEXT|INDIVIDUAL|" + selectedUser + "|" + username + "|" + message;
                    chatArea.append("[You to " + selectedUser + "]: " + message + "\n");
                    dos.writeUTF(formatted);
                } else {
                    String group = (String) groupCombo.getSelectedItem();
                    if (group == null) {
                        JOptionPane.showMessageDialog(this, "No group selected");
                        return;
                    }
                    String formatted = "TEXT|GROUP|" + group + "|" + username + "|" + message;
                    chatArea.append("[You to " + group + "]: " + message + "\n");
                    dos.writeUTF(formatted);
                }
                messageField.setText("");
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Error sending message");
            }
        }
    }

    private void sendImage() {
        JFileChooser fileChooser = new JFileChooser();
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            try {
                File file = fileChooser.getSelectedFile();
                long fileSize = file.length();
                if (fileSize > 2 * 1024 * 1024) {
                    JOptionPane.showMessageDialog(this, "Image must be < 2MB");
                    return;
                }
                
                byte[] imageData = Files.readAllBytes(file.toPath());
                String base64Image = Base64.getEncoder().encodeToString(imageData);
                
                if (chatType.equals("INDIVIDUAL")) {
                    if (selectedUser == null) {
                        JOptionPane.showMessageDialog(this, "Please select a user first!");
                        return;
                    }
                    String formatted = "IMAGE|INDIVIDUAL|" + selectedUser + "|" + username + "|" + base64Image;
                    dos.writeUTF(formatted);
                } else {
                    String group = (String) groupCombo.getSelectedItem();
                    String formatted = "IMAGE|GROUP|" + group + "|" + username + "|" + base64Image;
                    dos.writeUTF(formatted);
                }
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Error reading image file");
            } catch (SecurityException ex) {
                JOptionPane.showMessageDialog(this, "Access denied to file");
            }
        }
    }

    private void disconnect() {
        try {
            dos.writeUTF("DISCONNECT");
            socket.close();
            dispose();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Error disconnecting");
        }
    }

    public void run() {
        try {
            while (true) {
                String message = dis.readUTF();
                handleServerMessage(message);
            }
        } catch (IOException e) {
            System.out.println("Disconnected from server");
        }
    }

    private void handleServerMessage(String message) {
        String[] parts = message.split("\\|", 5);
        String type = parts[0];
        
        SwingUtilities.invokeLater(() -> {
            switch (type) {
                case "TEXT":
                    if (parts[1].equals("GROUP")) {
                        chatArea.append("[" + parts[2] + "] " + parts[3] + ": " + parts[4] + "\n");
                    } else if (parts[1].equals("INDIVIDUAL")) {
                        chatArea.append("[Private from " + parts[2] + "]: " + parts[4] + "\n");
                    }
                    break;
                case "IMAGE":
                    if (parts[1].equals("GROUP")) {
                        chatArea.append("[" + parts[2] + "] " + parts[3] + " sent an image\n");
                    } else {
                        chatArea.append("[Private image from " + parts[2] + "]\n");
                    }
                    displayImage(parts[3], parts[4]);
                    break;
                case "GROUP_LIST":
                    updateGroupList(parts[1]);
                    break;
                case "USER_LIST":
                    updateUserList(parts[1]);
                    break;
                case "ERROR":
                    JOptionPane.showMessageDialog(this, parts[1], "Error", JOptionPane.ERROR_MESSAGE);
                    break;
            }
        });
    }
    private void displayImage(String sender, String imageData) {
        byte[] decoded = Base64.getDecoder().decode(imageData);
        ImageIcon icon = new ImageIcon(decoded);
        Image scaled = icon.getImage().getScaledInstance(200, 200, Image.SCALE_SMOOTH);
        
        JLabel imageLabel = new JLabel(new ImageIcon(scaled));
        JOptionPane.showMessageDialog(this, imageLabel, 
            "Image from " + sender, JOptionPane.PLAIN_MESSAGE);
    }

    private void updateUserList(String users) {
        userListModel.clear();
        Arrays.stream(users.split(","))
            .filter(u -> !u.equals(username))
            .forEach(userListModel::addElement);
    }

    private void updateGroupList(String groups) {
        groupCombo.removeAllItems();
        for (String group : groups.split(",")) {
            groupCombo.addItem(group);
        }
    }
}