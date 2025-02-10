package utilities;
import java.sql.*;

public class sendToDataBase {
    public static boolean checkIfNameExists(String name) {
        Connection con = null;
        PreparedStatement pst = null;
        ResultSet rs = null;
        boolean result = false;

        try {
            con = DataBase.connect();
            String sql = "select * from credentials where name = ?";
            pst = con.prepareStatement(sql);
            pst.setString(1, name);
            rs = pst.executeQuery();

            if(rs.next()) {
                result = true;
            }
        }
        catch(SQLException e) {
            System.out.println("Error: " + e);
        } finally {
            try {
                if(con != null) con.close();
                if(pst != null) pst.close();
                if(rs != null) rs.close();
            }
            catch(SQLException e) {
                System.out.println("Error: " + e);
            }
        }
        return result;
    }

    public static void insertCredentials(String name, String password) {
        Connection con = null;
        PreparedStatement pst = null;
        con = DataBase.connect();

        try {
            String sql = "INSERT INTO credentials (name, password) VALUES (?, ?)";

            pst = con.prepareStatement(sql);
            pst.setString(1, name);
            pst.setString(2, password);
            pst.executeUpdate();
            System.out.println("Credential inserted");
        }
        catch (Exception e) {
            System.out.println(e);
        } finally {
            try {
                if(pst != null) pst.close();
                if(con != null) con.close();
            } catch (SQLException e) {
                System.out.println(e);
            }
        }
    }
}
