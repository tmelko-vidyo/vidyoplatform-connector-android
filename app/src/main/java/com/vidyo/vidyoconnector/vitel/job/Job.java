package com.vidyo.vidyoconnector.vitel.job;

import com.vidyo.vidyoconnector.utils.Logger;

public abstract class Job implements Runnable {

    @Override
    public void run() {
    }

    public void onThrow(Exception ex) {
        if (ex != null) {
            Logger.e("Exception during job executable: " + (ex.getMessage() != null ? ex.getMessage() : "NULL"));
            if (!shouldMuteException()) ex.printStackTrace();
        }
    }

    public boolean shouldMuteException() {
        return false;
    }

    public void onCanceled() {
        /* Further implementation required */
    }

    protected void printThreadInfo(String prefix) {
        Thread thread = Thread.currentThread();
        Logger.i("ThreadInfo. %1s. Name: %2s, Id: %d", prefix, thread.getName(), thread.getId());
    }
}