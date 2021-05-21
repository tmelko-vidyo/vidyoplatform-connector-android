package com.vidyo.vidyoconnector.audio.communicator;

import android.media.AudioManager;

/**
 * S8 (7.1), Tab S2(7.1, 7.0), Note 4(5.x), S5(6.0.1).
 * On the older devices sometimes Media Audio is not getting connected for BT.
 * So should check the behavior carefully.
 * <p>
 * Samsung devices don't need to set communication mode when bluetooth is connected
 * And Samsung devices don't need delay
 */
class SamsungDeviceCommunicator extends DeviceCommunicator {

    @Override
    void addDelay() {
    }

    @Override
    void setAudioModeOnBluetoothConnected(AudioManager audioManager) {
    }

    @Override
    void setAudioModeOnBluetoothDisconnected(AudioManager audioManager) {
    }
}