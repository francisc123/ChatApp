package main;

import java.util.ArrayList;
import utilities.*;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        new Thread(() -> {
            ChatServer.main(args);
        }).start();

        System.out.println("Welcome to Chat Server: ");
        System.out.println("Do you have an account?");
        System.out.println("1. Yes");
        System.out.println("2. No");

        int choice = sc.nextInt();
        if (choice == 1) {
            Scanner sc1 = new Scanner(System.in);

            System.out.println("What's your name?");
            String name = sc1.nextLine();
            System.out.println("What's your password?");
            String password = sc1.nextLine();

            if(SignIn.Credentials(name, password) == true) {
                System.out.println("You have successfully logged in!");
                System.out.println("Who you want to connect with?");

                ArrayList<String> usernames = DataBase.getUsernames();
                if(!usernames.isEmpty()) {
                    System.out.println("Available users: ");
                    usernames.forEach(System.out::println);
                    sc1 = new Scanner(System.in);
                }
                else {
                    System.out.println("No users found in the database!");
                }
            }
            else {
                System.out.println("The username or password is incorrect.");
            }
        }
    }
}
