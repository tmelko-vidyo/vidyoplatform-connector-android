package com.vidyo.vidyoconnector.audio.communicator;

/**
 * For some devices we need to add a delay in some cases.
 */
class BluetoothDelayedDeviceCommunicator extends DefaultDeviceCommunicator {

    private static final long BLUETOOTH_ACTION_DELAY_MILLIS = 3000;

    @Override
    void addDelay() {
        try {
            Thread.sleep(BLUETOOTH_ACTION_DELAY_MILLIS);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
    }
}