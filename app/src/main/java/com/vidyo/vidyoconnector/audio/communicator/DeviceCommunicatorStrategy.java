package com.vidyo.vidyoconnector.audio.communicator;

import android.media.AudioManager;

public class DeviceCommunicatorStrategy {

    private DeviceCommunicatorStrategy() {
    }

    public static DeviceCommunicator create(AudioManager audioManager) {
        DeviceCommunicator deviceCommunicator = detectDeviceCommunicatorBasedOnManufacturer();
        deviceCommunicator.setAudioManager(audioManager);

        return deviceCommunicator;
    }

    private static DeviceCommunicator detectDeviceCommunicatorBasedOnManufacturer() {
        @DeviceManufacturer String manufacturer = android.os.Build.MANUFACTURER;
        switch (manufacturer) {
            case DeviceManufacturer.SAMSUNG:
                return new SamsungDeviceCommunicator();
            case DeviceManufacturer.HUAWEI:
            case DeviceManufacturer.HTC:
                return new BluetoothDelayedDeviceCommunicator();
            case DeviceManufacturer.GOOGLE:
            case DeviceManufacturer.MOTOROLA:
            case DeviceManufacturer.LG:
            case DeviceManufacturer.SONY:
            default:
                return new DefaultDeviceCommunicator();
        }
    }
}