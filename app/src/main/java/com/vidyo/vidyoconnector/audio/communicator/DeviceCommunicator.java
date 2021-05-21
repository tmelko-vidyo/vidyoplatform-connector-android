package com.vidyo.vidyoconnector.audio.communicator;

import android.media.AudioManager;

public abstract class DeviceCommunicator {

    private final static int NONE = 0;
    private final static int BLUETOOTH = 1;
    private final static int HEADSETS = 2;
    private AudioManager mAudioManager;
    private int mCurrentStartedMode = NONE;

    DeviceCommunicator() {
    }

    abstract void addDelay();

    abstract void setAudioModeOnBluetoothConnected(AudioManager audioManager);

    abstract void setAudioModeOnBluetoothDisconnected(AudioManager audioManager);

    public void onStartBluetoothCommunication() {
        if (mCurrentStartedMode == BLUETOOTH) {
            return;
        }
        addDelay();

        mAudioManager.setBluetoothScoOn(true);
        setAudioModeOnBluetoothConnected(mAudioManager);
        mAudioManager.startBluetoothSco();
        mAudioManager.setSpeakerphoneOn(false);

        mCurrentStartedMode = BLUETOOTH;
    }

    public void onStopBluetoothCommunication() {
        mAudioManager.setBluetoothScoOn(false);
        mAudioManager.stopBluetoothSco();
        if (mCurrentStartedMode != BLUETOOTH) {
            return;
        }

        mAudioManager.setSpeakerphoneOn(true);
        setAudioModeOnBluetoothDisconnected(mAudioManager);

        mCurrentStartedMode = NONE;
    }

    public void onStartHeadsetCommunication() {
        if (mCurrentStartedMode == HEADSETS) {
            return;
        }
        mAudioManager.setWiredHeadsetOn(true);
        mAudioManager.setSpeakerphoneOn(false);

        mCurrentStartedMode = HEADSETS;
    }

    public void onStopHeadsetCommunication() {
        if (mCurrentStartedMode == BLUETOOTH) {
            return;
        }
        mAudioManager.setWiredHeadsetOn(false);
        mAudioManager.setSpeakerphoneOn(true);

        mCurrentStartedMode = NONE;
    }

    void setAudioManager(AudioManager audioManager) {
        mAudioManager = audioManager;
    }

    public void clearState() {
        mCurrentStartedMode = NONE;
    }

    public void onRestartBluetoothCommunication() {
        onStopBluetoothCommunication();
        onStartBluetoothCommunication();
    }
}