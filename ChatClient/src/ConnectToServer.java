import javax.swing.*;
import java.io.*;
import java.net.*;

public class ConnectToServer {
    private static final String SERVER_ADDRESS = "192.168.1.50";
    private static final int SERVER_PORT = 12345;
    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;

    public static void main(String[] args) {
        new ConnectToServer().start();
    }

    public void start() {
        try {
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            socket.setKeepAlive(true);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Closing connection...");
                closeResources();
            }));

            new Thread(new ServerListener()).start();
            new Thread(new UserInputHandler()).start();
        } catch (IOException e) {
            System.err.println("Error while starting the connection: " + e.getMessage());
            e.printStackTrace();
        }
    }

    class UserInputHandler implements Runnable {
        boolean isConnected = false;

        public void run() {
            try (BufferedReader userInputReader = new BufferedReader(new InputStreamReader(System.in))) {
                while (true) {
                    String userInput = userInputReader.readLine();
                    if ("1".equals(userInput) || "2".equals(userInput)) {
                        isConnected = true;
                    }
                    if ("send".equals(userInput) && isConnected) {
                        out.writeUTF("send");
                        sendFile();
                    } else if ("download".equals(userInput)) {
                        out.writeUTF("download");
                        //setDownloadModeOn();
                    } else if (userInput != null && !userInput.isEmpty()) {
                        out.writeUTF(userInput);
                        out.flush();
                    }
                }
            } catch (IOException e) {
                System.err.println("Error while handling user input: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public void receiveFile() {
        try {
            String fileName = ServerListener.getFileName();
            long fileSize = ServerListener.getFileSize();
            byte[] fileContent = ServerListener.getFileContent();

            if (fileName == null || fileContent == null) {
                System.err.println("No file data available.");
                return;
            }

            String home = System.getProperty("user.home");
            File downloadsDir = new File(home, "Downloads");
            if (!downloadsDir.exists()) downloadsDir.mkdir();

            File file = new File(downloadsDir, fileName);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(fileContent);
            }

            System.out.println("File received and saved as: " + file.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Error receiving file: " + e.getMessage());
        }
    }

    public void sendFile() {
        try {
            String filePath = chooseFile();
            if (filePath.isEmpty()) return;

            File file = new File(filePath);
            String fileName = file.getName();

            out.writeUTF(fileName);
            out.flush();
            out.writeLong(file.length());
            out.flush();

            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }

            out.flush();
            System.out.println("File sent successfully: " + fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    class ServerListener implements Runnable {
        private static String fileName;
        private static long fileSize;
        private static byte[] fileContent;

        public static String getFileName() {
            return fileName;
        }

        public static long getFileSize() {
            return fileSize;
        }

        public static byte[] getFileContent() {
            return fileContent;
        }

        public void run() {
            try {
                while (true) {
                    if (socket.isClosed()) {
                        System.out.println("Socket is closed, cannot receive data.");
                        break;
                    }

                    String serverMessage = in.readUTF();
                    if (serverMessage == null) {
                        System.out.println("Server has closed the connection.");
                        break;
                    }

                    System.out.println(serverMessage);

                    if (serverMessage.equals("END_OF_LIST")) {
                        fileName = in.readUTF();
                        fileSize = in.readLong();;
                        fileContent = new byte[(int) fileSize];
                        in.readFully(fileContent);

                        receiveFile();
                        SwingUtilities.invokeLater(() -> receiveFile());
                    }
                }
            } catch (IOException e) {
                System.err.println("Error while listening to server: " + e.getMessage());
                e.printStackTrace();
            } finally {
                closeResources();
            }
        }
    }

    private synchronized void closeResources() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
            System.out.println("Resources closed successfully.");
        } catch (IOException e) {
            System.err.println("Error while closing resources: " + e.getMessage());
        }
    }

    public static String chooseFile() {
        JFileChooser chooser = new JFileChooser();
        int result = chooser.showOpenDialog(null);
        File file = null;
        if (result == JFileChooser.APPROVE_OPTION) {
            file = chooser.getSelectedFile();
        }
        return file != null ? file.getAbsolutePath() : "";
    }
}
