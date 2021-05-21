package com.vidyo.vidyoconnector.audio;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Handler;

import com.vidyo.vidyoconnector.audio.communicator.DeviceCommunicator;
import com.vidyo.vidyoconnector.audio.communicator.DeviceCommunicatorStrategy;

import java.util.List;

public class BluetoothManager {

    private enum LastConnectedDevice {
        NONE,
        HEADSET,
        BLUETOOTH
    }

    private LastConnectedDevice mLastConnectedDevice = LastConnectedDevice.NONE;

    private AudioManager mAudioManager;
    private BluetoothAdapter mBluetoothAdapter;
    private WiredHeadsetReceiver mWiredHeadsetReceiver;

    private final Handler mMainThreadHandler = new Handler();

    private boolean mBluetoothDeviceIsConnectedToAndroidDevice;
    private boolean mHeadsetsAreConnectedToAndroidDevice;

    private BluetoothHeadset mBluetoothHeadsetService;
    private BluetoothProfile.ServiceListener mHeadsetProfileListener;

    private final Context mContext;

    private static BluetoothManager mBluetoothManager;

    private DeviceCommunicator mDeviceCommunicator;

    private BluetoothManager(Context context) {
        mContext = context;

        if (context != context.getApplicationContext()) {
            throw new IllegalArgumentException("BluetoothManager should be initialized only with application context, existing context " + context);
        }

        startBluetoothManager(mContext);
    }

    private void startBluetoothManager(Context context) {
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mDeviceCommunicator = DeviceCommunicatorStrategy.create(mAudioManager);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter != null) {
            registerBluetoothHeadsetEventsReceiver();
        }

        setupBluetoothListeners();
        registerHeadsetReceiver();
    }

    public static void initBluetoothManager(Context context) {
        if (mBluetoothManager == null) {
            mBluetoothManager = new BluetoothManager(context);
        }
    }

    private void bluetoothAudioStateDisconnected() {
        if (mLastConnectedDevice == LastConnectedDevice.BLUETOOTH) {
            mMainThreadHandler.postDelayed(mRestartBluetoothOnDisconnectAudioRunnable, 1000);
        }
    }

    private void setupBluetoothListeners() {
        if (mAudioManager.isBluetoothScoAvailableOffCall()) {

            mHeadsetProfileListener = new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceDisconnected(int profile) {
                    if (mBluetoothDeviceIsConnectedToAndroidDevice) {
                        mDeviceCommunicator.onStopBluetoothCommunication();
                    }
                    mBluetoothDeviceIsConnectedToAndroidDevice = false;

                    if (mHeadsetsAreConnectedToAndroidDevice) {
                        mLastConnectedDevice = LastConnectedDevice.HEADSET;
                    } else {
                        mLastConnectedDevice = LastConnectedDevice.NONE;
                    }
                }

                @Override
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    // Get bluetooth profile
                    mBluetoothHeadsetService = (BluetoothHeadset) proxy; // BluetoothHeadset is derived from BluetoothProfile

                    /*
                     * if headset is connected before the application starts then
                     * ACTION_CONNECTION_STATE_CHANGED will not be broadcast
                     * so we need to check for already connected headset
                     */
                    List<BluetoothDevice> devices = mBluetoothHeadsetService.getConnectedDevices();

                    if (devices.size() > 0) {
                        // only one headset can be connected at a time, so the connected headset is at index 0

                        int state = mBluetoothHeadsetService.getConnectionState(devices.get(0));
                        if (state == BluetoothProfile.STATE_CONNECTED) {

                            bluetoothDeviceStateConnected();

                        }
                    }
                }
            };
            //  Attach the mHeadsetProfileListener to the mBluetoothAdapter
            mBluetoothAdapter.getProfileProxy(mContext, mHeadsetProfileListener, BluetoothProfile.HEADSET);
        }
    }

    private void bluetoothDeviceStateDisconnected() {
        unregisterBluetoothAudioStateEventsReceiver();
        mBluetoothDeviceIsConnectedToAndroidDevice = false;

        if (mHeadsetsAreConnectedToAndroidDevice) {
            mLastConnectedDevice = LastConnectedDevice.HEADSET;
        } else {
            mLastConnectedDevice = LastConnectedDevice.NONE;
        }

        mMainThreadHandler.removeCallbacks(mRestartBluetoothOnDisconnectAudioRunnable);
        mDeviceCommunicator.onStopBluetoothCommunication();
    }

    private void bluetoothDeviceStateConnected() {
        registerBluetoothAudioStateEventsReceiver();

        mLastConnectedDevice = LastConnectedDevice.BLUETOOTH;
        mBluetoothDeviceIsConnectedToAndroidDevice = true;

        if (mHeadsetsAreConnectedToAndroidDevice) {
            mDeviceCommunicator.onStopHeadsetCommunication();
        }

        mDeviceCommunicator.onStartBluetoothCommunication();
    }

    private void registerBluetoothHeadsetEventsReceiver() {
        mContext.registerReceiver(mBluetoothHeadsetChangeReceiver, new IntentFilter(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED));
    }

    private void registerBluetoothAudioStateEventsReceiver() {
        mContext.registerReceiver(mBluetoothAudioChangeReceiver, new IntentFilter(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED));
    }

    private void registerHeadsetReceiver() {
        mWiredHeadsetReceiver = new WiredHeadsetReceiver();
        mContext.registerReceiver(mWiredHeadsetReceiver, new IntentFilter(Intent.ACTION_HEADSET_PLUG));
    }

    private void unregisterBluetoothAudioStateEventsReceiver() {
        mContext.unregisterReceiver(mBluetoothAudioChangeReceiver);
    }

    private void headphonesConnected() {
        mLastConnectedDevice = LastConnectedDevice.HEADSET;

        mHeadsetsAreConnectedToAndroidDevice = true;

        if (mBluetoothDeviceIsConnectedToAndroidDevice) {
            mDeviceCommunicator.onStopBluetoothCommunication();
        }
        mDeviceCommunicator.onStartHeadsetCommunication();
    }

    private void headphonesDisconnected() {
        mHeadsetsAreConnectedToAndroidDevice = false;

        if (mBluetoothDeviceIsConnectedToAndroidDevice) {
            mLastConnectedDevice = LastConnectedDevice.BLUETOOTH;
        } else {
            mLastConnectedDevice = LastConnectedDevice.NONE;
        }

        mDeviceCommunicator.onStopHeadsetCommunication();
        if (mBluetoothDeviceIsConnectedToAndroidDevice) {
            mDeviceCommunicator.onStartBluetoothCommunication();
        }
    }

    private final BroadcastReceiver mBluetoothHeadsetChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();
            if (!BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED.equals(action)) {
                throw new IllegalArgumentException("wrong action received, action[" + action + "]");
            }

            handleBluetoothHeadsetConnectionStateChanged(intent);
        }
    };

    private void handleBluetoothHeadsetConnectionStateChanged(Intent intent) {
        int currentState = intent.getExtras().getInt("android.bluetooth.profile.extra.STATE");

        switch (currentState) {
            case BluetoothProfile.STATE_CONNECTED:

                bluetoothDeviceStateConnected();

                break;
            case BluetoothProfile.STATE_DISCONNECTED:

                if (mBluetoothDeviceIsConnectedToAndroidDevice) {
                    bluetoothDeviceStateDisconnected();
                }

                break;
        }
    }

    private final BroadcastReceiver mBluetoothAudioChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (!BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED.equals(action)) {
                throw new IllegalArgumentException("wrong action received, action[" + action + "]");
            }

            handleBluetoothHeadsetAudioStateChanged(intent);
        }
    };

    private void handleBluetoothHeadsetAudioStateChanged(Intent intent) {
        int currentState = intent.getExtras().getInt(BluetoothProfile.EXTRA_STATE);

        switch (currentState) {
            case BluetoothHeadset.STATE_AUDIO_CONNECTED:

                break;
            case BluetoothHeadset.STATE_AUDIO_DISCONNECTED:
                bluetoothAudioStateDisconnected();
                break;
        }
    }

    private class WiredHeadsetReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            switch (action) {
                case Intent.ACTION_HEADSET_PLUG:
                    int state = intent.getIntExtra("state", -1);

                    /**
                     * See {@link Intent#ACTION_HEADSET_PLUG}
                     * <li><em>state</em> - 0 for unplugged, 1 for plugged. </li>
                     */
                    switch (state) {
                        case 0:
                            headphonesDisconnected();
                            break;
                        case 1:
                            headphonesConnected();
                            break;
                    }
                    break;
            }
        }
    }

    private final Runnable mRestartBluetoothOnDisconnectAudioRunnable = new Runnable() {
        @Override
        public void run() {
            mDeviceCommunicator.onRestartBluetoothCommunication();
        }
    };
}