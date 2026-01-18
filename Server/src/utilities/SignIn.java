package utilities;
import java.sql.*;

public class SignIn {
    public static boolean Credentials(String name, String password) {
        Connection con = null;
        PreparedStatement pst = null;
        ResultSet rs = null;
        boolean isValid = false;

        try {
            con = DataBase.connect();
            String sql = "SELECT * FROM credentials WHERE name = ? AND password = ?";

            pst = con.prepareStatement(sql);
            pst.setString(1, name);
            pst.setString(2, password);
            rs = pst.executeQuery();

            if(rs.next()) {
                isValid = true;
            }
        }
        catch (SQLException e) {
            System.out.println(e);
        } finally {
            try {
                if(pst != null) pst.close();
                if(con != null) con.close();
                if(rs != null) rs.close();
            }
            catch (SQLException e) {
                System.out.println(e);
            }
        }
        return isValid;
    }
}
