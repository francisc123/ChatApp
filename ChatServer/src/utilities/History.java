package utilities;

import java.io.DataOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class History {
    private static final Object lock = new Object();
    private static final Map<String, String> undeliveredMessages = new ConcurrentHashMap<>();

    public static void sendMessage(String message, String username, String receiver, Map<String, DataOutputStream> clientWriters) {
        synchronized (lock) {
            Connection con = null;
            PreparedStatement pst = null;

            try {
                con = DataBase.connect();

                String sql = "INSERT INTO messages (username, message, receiver, ip_address, seen) VALUES (?, ?, ?, ?, ?)";
                pst = con.prepareStatement(sql);
                pst.setString(1, username);
                pst.setString(2, message);
                pst.setString(3, receiver);
                pst.setString(4, "ipAdress");
                pst.setInt(5, 0); 

                pst.executeUpdate();
                System.out.println("Message saved to database.");

                DataOutputStream dos = clientWriters.get(receiver);
                if (dos != null) {
                    dos.writeUTF(username + ": " + message);
                    System.out.println("Message sent to " + receiver + " in real-time.");

                    String updateSql = "UPDATE messages SET seen = 1 WHERE receiver = ? AND username = ? AND message = ?";
                    try (PreparedStatement updatePst = con.prepareStatement(updateSql)) {
                        updatePst.setString(1, receiver);
                        updatePst.setString(2, username);
                        updatePst.setString(3, message);
                        updatePst.executeUpdate();
                    }
                }

            } catch (SQLException | IOException e) {
                System.err.println("Error handling message: " + e.getMessage());
                e.printStackTrace();
            } finally {
                try {
                    if (pst != null) pst.close();
                    if (con != null) con.close();
                } catch (SQLException e) {
                    System.err.println("Error closing resources: " + e.getMessage());
                }
            }
        }
    }



    public static void processUndeliveredMessages(String username, Map<String, DataOutputStream> clientWriters) {
        synchronized (lock) {
            System.out.println("Processing undelivered messages for: " + username);
            Connection con = null;
            PreparedStatement pst = null;
            ResultSet rs = null;

            try {
                con = DataBase.connect();
                System.out.println("Connected to the database for retrieving undelivered messages.");

                String sql = "SELECT username, message FROM messages WHERE receiver = ? AND seen = 0";
                pst = con.prepareStatement(sql);
                pst.setString(1, username);
                rs = pst.executeQuery();

                DataOutputStream dos = clientWriters.get(username);
                if (dos != null) {
                    while (rs.next()) {
                        String sender = rs.getString("username");
                        String message = rs.getString("message");

                        dos.writeUTF(sender + ": " + message);
                        System.out.println("Delivered undelivered message from " + sender + " to " + username);
                    }

                    String updateSql = "UPDATE messages SET seen = 1 WHERE receiver = ?";
                    try (PreparedStatement updatePst = con.prepareStatement(updateSql)) {
                        updatePst.setString(1, username);
                        updatePst.executeUpdate();
                    }
                }

            } catch (SQLException | IOException e) {
                System.err.println("Error processing undelivered messages: " + e.getMessage());
                e.printStackTrace();
            } finally {
                try {
                    if (rs != null) rs.close();
                    if (pst != null) pst.close();
                    if (con != null) con.close();
                    System.out.println("Database connection closed after processing undelivered messages.");
                } catch (SQLException e) {
                    System.err.println("Error closing database resources: " + e.getMessage());
                }
            }
        }
    }

}
