package com.vidyo.vidyoconnector.vitel.request;

import androidx.annotation.NonNull;

public class Room {

    private final String name;
    private final String roomId;
    private final String roomUrl;
    private final String pin;

    private boolean deleted;

    public Room(String name, String roomId, String roomUrl, String pin) {
        this.name = name;
        this.roomId = roomId;
        this.roomUrl = roomUrl;
        this.pin = pin;
    }

    public void markDeleted() {
        this.deleted = true;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public String getName() {
        return name;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getRoomUrl() {
        return roomUrl;
    }

    public String getPin() {
        return pin;
    }

    @NonNull
    @Override
    public String toString() {
        return "Room{" +
                "name='" + name + '\'' +
                ", roomId='" + roomId + '\'' +
                ", roomUrl='" + roomUrl + '\'' +
                ", pin='" + pin + '\'' +
                '}';
    }
}
