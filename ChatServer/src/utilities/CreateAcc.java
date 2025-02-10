package utilities;

public class CreateAcc {
    private static String name;
    private static String password;

    public static void setName(String username){
        name = username;
    }
    public static void setPassword(String NewPassword){
        password = NewPassword;
    }
    public static String getName(){
        return name;
    }
    public static String getPassword(){
        return password;
    }

    public static void main(String [] args) {
        if(sendToDataBase.checkIfNameExists(name) != true) {
            sendToDataBase.insertCredentials(name, password);
        }
        else {
            System.out.println("Name already exists, try another name");

        }
    }
}
