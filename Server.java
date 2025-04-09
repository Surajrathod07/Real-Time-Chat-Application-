import java.net.*;
import java.util.*;
import java.io.*;

public class Server {
    private static final int PORT = 6001;
    private static final Map<String, ClientHandler> clients = new HashMap<>();
    private static final Map<String, Group> groups = new HashMap<>();

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started on port " + PORT);
            
            while (true) {
                Socket socket = serverSocket.accept();
                new ClientHandler(socket).start();
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    private static void broadcastUserList() {
        String userList = String.join(",", clients.keySet());
        clients.values().forEach(client -> {
            try {
                client.dos.writeUTF("USER_LIST|" + userList);
            } catch (IOException e) {
                System.err.println("Error sending user list");
            }
        });
    }

    static class ClientHandler extends Thread {
        private final Socket socket;
        private DataInputStream dis;
        private DataOutputStream dos;
        private String username;
        private String currentGroup;

        public ClientHandler(Socket socket) {
            this.socket = socket;
            // Broadcast user list when new client connects
            new Thread(() -> {
                try {
                    Thread.sleep(500);
                    broadcastUserList();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        }

        public void run() {
            try {
                dis = new DataInputStream(socket.getInputStream());
                dos = new DataOutputStream(socket.getOutputStream());
        
                // Authentication: Read the username first.
                username = dis.readUTF();
        
                synchronized (clients) {
                    clients.put(username, this);
                    broadcastUserList();
                }
                
                // *** Automatically join the "General" group ***
                handleGroupJoin("General");
        
                // Main message loop
                while (true) {
                    String message = dis.readUTF();
                    handleMessage(message);
                }
            } catch (IOException e) {
                disconnect();
            }
        }
        

        private void handleMessage(String message) {
            String[] parts = message.split("\\|", 5);
            String type = parts[0];
            
            switch (type) {
                case "TEXT":
                    handleTextMessage(parts);
                    break;
                case "IMAGE":
                    handleImageMessage(parts);
                    break;
                case "JOIN_GROUP":
                    handleGroupJoin(parts[1]);
                    break;
                case "LEAVE_GROUP":
                    handleGroupLeave();
                    break;
                case "DISCONNECT":
                    disconnect();
                    break;
            }
        }

        private void handleTextMessage(String[] parts) {
            String chatType = parts[1];
            String content = parts[4];
            
            if (chatType.equals("GROUP")) {
                // Extract the group name directly from the received message.
                String groupName = parts[2];  // Should be "General" if sent by the client.
                Group group = groups.get(groupName);
                if (group != null) {
                    group.broadcast("TEXT|GROUP|" + groupName + "|" + username + "|" + content);
                } else {
                    try {
                        dos.writeUTF("ERROR|Group not found: " + groupName);
                    } catch (IOException e) {
                        System.err.println("Error sending group not found message");
                    }
                }
            } else if (chatType.equals("INDIVIDUAL")) {
                String recipient = parts[2];
                ClientHandler recipientHandler = clients.get(recipient);
                if (recipientHandler != null) {
                    try {
                        recipientHandler.dos.writeUTF("TEXT|INDIVIDUAL|" + username + "||" + content);
                    } catch (IOException e) {
                        System.err.println("Error sending private message");
                    }
                }
            }
        }
        
        

        private void handleImageMessage(String[] parts) {
            String chatType = parts[1];
            String imageData = parts[4];
            
            if (chatType.equals("GROUP")) {
                // Use the group name from parts[2] here as well.
                String groupName = parts[2];
                Group group = groups.get(groupName);
                if (group != null) {
                    group.broadcast("IMAGE|GROUP|" + groupName + "|" + username + "|" + imageData);
                } else {
                    try {
                        dos.writeUTF("ERROR|Group not found: " + groupName);
                    } catch (IOException e) {
                        System.err.println("Error sending group not found message");
                    }
                }
            } else if (chatType.equals("INDIVIDUAL")) {
                String recipient = parts[2];
                ClientHandler recipientHandler = clients.get(recipient);
                if (recipientHandler != null) {
                    try {
                        recipientHandler.dos.writeUTF("IMAGE|INDIVIDUAL|" + username + "||" + imageData);
                    } catch (IOException e) {
                        System.err.println("Error sending private image");
                    }
                }
            }
        }

        private void handleGroupJoin(String groupName) {
            Group group = groups.computeIfAbsent(groupName, k -> new Group());
            
            if (group.members.size() < 5) {
                // Leave previous group if necessary
                if (currentGroup != null) {
                    handleGroupLeave();
                }
                group.addMember(username);
                currentGroup = groupName;   // Set the current group to "General"
                updateGroupList();
        
                group.broadcast("TEXT|GROUP|" + currentGroup + "|System|" + username + " has joined the group");
            } else {
                try {
                    dos.writeUTF("ERROR|Group is full (max 5 members)");
                } catch (IOException e) {
                    System.err.println("Error sending group full message");
                }
            }
        }
        

        private void handleGroupLeave() {
            if (currentGroup != null) {
                Group group = groups.get(currentGroup);
                if (group != null) {
                    group.removeMember(username);
                    if (group.members.isEmpty()) {
                        groups.remove(currentGroup);
                    } else {
                        group.broadcast("TEXT|GROUP|" + currentGroup + "|System|" + 
                                      username + " has left the group");
                    }
                }
                currentGroup = null;
                updateGroupList();
            }
        }

        private void updateGroupList() {
            try {
                dos.writeUTF("GROUP_LIST|" + String.join(",", groups.keySet()));
            } catch (IOException e) {
                System.err.println("Error updating group list");
            }
        }

        private void disconnect() {
            try {
                handleGroupLeave();
                synchronized(clients) {
                    clients.remove(username);
                    broadcastUserList();
                }
                socket.close();
                System.out.println(username + " disconnected");
            } catch (IOException e) {
                System.err.println("Error during disconnect");
            }
        }
    }

    static class Group {
        Set<String> members = new HashSet<>();

        void addMember(String user) {
            members.add(user);
        }

        void removeMember(String user) {
            members.remove(user);
        }

        void broadcast(String message) {
            members.forEach(member -> {
                ClientHandler client = clients.get(member);
                if (client != null) {
                    try {
                        client.dos.writeUTF(message);
                    } catch (IOException e) {
                        System.err.println("Error broadcasting to group");
                    }
                }
            });
        }
    }
}