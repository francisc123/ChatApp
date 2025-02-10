package utilities;

import java.io.*;
import java.net.*;
import java.sql.*;

public class FileTransfer {
    private static final String DB_URL = "jdbc:mysql://localhost:3306/chatserver";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "parola";

    public static void receiveFile(String username, String targetUser, ServerSocket serverSocket) {
        String receivedFileName = "";
        String filePath = "C:\\Users\\franc\\Desktop\\ChatServer\\received_files";

        try (Socket socket = serverSocket.accept();
             DataInputStream in = new DataInputStream(socket.getInputStream())) {

            String fileName = in.readUTF(); 
            System.out.println("Received file: " + fileName);
            receivedFileName = fileName;
            filePath = filePath + receivedFileName;

            long fileSize = in.readLong(); 
            System.out.println("File dimension: " + fileSize);

            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                byte[] buffer = new byte[4096];
                long totalBytesRead = 0;
                int bytesRead;

                while (totalBytesRead < fileSize && (bytesRead = in.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;
                }

                System.out.println("File saved as: " + receivedFileName);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
