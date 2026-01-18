package utilities;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.sql.*;
import javax.swing.*;
import java.nio.file.Paths;

public class DataBase {
    private static final String URL = "jdbc:mysql://localhost:3306/ChatDataBase";
    private static final String USER = "root";
    private static final String PASSWORD = "pass";
    private static Connection connection = null;

    public static Connection connect() {
        try {
            // Încarcă driverul MySQL
            Class.forName("com.mysql.cj.jdbc.Driver");
            // Creează o nouă conexiune
            connection = DriverManager.getConnection(URL, USER, PASSWORD);
            //System.out.println("Connection established!");
        } catch (ClassNotFoundException e) {
            System.err.println("MySQL JDBC driver not found!");
            e.printStackTrace();
        } catch (SQLException e) {
            System.err.println("Error connecting to the database!");
            e.printStackTrace();
        }
        return connection;
    }

    public static void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
                connection = null;
                System.out.println("The connection has been closed!");
            } catch (SQLException e) {
                System.err.println("Error closing connection!");
                e.printStackTrace();
            }
        }
    }

    public static void updateProfileImage(String username, String base64Image) {
        String sql = "UPDATE credentials SET profile_image = ? WHERE name = ?";
        try(Connection conn = connect();
            PreparedStatement pst = conn.prepareStatement(sql)) {

            pst.setString(1, base64Image);
            pst.setString(2, username);
            pst.executeUpdate();
            System.out.println("Profile image updated for user " + username);

        }catch (SQLException e){
            System.err.println("Error updating profile image!" + e.getMessage());
            e.printStackTrace();
        }
    }

    public static String getProfileImage(String username) {
        String sql = "SELECT profile_image FROM credentials WHERE name = ?";
        try(Connection conn = connect();
            PreparedStatement pst = conn.prepareStatement(sql)) {

            pst.setString(1, username);
            ResultSet rs = pst.executeQuery();

            if (rs.next()) {
                String img = rs.getString("profile_image");
                return (img != null) ? img : "";
            }

        } catch (SQLException e) {
            System.err.println("Error getting profile image for user " + username + " error: " + e.getMessage());
            e.printStackTrace();
        }
        return "";
    }

    public static ArrayList<String> getUsernames() {
        ArrayList<String> userNames = new ArrayList<>();
        String sql = "SELECT name FROM credentials";

        try (Connection conn = connect();
             PreparedStatement pst = conn.prepareStatement(sql);
             ResultSet rs = pst.executeQuery()) {

            while (rs.next()) {
                userNames.add(rs.getString("name"));
            }
        } catch (SQLException e) {
            System.err.println("Error getting usernames!");
            e.printStackTrace();
        }
        return userNames;
    }

    public static ArrayList<String> getDownloadableFiles(String username) {
        ArrayList<String> downloadableFiles = new ArrayList<>();
        String sql = "SELECT fileName FROM files2 WHERE receiver = ? OR sender = ?";
        Connection conn = null;
        PreparedStatement pst = null;
        ResultSet rs = null;

        try {
            conn = connect();
            pst = conn.prepareStatement(sql);
            pst.setString(1, username); // Setează parametrul corect
            pst.setString(2, username);

            rs = pst.executeQuery();
            while (rs.next()) {
                downloadableFiles.add(rs.getString("fileName"));
            }
        } catch (SQLException e) {
            System.err.println("Error getting downloadable files!");
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) rs.close();
                if (pst != null) pst.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return downloadableFiles;
    }

    public static void insertFile(String sender, String receiver, String filePath) {
        if (filePath == null) {
            System.err.println("No file selected!");
            return;
        }

        String sql = "INSERT INTO files2 (fileName, fileExtension, size, filePath, sender, receiver) VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = connect();
             PreparedStatement pst = conn.prepareStatement(sql)) {

            File file = new File(filePath);
            String fileName = file.getName();
            String fileType = fileName.substring(fileName.lastIndexOf(".") + 1);

            // Citește fișierul ca InputStream
            FileInputStream fis = new FileInputStream(file);

            // Obține dimensiunea fișierului în bytes
            long sizeInBytes = file.length();

            pst.setString(1, fileName);
            pst.setString(2, fileType);
            pst.setLong(3, sizeInBytes);
            pst.setString(4, "C:\\Users\\franc\\Desktop\\ChatServer\\received_files\\" + fileName);
              // Folosim direct bytes în loc de MB
            pst.setString(5, sender);
            pst.setString(6, receiver);

            int rowsAffected = pst.executeUpdate();
            System.out.println(rowsAffected + " file(s) inserted successfully!");
            fis.close();

        } catch (SQLException e) {
            System.err.println("Error inserting file!");
            e.printStackTrace();
        } catch (IOException e) {
            System.err.println("Error reading file!");
            e.printStackTrace();
        }
    }

    public static File getFileByName(String fileName) {
        String sql = "SELECT id, fileName, filePath FROM files2 WHERE fileName = ?";

        try (Connection conn = connect();
             PreparedStatement pst = conn.prepareStatement(sql)) {

            pst.setString(1, fileName);
            ResultSet rs = pst.executeQuery();

            if (rs.next()) {
                int fileId = rs.getInt("id");
                String folderPath = rs.getString("filePath");
                System.out.println("Folder path from DB: " + folderPath);

                // Construiește calea completă către fișier
                // Folosește File.separator pentru compatibilitate cross-platform
                String fullPath = folderPath + File.separator + fileName;

                File file = new File(fullPath);
                if (file.exists()) {
                    System.out.println("File exists at: " + file.getAbsolutePath());
                    return file;
                } else {

                    // Încearcă și în directorul curent al serverului
                    File alternativePath = new File("received_files" + File.separator + fileName);
                    if (alternativePath.exists()) {
                        System.out.println("File found in alternative location: " + alternativePath.getAbsolutePath());
                        return alternativePath;
                    }

                    System.out.println("File not found in any location");
                    return null;
                }
            } else {
                System.out.println("No database entry found for file: " + fileName);
                return null;
            }

        } catch (SQLException e) {
            System.err.println("Database error while searching for file: " + fileName);
            e.printStackTrace();
        }
        return null;
    }

    // Adaugă și o metodă pentru a verifica dacă un fișier există și are permisiuni de citire:
    private static boolean isFileAccessible(File file) {
        try {
            return file.exists() && file.canRead() && file.isFile();
        } catch (SecurityException e) {
            System.err.println("Security error checking file: " + e.getMessage());
            return false;
        }
    }


    public static void downloadFile(String fileName, Socket clientSocket) {
        String sql = "SELECT filename, file_type, file_data, size FROM files2 WHERE filename = ?";

        try (Connection conn = connect();
             PreparedStatement pst = conn.prepareStatement(sql)) {

            pst.setString(1, fileName);  // Folosește numele fișierului în loc de ID
            ResultSet rs = pst.executeQuery();

            if (rs.next()) {
                String fileNameFromDb = rs.getString("filename");
                InputStream input = rs.getBinaryStream("file_data");
                long fileSize = rs.getLong("size");

                // Obține fluxul de ieșire pentru a trimite fișierul la client
                OutputStream output = clientSocket.getOutputStream();

                // Trimite numele fișierului și dimensiunea
                DataOutputStream dataOutput = new DataOutputStream(output);
                dataOutput.writeUTF(fileNameFromDb);   // Trimite numele fișierului
                dataOutput.writeLong(fileSize);        // Trimite dimensiunea fișierului

                // Trimite fișierul în bucăți
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                }
                output.flush();  // Asigură-te că datele sunt trimise complet

                input.close();
                System.out.println("File " + fileNameFromDb + " has been sent to the client.");
            } else {
                System.err.println("File " + fileName + " not found.");
            }

        } catch (SQLException e) {
            System.err.println("Error downloading file!");
            e.printStackTrace();
        } catch (IOException e) {
            System.err.println("Error sending file to client!");
            e.printStackTrace();
        }
    }


    public static void main(String[] args) {
        connect();
        //insertFile(); // Testează inserarea unui fișier
        //closeConnection();
    }
}
