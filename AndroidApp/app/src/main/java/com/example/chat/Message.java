package com.example.chat;

import java.io.Serializable;

public class Message implements Serializable {
    public String content;
    public boolean isSentByMe;
    public int type;
    public String fileName;
    public String filePath;

    public Message(String content, boolean isSentByMe) {
        this.content = content;
        this.isSentByMe = isSentByMe;
        this.type = 0;
    }

    public Message(String content, boolean isSentByMe, int type, String fileName, String filePath) {
        this.content = content;
        this.isSentByMe = isSentByMe;
        this.type = type;
        this.fileName = fileName;
        this.filePath = filePath;
    }

    public String getContent() {
        return content;
    }

    public boolean isSentByUser() {
        return isSentByMe;
    }
}
