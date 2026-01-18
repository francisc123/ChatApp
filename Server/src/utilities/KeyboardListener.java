package utilities;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import utilities.*;

public class KeyboardListener {
    private ChatServer chatServer; // Instanță a serverului
    private Socket currentSocket; // Referință la socketul clientului

    // Constructor care primește serverul și socketul clientului
    public KeyboardListener(ChatServer chatServer, Socket currentSocket) {
        this.chatServer = chatServer;
        this.currentSocket = currentSocket;
    }

    public void listenForKeyPress() {
        // Creăm un thread pentru a asculta inputul de la utilizator
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
                while (true) {
                    String input = reader.readLine(); // Citim inputul utilizatorului

                    // Verificăm dacă inputul este un număr
                    if (input.matches("\\d+")) {
                        int command = Integer.parseInt(input);

                        switch (command) {
                            case 1:
                                // Schimbăm starea pentru a reveni la meniul de conectare
                                chatServer.updateState(currentSocket, ChatServer.ClientState.CONNECTING_TO_USER);
                                System.out.println("Te-ai întors în meniul de conectare cu alte persoane.");
                                break;

                            case 2:
                                // Schimbăm starea pentru a ieși din aplicație
                                chatServer.updateState(currentSocket, ChatServer.ClientState.EXIT);
                                System.out.println("Aplicația va fi închisă.");
                                return; // Ieșim din bucla de ascultare



                            default:
                                System.out.println("Comandă necunoscută. Introdu doar 1 sau 2.");
                                break;
                        }
                    } else {
                        // Dacă nu este un număr, tratăm inputul ca mesaj text
                        System.out.println("Mesaj trimis: " + input);
                        chatServer.updateState(currentSocket, ChatServer.ClientState.CHAT);
                        // Poți adăuga logica pentru a trimite acest mesaj altui utilizator
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}
