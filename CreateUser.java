import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Arrays;

public class CreateUser extends JFrame implements ActionListener {
    private JTextField nameField;
    private JButton colorButton;
    private JButton createButton;
    private JRadioButton individualRadio;
    private JRadioButton groupRadio;
    private Color selectedColor = Color.BLUE;

    public CreateUser() {
        setTitle("Create New User");
        setSize(400, 300);
        setLayout(new GridLayout(6, 1, 10, 10));

        // Name input
        JPanel namePanel = new JPanel();
        namePanel.add(new JLabel("Username:"));
        nameField = new JTextField(15);
        namePanel.add(nameField);

        // Color selection
        JPanel colorPanel = new JPanel();
        colorButton = new JButton("Choose Color");
        colorButton.addActionListener(this);
        colorPanel.add(colorButton);

        // Chat type selection
        JPanel typePanel = new JPanel();
        individualRadio = new JRadioButton("One-to-One");
        groupRadio = new JRadioButton("Group Chat");
        ButtonGroup typeGroup = new ButtonGroup();
        typeGroup.add(individualRadio);
        typeGroup.add(groupRadio);
        typePanel.add(individualRadio);
        typePanel.add(groupRadio);

        // Create button
        createButton = new JButton("Create User");
        createButton.addActionListener(this);

        add(namePanel);
        add(colorPanel);
        add(typePanel);
        add(createButton);

        setVisible(true);
    }

    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == colorButton) {
            selectedColor = JColorChooser.showDialog(this, 
                "Choose Theme Color", selectedColor);
        } else if (e.getSource() == createButton) {
            createUser();
        }
    }

    private void createUser() {
        String username = nameField.getText().trim();
        if (username.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Username cannot be empty!");
            return;
        }

        if (!individualRadio.isSelected() && !groupRadio.isSelected()) {
            JOptionPane.showMessageDialog(this, "Select chat type!");
            return;
        }

        String chatType = individualRadio.isSelected() ? "INDIVIDUAL" : "GROUP";
        
        SwingUtilities.invokeLater(() -> {
            new Client("localhost", 6001, username, 
                      selectedColor, chatType);
        });

        nameField.setText("");
        individualRadio.setSelected(false);
        groupRadio.setSelected(false);
    }

    public static void main(String[] args) {
        new CreateUser();
    }
}