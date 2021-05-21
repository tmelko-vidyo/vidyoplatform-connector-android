package com.vidyo.vidyoconnector.audio.communicator;

import android.media.AudioManager;

/**
 * For most devices we need to
 * - set audio mode when bluetooth is connected by
 * audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
 * <p>
 * - set audio mode back to normal when bluetooth is disconnected
 * audioManager.setMode(AudioManager.MODE_NORMAL);
 * <p>
 * Method addDelay here is doing nothing
 * <p>
 * LG Nexus 5X(7.1.2, 8.0), Motorola Moto X2(6.0), Motorola Nexus 6(7.1), Sony Xperia Z3(6.0), Google Pixel 2XL(8.0)
 */
class DefaultDeviceCommunicator extends DeviceCommunicator {

    @Override
    void addDelay() {
    }

    @Override
    void setAudioModeOnBluetoothConnected(AudioManager audioManager) {
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
    }

    @Override
    void setAudioModeOnBluetoothDisconnected(AudioManager audioManager) {
        audioManager.setMode(AudioManager.MODE_NORMAL);
    }
}