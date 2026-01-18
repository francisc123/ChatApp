package com.example.chat;

public class User {
    public String name;
    public String lastMessage;
    public int unreadCount;
    public String profileImageBase64;

    public User(String name, String lastMessage, int unreadCount, String profileImageBase64) {
        this.name = name;
        this.lastMessage = lastMessage;
        this.unreadCount = unreadCount;
        this.profileImageBase64 = profileImageBase64;
    }

    public String getName() {
        return name;
    }

    public String getLastMessage() {
        return lastMessage;
    }
}
