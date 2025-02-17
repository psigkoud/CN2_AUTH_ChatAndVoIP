package com.cn2.communication;

import java.io.*;
import java.net.*;
import javax.swing.*;
import javax.sound.sampled.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

public class App extends JFrame implements ActionListener {

    private JTextField inputTextField;
    private JTextArea textArea;
    private JButton sendButton;
    private JButton callButton;
    private JButton quickMessagesButton;
    private JButton emojiButton;
    private JPopupMenu quickMessagesMenu;
    private JPopupMenu emojiMenu;
    private JList<String> activeUsersList;
    private DefaultListModel<String> activeUsersModel;

    private DatagramSocket socket;
    private InetAddress remoteAddress;
    private int remotePort = 5500; // Change as needed
    private int localPort = 5500; // Change as needed

    private TargetDataLine microphone;
    private SourceDataLine speakers;

    public App() {
        super("CN2 - AUTH");
        initializeGUI();
        setupNetworking();
        addLocalUser();
    }

    private void initializeGUI() {
        // Set layout and appearance
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        
        // Create components
        textArea = new JTextArea(10, 40);
        textArea.setLineWrap(true);
        textArea.setEditable(false);
        textArea.setBackground(new Color(240, 248, 255)); // Light blue background
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        inputTextField = new JTextField(30);
        sendButton = new JButton("Send");
        callButton = new JButton("Call");
        quickMessagesButton = new JButton("Quick Messages");
        emojiButton = new JButton("Emoji");

        // Create quick messages menu
        quickMessagesMenu = new JPopupMenu();
        addQuickMessage("Hello!");
        addQuickMessage("How are you?");
        addQuickMessage("<3");
        addQuickMessage("Thanks!");
        addQuickMessage("Goodbye!");

        quickMessagesButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                quickMessagesMenu.show(quickMessagesButton, e.getX(), e.getY());
            }
        });

        // Create emoji menu
        emojiMenu = new JPopupMenu();
        addEmoji("π€");
        addEmoji("π‚");
        addEmoji("β¤οΈ");
        addEmoji("π‘");
        addEmoji("π‰");

        emojiButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                emojiMenu.show(emojiButton, e.getX(), e.getY());
            }
        });

        // Active users list
        activeUsersModel = new DefaultListModel<>();
        activeUsersList = new JList<>(activeUsersModel);
        JScrollPane usersScrollPane = new JScrollPane(activeUsersList);
        usersScrollPane.setPreferredSize(new Dimension(150, 200));
        activeUsersList.setBorder(BorderFactory.createTitledBorder("Active Users"));

        // Add action listeners
        sendButton.addActionListener(this);
        callButton.addActionListener(this);

        // Arrange components
        JPanel bottomPanel = new JPanel(new FlowLayout());
        bottomPanel.add(inputTextField);
        bottomPanel.add(sendButton);
        bottomPanel.add(callButton);
        bottomPanel.add(quickMessagesButton);
        bottomPanel.add(emojiButton);

        add(scrollPane, BorderLayout.CENTER);
        add(usersScrollPane, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);

        pack(); // Automatically size the frame
        setLocationRelativeTo(null); // Center the window
        setVisible(true);
    }

    private void addQuickMessage(String message) {
        JMenuItem menuItem = new JMenuItem(message);
        menuItem.setBackground(new Color(200, 230, 255)); // Light blue background
        menuItem.addActionListener(e -> sendQuickMessage(message));
        quickMessagesMenu.add(menuItem);
    }

    private void addEmoji(String emoji) {
        JMenuItem menuItem = new JMenuItem(emoji);
        menuItem.setBackground(new Color(255, 240, 200)); // Light orange background
        menuItem.addActionListener(e -> sendEmoji(emoji));
        emojiMenu.add(menuItem);
    }

    private void addLocalUser() {
        try {
            String localIP = InetAddress.getLocalHost().getHostAddress();
            updateActiveUsers(localIP);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    private void sendQuickMessage(String message) {
        try {
            byte[] buffer = message.getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, remoteAddress, remotePort);
            socket.send(packet);
            textArea.append("Local: " + message + "\n");
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void sendEmoji(String emoji) {
        try {
            byte[] buffer = emoji.getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, remoteAddress, remotePort);
            socket.send(packet);
            textArea.append("Local: " + emoji + "\n");
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void setupNetworking() {
        try {
            socket = new DatagramSocket(localPort);
            remoteAddress = InetAddress.getByName("172.17.0.8"); // Change to the remote IP
        } catch (Exception e) {
            e.printStackTrace();
        }

        startMessageReceiver();
    }

    private void startMessageReceiver() {
        Thread receiverThread = new Thread(() -> {
            byte[] buffer = new byte[1024];
            while (true) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    String senderIP = packet.getAddress().getHostAddress();
                    SwingUtilities.invokeLater(() -> updateActiveUsers(senderIP));

                    String received = new String(packet.getData(), 0, packet.getLength());
                    SwingUtilities.invokeLater(() -> textArea.append("Remote: " + received + "\n"));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        receiverThread.start();
    }

    private void updateActiveUsers(String senderIP) {
        if (!activeUsersModel.contains(senderIP)) {
            activeUsersModel.addElement(senderIP);
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == sendButton) {
            sendMessage();
        } else if (e.getSource() == callButton) {
            startAudioCommunication();
        }
    }

    private void sendMessage() {
        try {
            String message = inputTextField.getText();
            byte[] buffer = message.getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, remoteAddress, remotePort);
            socket.send(packet);
            textArea.append("Local: " + message + "\n");
            inputTextField.setText("");
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void startAudioCommunication() {
        new Thread(this::sendAudio).start();
        new Thread(this::receiveAudio).start();
    }

    private void sendAudio() {
        try {
            AudioFormat format = new AudioFormat(8000.0f, 8, 1, true, true);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            microphone = (TargetDataLine) AudioSystem.getLine(info);
            microphone.open(format);
            microphone.start();

            byte[] buffer = new byte[1024];
            while (true) {
                int bytesRead = microphone.read(buffer, 0, buffer.length);
                DatagramPacket packet = new DatagramPacket(buffer, bytesRead, remoteAddress, remotePort);
                socket.send(packet);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void receiveAudio() {
        try {
            AudioFormat format = new AudioFormat(8000.0f, 8, 1, true, true);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            speakers = (SourceDataLine) AudioSystem.getLine(info);
            speakers.open(format);
            speakers.start();

            byte[] buffer = new byte[1024];
            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                speakers.write(packet.getData(), 0, packet.getLength());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(App::new);
    }
}
