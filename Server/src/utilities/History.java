package utilities;

import javax.xml.crypto.Data;
import java.io.DataOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
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

                // Inserează mesajul în baza de date indiferent dacă utilizatorul e online sau nu
                String sql = "INSERT INTO messages (username, message, receiver, ip_address, seen) VALUES (?, ?, ?, ?, ?)";
                pst = con.prepareStatement(sql);
                pst.setString(1, username);
                pst.setString(2, message);
                pst.setString(3, receiver);
                pst.setString(4, "ipAdress");
                pst.setInt(5, 0); // Inițial mesajul este nevăzut

                pst.executeUpdate();
                System.out.println("Message saved to database.");

                // Dacă destinatarul este online, trimite-i mesajul și marchează-l ca văzut
                DataOutputStream dos = clientWriters.get(receiver);
                if (dos != null) {
//                    dos.writeUTF(username + ": " + message);
                    dos.writeUTF("MSG_LIVE|" + username + "|" + message);
                    System.out.println("Message sent to " + receiver + " in real-time.");

                    // Marchează mesajele ca "văzute" imediat
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

    public static ArrayList<String> getChatHistory(String myUsername, String otherUser) {
        ArrayList<String> history = new ArrayList<>();

        // 1. Luăm istoricul (Codul vechi)
        String sql = "SELECT username, message FROM messages " +
                "WHERE (username = ? AND receiver = ?) OR (username = ? AND receiver = ?) " +
                "ORDER BY id ASC";

        try(Connection conn = DataBase.connect();
            PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, myUsername);
            pstmt.setString(2, otherUser);
            pstmt.setString(3, otherUser);
            pstmt.setString(4, myUsername);

            ResultSet rs = pstmt.executeQuery();

            while(rs.next()) {
                history.add(rs.getString("username") + "|" +  rs.getString("message"));
            }

            // --- PARTEA NOUĂ: Marcăm mesajele primite de la "otherUser" ca văzute ---
            // Deoarece tocmai le-am descărcat pe ecran
            String updateSql = "UPDATE messages SET seen = 1 WHERE username = ? AND receiver = ? AND seen = 0";
            try (PreparedStatement updatePst = conn.prepareStatement(updateSql)) {
                updatePst.setString(1, otherUser);  // Expeditorul (Prietenul)
                updatePst.setString(2, myUsername); // Destinatarul (Eu)
                updatePst.executeUpdate();
                // System.out.println("Marked messages from " + otherUser + " as seen.");
            }
            // ------------------------------------------------------------------------

        } catch (SQLException e) {
            System.err.println("Error getting chat history: " + e.getMessage());
        }
        return history;
    }

    // Nu mai avem nevoie de friend1, friend2 etc. Doar username-ul tau.
    public static void processUndeliveredMessages(String myUsername, Map<String, DataOutputStream> clientWriters) {
        synchronized (lock) {
            System.out.println("Checking inbox for: " + myUsername);
            Connection con = null;
            PreparedStatement pst = null;
            ResultSet rs = null;

            try {
                con = DataBase.connect();

                // 1. SELECT GENERIC
                // Această interogare ia TOATE mesajele necitite, indiferent dacă ai 1 sau 1000 de prieteni care ți-au scris.
                String sql = "SELECT username, message FROM messages WHERE receiver = ? AND seen = 0 ORDER BY id ASC";

                pst = con.prepareStatement(sql);
                pst.setString(1, myUsername); // Setăm un singur parametru: TU.

                rs = pst.executeQuery();

                DataOutputStream dos = clientWriters.get(myUsername);

                if (dos != null) {
                    // Iterăm prin rezultate.
                    // 'rs.getString("username")' ne va spune cine a trimis fiecare mesaj (Ion, Maria, etc.)
                    boolean foundMessages = false;
                    while (rs.next()) {
                        String sender = rs.getString("username"); // Cine a trimis
                        String message = rs.getString("message"); // Ce a trimis

                        // Trimitem la client formatat: "Popescu: Salut!"
                        dos.writeUTF(sender + ": " + message);
                        System.out.println("Delivered message from " + sender);
                        foundMessages = true;
                    }

                    if (foundMessages) {
                        // 2. UPDATE GENERIC
                        // Marcăm tot ce era necitit pentru tine ca fiind citit
                        String updateSql = "UPDATE messages SET seen = 1 WHERE receiver = ? AND seen = 0";
                        try (PreparedStatement updatePst = con.prepareStatement(updateSql)) {
                            updatePst.setString(1, myUsername);
                            updatePst.executeUpdate();
                            System.out.println("Marked all pending messages as seen.");
                        }
                    }
                }

            } catch (SQLException | IOException e) {
                System.err.println("Error processing inbox: " + e.getMessage());
                e.printStackTrace();
            } finally {
                // ... blocul finally pentru închiderea resurselor ...
                try {
                    if (rs != null) rs.close();
                    if (pst != null) pst.close();
                    if (con != null) con.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static ArrayList<String> lastMessageFromEveryone(String username) {
        ArrayList<String> dataList = new ArrayList<>();
        synchronized (lock) {
            Connection con = null;
            PreparedStatement pst = null;
            ResultSet rs = null;
            try {
                con = DataBase.connect();
                // Interogarea principală rămâne neschimbată
                String sql = "SELECT (CASE WHEN m.username = ? THEN m.receiver ELSE m.username END) AS contact_name, " +
                        "m.message " +
                        "FROM messages m " +
                        "WHERE m.id IN ( " +
                        "    SELECT MAX(id) " +
                        "    FROM messages " +
                        "    WHERE username = ? OR receiver = ? " +
                        "    GROUP BY CASE WHEN username = ? THEN receiver ELSE username END " +
                        ") " +
                        "ORDER BY m.id DESC";

                pst = con.prepareStatement(sql);
                pst.setString(1, username);
                pst.setString(2, username);
                pst.setString(3, username);
                pst.setString(4, username);
                rs = pst.executeQuery();

                while (rs.next()) {
                    String name = rs.getString("contact_name");
                    String msg = rs.getString("message");

                    int unread = 0;
                    try (PreparedStatement psCount = con.prepareStatement(
                            "SELECT COUNT(*) FROM messages WHERE username = ? AND receiver = ? AND seen = 0")) {
                        psCount.setString(1, name);
                        psCount.setString(2, username);
                        ResultSet rsCount = psCount.executeQuery();
                        if (rsCount.next()) {
                            unread = rsCount.getInt(1);
                        }
                        rsCount.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    String contactImage = DataBase.getProfileImage(name);

                    dataList.add(name + "|" + msg + "|" + unread + "|" + contactImage);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (rs != null) rs.close();
                    if (pst != null) pst.close();
                    if (con != null) con.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
        return dataList;
    }
}
