package com.vidyo.vidyoconnector.vitel.request;

import android.content.Context;

import androidx.lifecycle.Observer;

import com.vidyo.vidyoconnector.utils.Logger;

public class CreateRoomManager {

    private final RequestManager requestManager;
    private Room room;

    private static final int MAX_LOOPS = 100;

    private int attempt = 1;

    public CreateRoomManager(Context c) {
        requestManager = new RequestManager(c);
    }

    public void create(Observer<Room> callback) {
        if (attempt == MAX_LOOPS) {
            release();
            return;
        }

        Logger.i("Start attempt: #%d", attempt);
        long start = System.currentTimeMillis();
        requestManager.createRoom(new RequestManager.RequestCallback<Room>() {
            @Override
            public void onSuccess(Room room) {
                Logger.i("OK: Room has been CREATED in " + (System.currentTimeMillis() - start) + "ms. Room: " + room);
                CreateRoomManager.this.room = room;
                callback.onChanged(room);
            }

            @Override
            public void onFailure(String reason) {
                Logger.e("Failed to create a room: " + reason);
                callback.onChanged(null);
            }
        });
    }

    public void deleteRoom(Room room, Observer<Boolean> callback) {
        long start = System.currentTimeMillis();
        requestManager.deleteRoom(room, new RequestManager.RequestCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean status) {
                if (status) {
                    room.markDeleted();
                    Logger.i("OK: Room has been DELETED in " + (System.currentTimeMillis() - start) + "ms. Room: " + room);

                    Logger.i("End attempt: #%d", attempt);
                    attempt++;

                    callback.onChanged(true);
                } else {
                    Logger.e("Room was not DELETED. Response: False");
                    callback.onChanged(false);
                }
            }

            @Override
            public void onFailure(String reason) {
                Logger.e("Failed to DELETE a room: " + reason);
                callback.onChanged(false);
            }
        });
    }

    public void release() {
        Logger.i("END of the test at attempt: %d", attempt);
        if (room != null && !this.room.isDeleted()) {
            deleteRoom(room, aBoolean -> Logger.i("Deleted room: " + aBoolean));
        } else
            requestManager.cancel();
    }
}
