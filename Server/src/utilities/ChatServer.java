package utilities;

import javax.imageio.IIOException;
import javax.xml.crypto.Data;
import java.io.*;
import java.net.*;
import java.util.*;

public class ChatServer {
    private static final int PORT = 12345;
    private static final int FILE_PORT = 49000;

    private static boolean isPortAvailable(int port) {
        try (ServerSocket socket = new ServerSocket(port)) {
            socket.close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static int findAvailablePort(int startPort) {
        int port = startPort;
        while (!isPortAvailable(port) && port < 65535) {
            port++;
        }
        if (port == 65535) {
            throw new RuntimeException("No available ports found");
        }
        return port;
    }

    public enum ClientState {
        INIT,
        ASKING_FOR_ACCOUNT,
        CREATING_ACCOUNT,
        WAITING_FOR_USERNAME,
        WAITING_FOR_PASSWORD,
        LOGGED_IN,
        CONNECTING_TO_USER,
        CHAT,
        EXIT;
    }

    private static Map<Socket, ClientState> clientStates = new HashMap<>();
    private static Map<String, DataOutputStream> clientWriters = new HashMap<>();

    public void updateState(Socket socket, ClientState clientState) {
        synchronized (clientStates) {
            clientStates.put(socket, clientState);
        }
    }

    public static int getFilePort() {
        return FILE_PORT;
    }

    public static int getPort() {
        return PORT;
    }

    public static void main(String[] args) {
        ChatServer server = new ChatServer();
        System.out.println("Chat server has started...");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler clientHandler = new ClientHandler(clientSocket, server);
                clientSocket.setKeepAlive(true);
                clientHandler.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Clasa FileReceiver
    public static class FileReceiver implements Runnable {
        private final ChatServer chatServer;
        private final String username;
        private final String targetUser;
        private ServerSocket serverSocket;

        public FileReceiver(ChatServer chatServer, String username, String targetUser) {
            this.chatServer = chatServer;
            this.username = username;
            this.targetUser = targetUser;
        }

        @Override
        public void run() {
            try {
                int availablePort = findAvailablePort(FILE_PORT);
                serverSocket = new ServerSocket(availablePort);
                System.out.println("FileReceiver listening on port: " + availablePort);

                while (!Thread.currentThread().isInterrupted()) {
                    FileTransfer.receiveFile(username, targetUser, serverSocket);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (serverSocket != null && !serverSocket.isClosed()) {
                        serverSocket.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public void stop() {
            try {
                if (serverSocket != null) {
                    serverSocket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static class ClientHandler extends Thread {
        private Socket socket;
        private DataOutputStream out;
        private DataInputStream in;
        private String username;
        private String password;
        private String targetUser;
        private final ChatServer chatServer;
        private boolean isAndroid = false;

        public ClientHandler(Socket socket, ChatServer chatServer) {
            this.socket = socket;
            this.chatServer = chatServer;
        }

        public void updateState(ClientState state) {
            synchronized (clientStates) {
                clientStates.put(socket, state);
            }
        }

        private void sendMessageToUser(String message) {
            History.sendMessage(message, username, targetUser, clientWriters);
        }


        public void run() {
            try {
                in = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());
                boolean isActive = true;
                boolean fileTransfer = false;

                synchronized (clientStates) {
                    clientStates.put(socket, ClientState.INIT);
                }

                while (isActive) {
                    ClientState state;
                    synchronized (clientStates) {
                        state = clientStates.get(socket);
                    }

                    switch (state) {
                        case INIT:
                            out.writeUTF("Welcome to the chat server!");
                            out.writeUTF("Do you have an account? (1. Yes or 2. No)");
                            System.out.println("INIT STATE");
                            updateState(ClientState.ASKING_FOR_ACCOUNT);

                            break;

                        case ASKING_FOR_ACCOUNT:
                            System.out.println("ASKING FOR ACCOUNT STATE");
                            String input = in.readUTF();

                            System.out.println("DEBUG: Am primit input " + input);

                            if("CLIENT_TYPE:ANDROID".equals(input)) {
                                System.out.println("ANDROID");
                                isAndroid = true;
                                input = in.readUTF();
                                System.out.println("Input secundar dupa android " + input + " ");
                            }

                            if ("1".equals(input)) {
                                out.writeUTF("What's your name?");
                                updateState(ClientState.WAITING_FOR_USERNAME);
                            } else if ("2".equals(input)) {
                                out.writeUTF("Set a name for your account.");
                                updateState(ClientState.CREATING_ACCOUNT);
                            } else {
                                out.writeUTF("Invalid choice. Please enter 1 or 2.");
                            }
                            break;

                        case CREATING_ACCOUNT:
                            System.out.println("CREATING ACCOUNT STATE");
                            input = in.readUTF();

                            username = input;
                            CreateAcc.setName(input);
                            out.writeUTF("What's your password?");
                            input = in.readUTF();
                            CreateAcc.setPassword(input);
                            sendToDataBase.insertCredentials(username, input);
                            out.writeUTF("You have successfully created an account, now you need to insert your credentials again to sign in.");
                            out.writeUTF("Press 1 to go to the sign-in menu.");
                            updateState(ClientState.ASKING_FOR_ACCOUNT);
                            break;

                        case WAITING_FOR_USERNAME:
                            System.out.println("WAITING FOR USERNAME STATE");
                            username = in.readUTF();

                            out.writeUTF("What's your password?");
                            clientWriters.put(username, out);  // Adaugă utilizatorul
                            updateState(ClientState.WAITING_FOR_PASSWORD);
                            break;

                        case WAITING_FOR_PASSWORD:
                            System.out.println("WAITING FOR PASSWORD STATE");
                            password = in.readUTF();

                            if (SignIn.Credentials(username, password)) {
                                out.writeUTF("Login successful!");
                                if(!isAndroid) {
                                    History.processUndeliveredMessages(username, clientWriters);
                                }
                                updateState(ClientState.LOGGED_IN);
                            } else {
                                out.writeUTF("Login failed! Try again.");
                                updateState(ClientState.ASKING_FOR_ACCOUNT);
                            }
                            break;

//                        case LOGGED_IN: {
//                            System.out.println("LOGGED IN STATE");
//                            ArrayList<String> usernames = DataBase.getUsernames();
//                            ArrayList<String> lastMessage = History.lastMessageFromEveryone(username);
//
//                            System.out.println(usernames + " ");
//
//                            out.writeUTF("Who do you want to connect with?");
//                            usernames.forEach(user -> {
//                                try {
//                                    out.writeUTF(user);
//                                } catch (IOException e) {
//                                    e.printStackTrace();
//                                }
//                            });
//                            out.writeUTF("END_OF_LIST");
//                            lastMessage.forEach(msg -> {
//                                try {
//                                    out.writeUTF(msg);
//                                } catch (IOException e) {
//                                    e.printStackTrace();
//                                }
//                            });
//                            out.writeUTF("END_OF_LIST_OF_MESSAGES");
//                            updateState(ClientState.CONNECTING_TO_USER);
//                            break;
//                        }

                        case LOGGED_IN: {
                            System.out.println("LOGGED IN STATE");

                            ArrayList<String> combinedInbox = History.lastMessageFromEveryone(username);

                            System.out.println("DEBUG: Am gasit " + combinedInbox.size() + " conversatii pentru userul" + username);
                            //System.out.println("DEBUG: Continut lista " + combinedInbox);

                            out.writeUTF("INBOX_DATA");
                            out.writeInt(combinedInbox.size());

                            for(String entry : combinedInbox) {
                                byte[] entryBytes = entry.getBytes("UTF-8");
                                out.writeInt(entryBytes.length);
                                out.write(entryBytes);
                            }

                            out.flush();
                            updateState(ClientState.CONNECTING_TO_USER);

                            break;
                        }


                        case CONNECTING_TO_USER: {
                            System.out.println("CONNECTING TO USER STATE");
                            targetUser = in.readUTF();

                            if("CMD_UPDATE_PHOTO_LARGE".equals(targetUser)) {
                                int len = in.readInt();
                                byte[] data = new byte[len];
                                in.readFully(data);
                                String base64 = new String(data, "UTF-8");
                                DataBase.updateProfileImage(username, base64);

                                System.out.println("Profile image updated for user " + username + " successfully!");
                                break;
                            }

                            if("STOP_LISTENING".equals(targetUser)) {
                                out.writeUTF("ACK_STOP");
                                break;
                            }

                            ArrayList<String> usernames = DataBase.getUsernames();

                            if (!usernames.contains(targetUser)) {
                                out.writeUTF("There is no user with this username.");
                            } else {
//                                out.writeUTF("Attempting to connect with " + targetUser);
//                                out.writeUTF("Connected to " + targetUser);

                                if(isAndroid) {
                                    ArrayList<String> history = History.getChatHistory(username, targetUser);
                                    out.writeUTF("CHAT_HISTORY");
                                    out.writeInt(history.size());
                                    for(String historyEntry : history) {
                                        out.writeUTF(historyEntry);
                                    }
                                    out.flush();
                                } else {
                                    out.writeUTF("Attempting to connect with " + targetUser);
                                    out.writeUTF("Connected to " + targetUser);
                                }

                                Thread fileReceiverThread = new Thread(new FileReceiver(chatServer, username, targetUser));
                                fileReceiverThread.setDaemon(true);
                                fileReceiverThread.start();

                                updateState(ClientState.CHAT);
                            }
                            break;
                        }

                        case CHAT:
                            System.out.println("CHAT STATE");
                                String inputMessage = in.readUTF();

                                if (inputMessage == null) {
                                    break;
                                }

                                switch (inputMessage) {

                                    case "BACK_ANDROID":
                                        isAndroid = true;
                                        out.writeUTF("ACK_BACK");
                                        updateState(ClientState.LOGGED_IN);
                                        break;

                                    case "back":
                                        if(isAndroid) {
                                            // 1. Spunem aplicației Android să oprească ascultarea mesajelor din chat
                                            out.writeUTF("ACK_BACK");

                                            // 2. Mutăm serverul în starea LOGGED_IN.
                                            // Serverul va executa automat logica din LOGGED_IN și va trimite INBOX_DATA curat.
                                            updateState(ClientState.LOGGED_IN);
                                        } else {
                                            // Logica veche pentru Desktop rămâne neschimbată
                                            ArrayList<String> usernames = DataBase.getUsernames();
                                            out.writeUTF("You are now back in the connection menu.");
                                            out.writeUTF("Who do you want to connect with?: ");
                                            usernames.forEach(user -> {
                                                try {
                                                    out.writeUTF(user);
                                                } catch (IOException e) {
                                                    e.printStackTrace();
                                                }
                                            });
                                            updateState(ClientState.CONNECTING_TO_USER);
                                        }
                                        break;

                                    case "STOP_LISTENING":
                                        out.writeUTF("ACK_STOP");
                                        break;

                                    case "exit":
                                        out.writeUTF("Goodbye! Closing connection...");
                                        updateState(ClientState.EXIT);
                                        break;

                                    case "send":
                                        System.out.println("User requested to send a file.");

                                        String fileName = in.readUTF().trim();
                                        long fileSize = in.readLong();

                                        System.out.println("Receiving file: " + fileName + " (Size: " + fileSize + " bytes)");

                                        File uploadDir = new File("received_files");
                                        if (!uploadDir.exists()) {
                                            uploadDir.mkdir();
                                        }

                                        File receivedFile = new File(uploadDir, fileName);
                                        try (FileOutputStream fos = new FileOutputStream(receivedFile)) {
                                            byte[] buffer = new byte[4096];
                                            long remainingBytes = fileSize;

                                            while (remainingBytes > 0) {
                                                int bytesToRead = (int) Math.min(buffer.length, remainingBytes);
                                                int bytesRead = in.read(buffer, 0, bytesToRead);
                                                if (bytesRead == -1) break;

                                                fos.write(buffer, 0, bytesRead);
                                                remainingBytes -= bytesRead;
                                            }
                                        }

                                        System.out.println("File received and saved as: " + receivedFile.getAbsolutePath());
                                        DataBase.insertFile(username, targetUser, receivedFile.getAbsolutePath());

                                        String notificationMsg = "[IMAGE]: " + fileName;
                                        History.sendMessage(notificationMsg, username, targetUser, clientWriters);

                                        out.writeUTF("File received and saved successfully.");

                                        updateState(ClientState.CHAT);
                                        break;

                                    case "download_direct": // Comandă optimizată pentru Android
                                        try {
                                            fileName = in.readUTF().trim();
                                            File file = new File("received_files", fileName);

                                            if (file.exists() && !file.isDirectory()) {
                                                out.writeUTF("CMD_SENDING_FILE");
                                                out.writeUTF(file.getName());
                                                out.writeLong(file.length());
                                                out.flush();

                                                try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
                                                    byte[] buffer = new byte[8192];
                                                    int bytesRead;
                                                    while ((bytesRead = bis.read(buffer)) != -1) {
                                                        out.write(buffer, 0, bytesRead);
                                                    }
                                                    out.flush();
                                                }
                                                System.out.println("Auto-download reușit pentru: " + fileName);
                                            } else {
                                                out.writeUTF("FILE_NOT_FOUND");
                                                out.flush();
                                                System.out.println("Fișierul nu a fost găsit pe disc: " + fileName);
                                            }
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                        break;

                                    case "download":
                                        System.out.println("Received download request from client: " + username);
                                        ArrayList<String> files = DataBase.getDownloadableFiles(username);
                                        System.out.println(files);

                                        out.writeUTF("Files available for download: ");
                                        try {
                                            files.forEach(file -> {
                                                try {
                                                    out.writeUTF(file);
                                                    out.flush();
                                                } catch (IOException e) {
                                                    e.printStackTrace();
                                                }
                                            });
                                            out.flush();
                                            out.writeUTF("END_OF_LIST");

                                            String selectedFile = in.readUTF();
                                            System.out.println("Client selected file: " + selectedFile);

                                            File file = DataBase.getFileByName(selectedFile);

                                            if (file != null && file.exists()) {
                                                // Adăugăm logging pentru a verifica dimensiunea
                                                out.writeUTF("INCOMING_FILE");
                                                out.writeUTF(file.getName());
                                                fileSize = file.length();
                                                System.out.println("File found, preparing to send: " + file.getName());
                                                System.out.println("Actual file size: " + fileSize + " bytes");

                                                out.writeLong(fileSize);
                                                out.flush();
                                                System.out.println("Sent file metadata to client");

                                                try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
                                                    byte[] buffer = new byte[8192];
                                                    int bytesRead;
                                                    long totalBytesSent = 0;

                                                    while ((bytesRead = bis.read(buffer)) != -1) {
                                                        out.write(buffer, 0, bytesRead);
                                                        totalBytesSent += bytesRead;
                                                    }
                                                    out.flush();
                                                }
                                            } else {
                                                out.writeUTF("FILE_NOT_FOUND");
                                                out.flush();
                                                System.out.println("File not found: " + selectedFile);
                                            }
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                        break;

                                    default:
                                        if (!inputMessage.trim().isEmpty()) {
                                            sendMessageToUser(inputMessage);
                                            System.out.println("Message sent: " + inputMessage);
                                        }
                                        break;
                                }
                            break;

                        case EXIT:
                            socket.close();
                            isActive = false;
                            break;
                    }
                }
            }
            catch (EOFException | SocketException e) {
                System.out.println("Client disconnected abruptly: " + (username != null ? username : "Unknown"));
            }
            catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if(username != null) {
                        clientWriters.remove(username);
                        System.out.println("Client disconnected from user: " + username);
                    }
                    if(socket != null && socket.isClosed()) {
                        socket.close();
                    }
                    synchronized (clientWriters) {
                        clientWriters.remove(username);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}



