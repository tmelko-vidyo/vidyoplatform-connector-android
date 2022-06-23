package com.vidyo.vidyoconnector;

import android.content.res.Configuration;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Handler;
import android.text.format.DateUtils;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import com.vidyo.VidyoClient.Connector.Connector;
import com.vidyo.VidyoClient.Connector.ConnectorPkg;
import com.vidyo.VidyoClient.Device.Device;
import com.vidyo.VidyoClient.Device.LocalCamera;
import com.vidyo.VidyoClient.Device.LocalMicrophone;
import com.vidyo.VidyoClient.Device.LocalSpeaker;
import com.vidyo.VidyoClient.Endpoint.LogRecord;
import com.vidyo.VidyoClient.Endpoint.Participant;
import com.vidyo.vidyoconnector.event.ControlEvent;
import com.vidyo.vidyoconnector.event.IControlEventHandler;
import com.vidyo.vidyoconnector.utils.AppUtils;
import com.vidyo.vidyoconnector.utils.FontsUtils;
import com.vidyo.vidyoconnector.utils.Logger;
import com.vidyo.vidyoconnector.view.ControlView;
import com.vidyo.vidyoconnector.vitel.request.CreateRoomManager;
import com.vidyo.vidyoconnector.vitel.request.Room;

import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Conference activity holding all connection and callbacks logic.
 */
public class VideoConferenceActivity extends FragmentActivity implements Connector.IConnect,
        Connector.IRegisterLocalCameraEventListener,
        Connector.IRegisterLocalMicrophoneEventListener,
        Connector.IRegisterLocalSpeakerEventListener,
        Connector.IRegisterLogEventListener,
        IControlEventHandler, View.OnLayoutChangeListener,
        Connector.IRegisterParticipantEventListener {

    public static final String PORTAL_KEY = "portal.key";
    public static final String ROOM_KEY = "room.key";
    public static final String PIN_KEY = "pin.key";
    public static final String NAME_KEY = "name.key";

    private FrameLayout videoView;
    private ControlView controlView;
    private View progressBar;

    private Connector connector;
    private LocalCamera lastSelectedLocalCamera;

    private final AtomicBoolean isCameraDisabledForBackground = new AtomicBoolean(false);
    private final AtomicBoolean isDisconnectAndQuit = new AtomicBoolean(false);

    private static final long JOIN_MAX_TIMEOUT = 20000;
    private CreateRoomManager createRoomManager;
    private Room workingRoom = null;
    private long startConnectionAt = 0;
    private long startDisconnection = 0;
    private boolean hasFailed = false;

    @Override
    public void onStart() {
        super.onStart();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);

        if (connector != null) {
            ControlView.State state = controlView.getState();
            connector.setMode(Connector.ConnectorMode.VIDYO_CONNECTORMODE_Foreground);

            connector.setCameraPrivacy(state.isMuteCamera());
            connector.setMicrophonePrivacy(state.isMuteMic());
            connector.setSpeakerPrivacy(state.isMuteSpeaker());
        }

        if (connector != null && lastSelectedLocalCamera != null && isCameraDisabledForBackground.getAndSet(false)) {
            connector.selectLocalCamera(lastSelectedLocalCamera);
        }

        if (connector != null) {
            connector.setCameraPrivacy(true);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (connector != null) {
            connector.setMode(Connector.ConnectorMode.VIDYO_CONNECTORMODE_Background);

            connector.setCameraPrivacy(true);
            connector.setMicrophonePrivacy(true);
            connector.setSpeakerPrivacy(true);
        }

        if (!isFinishing() && connector != null && !controlView.getState().isMuteCamera() && !isCameraDisabledForBackground.getAndSet(true)) {
            connector.selectLocalCamera(null);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_conference);

        ConnectorPkg.setApplicationUIContext(this);

        videoView = findViewById(R.id.video_frame);

        progressBar = findViewById(R.id.progress);
        progressBar.setVisibility(View.GONE);

        controlView = findViewById(R.id.control_view);
        controlView.registerListener(this);

        String logLevel = "warning debug@VidyoClient all@LmiPortalSession " +
                "all@LmiPortalMembership info@LmiResourceManagerUpdates " +
                "info@LmiPace info@LmiIce all@LmiSignaling info@VidyoCameraEffect";

        createRoomManager = new CreateRoomManager(this);
        connector = new Connector(videoView, Connector.ConnectorViewStyle.VIDYO_CONNECTORVIEWSTYLE_Default, 8,
                logLevel, AppUtils.configLogFile(this), 0);
        Logger.i("Connector instance has been created.");

        FontsUtils fontsUtils = new FontsUtils(this);
        connector.setFontFileName(fontsUtils.fontFile().getPath());

        connector.setCertificateAuthorityFile(AppUtils.writeCaCertificates(this));

        controlView.showVersion(connector.getVersion());

        connector.registerLocalCameraEventListener(this);
        connector.registerLocalMicrophoneEventListener(this);
        connector.registerLocalSpeakerEventListener(this);
        connector.registerParticipantEventListener(this);

        connector.registerLogEventListener(this, logLevel);

        /* Await view availability */
        videoView.addOnLayoutChangeListener(this);

        createAndJoinDelayed(generateRandomTime());
    }

    private long generateRandomTime() {
        return 1000 + new Random().nextInt(1000);
    }

    private void createAndJoinDelayed(long ms) {
        if (hasFailed) return;

        if (this.workingRoom != null && !this.workingRoom.isDeleted()) {
            Logger.e("Room has not been deleted, Quit loop.");
            return;
        }

        Logger.i("Will CREATE room and Join after %dms delay", ms);
        new Handler().postDelayed(() -> createRoomManager.create(room -> runOnUiThread(() -> {
            if (room == null) {
                Toast.makeText(VideoConferenceActivity.this, "Failed to create a room on flight", Toast.LENGTH_SHORT).show();
                return;
            }
            this.workingRoom = room;
            onControlEvent(new ControlEvent<>(ControlEvent.Call.CONNECT_DISCONNECT, true));
        })), ms);
    }

    private void disconnectAndDelete(long ms) {
        Logger.i("Will DISCONNECT room and delete after %dms delay", ms);
        new Handler().postDelayed(() -> {
            createRoomManager.deleteRoom(this.workingRoom, aBoolean
                    -> runOnUiThread(()
                    -> onControlEvent(new ControlEvent<>(ControlEvent.Call.CONNECT_DISCONNECT, false))));
        }, ms);
    }

    @Override
    public void onLayoutChange(View view, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
        view.removeOnLayoutChangeListener(this);

        int width = view.getWidth();
        int height = view.getHeight();

        connector.showViewAt(view, 0, 0, width, height);
        Logger.i("Show View at: " + width + ", " + height);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        new Handler().postDelayed(this::updateView, DateUtils.SECOND_IN_MILLIS * 2);
    }

    @Override
    public void onSuccess() {
        long took = System.currentTimeMillis() - startConnectionAt;
        Logger.i("onSuccess. Connection took: %d ms", took);
        runOnUiThread(() -> {
            Toast.makeText(VideoConferenceActivity.this, R.string.connected, Toast.LENGTH_SHORT).show();
            progressBar.setVisibility(View.GONE);

            controlView.connectedCall(true);
            controlView.updateConnectionState(ControlView.ConnectionState.CONNECTED);
            controlView.disable(false);

            if (took > JOIN_MAX_TIMEOUT) {
                hasFailed = true;
                Logger.e("Connection took more than 20 sec. Quit test. Analyze...");
                createRoomManager.release();
                onControlEvent(new ControlEvent<>(ControlEvent.Call.CONNECT_DISCONNECT, false));
            } else
                disconnectAndDelete(generateRandomTime());
        });
    }

    @Override
    public void onFailure(final Connector.ConnectorFailReason connectorFailReason) {
        long took = System.currentTimeMillis() - startConnectionAt;
        Logger.i("onFailure: %s. With timeout: %d ms", connectorFailReason.name(), took);
        createRoomManager.release();

        if (connector != null) connector.unregisterResourceManagerEventListener();

        runOnUiThread(() -> {
            Toast.makeText(VideoConferenceActivity.this, connectorFailReason.name(), Toast.LENGTH_SHORT).show();
            progressBar.setVisibility(View.GONE);

            controlView.connectedCall(false);
            controlView.updateConnectionState(ControlView.ConnectionState.FAILED);
            controlView.disable(false);

            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        });
    }

    @Override
    public void onDisconnected(Connector.ConnectorDisconnectReason connectorDisconnectReason) {
        Logger.i("onDisconnected: %s. Took: %d ms", connectorDisconnectReason.name(), System.currentTimeMillis() - startDisconnection);
        if (connector != null) connector.unregisterResourceManagerEventListener();

        runOnUiThread(() -> {
            createAndJoinDelayed(generateRandomTime());

            Toast.makeText(VideoConferenceActivity.this, R.string.disconnected, Toast.LENGTH_SHORT).show();
            progressBar.setVisibility(View.GONE);

            controlView.connectedCall(false);
            controlView.updateConnectionState(ControlView.ConnectionState.DISCONNECTED);
            controlView.disable(false);

            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            /* Wrap up the conference */
            if (isDisconnectAndQuit.get()) {
                finish();
            }
        });
    }

    @Override
    public void onControlEvent(ControlEvent event) {
        if (connector == null) return;

        switch (event.getCall()) {
            case CONNECT_DISCONNECT:
                if (workingRoom == null) {
                    Logger.e("Failed to connect. Lack or Room Data?");
                    return;
                }

                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

                progressBar.setVisibility(View.VISIBLE);
                controlView.disable(true);
                boolean state = (boolean) event.getValue();
                controlView.updateConnectionState(state ? ControlView.ConnectionState.CONNECTING : ControlView.ConnectionState.DISCONNECTING);

                if (state) {
//                    Intent intent = getIntent();
//
//                    String portal = intent.getStringExtra(PORTAL_KEY);
//                    String room = intent.getStringExtra(ROOM_KEY);
//                    String pin = intent.getStringExtra(PIN_KEY);
//                    String name = intent.getStringExtra(NAME_KEY);

                    String portal = this.workingRoom.getRoomUrl().split("/join/")[0];
                    String room = this.workingRoom.getRoomUrl().split("/join/")[1];
                    String pin = this.workingRoom.getPin();
                    String name = "Demo Loop Tester";

                    startConnectionAt = System.currentTimeMillis();
                    Logger.i("Start connection: %s, %s, %s, %s. Started at: %d ms", portal, room, pin, name, startConnectionAt);

                    connector.connectToRoomAsGuest(portal, name, room, pin, this);
                } else {
                    startDisconnection = System.currentTimeMillis();
                    Logger.i("Execute disconnect at: %d ms", startDisconnection);

                    if (connector != null) connector.disconnect();
                }
                break;
            case MUTE_CAMERA:
                boolean cameraPrivacy = (boolean) event.getValue();
                connector.setCameraPrivacy(cameraPrivacy);

                if (cameraPrivacy) {
                    connector.selectLocalCamera(null);
                } else {
                    if (lastSelectedLocalCamera != null)
                        connector.selectLocalCamera(lastSelectedLocalCamera);
                    else
                        connector.selectDefaultCamera();
                }
                break;
            case MUTE_MIC:
                connector.setMicrophonePrivacy((boolean) event.getValue());
                break;
            case MUTE_SPEAKER:
                connector.setSpeakerPrivacy((boolean) event.getValue());
                break;
            case CYCLE_CAMERA:
                connector.cycleCamera();
                break;
            case DEBUG_OPTION:
                boolean value = (boolean) event.getValue();
                if (value) {
                    connector.enableDebug(7776, "");
                } else {
                    connector.disableDebug();
                }

                Toast.makeText(VideoConferenceActivity.this, getString(R.string.debug_option) + value, Toast.LENGTH_SHORT).show();
                break;
            case SEND_LOGS:
                AppUtils.sendLogs(this);
                break;
        }
    }

    @Override
    public void onBackPressed() {
        if (connector == null) {
            Logger.e("Connector is null!");
            finish();
            return;
        }

        Connector.ConnectorState state = connector.getState();

        if (state == Connector.ConnectorState.VIDYO_CONNECTORSTATE_Idle || state == Connector.ConnectorState.VIDYO_CONNECTORSTATE_Ready) {
            super.onBackPressed();
        } else {
            /* You are still connecting or connected */
            Toast.makeText(this, "You have to disconnect or await connection first", Toast.LENGTH_SHORT).show();

            /* Start disconnection if connected. Quit afterward. */
            if (state == Connector.ConnectorState.VIDYO_CONNECTORSTATE_Connected && !isDisconnectAndQuit.get()) {
                isDisconnectAndQuit.set(true);
                onControlEvent(new ControlEvent<>(ControlEvent.Call.CONNECT_DISCONNECT, false));
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        createRoomManager.release();

        if (controlView != null) controlView.unregisterListener();

        if (connector != null) {
            connector.unregisterLocalCameraEventListener();

            connector.hideView(videoView);

            connector.disable();
            connector = null;
        }

        ConnectorPkg.setApplicationUIContext(null);
        Logger.i("Connector instance has been released.");
    }

    @Override
    public void onLocalCameraAdded(LocalCamera localCamera) {
        if (localCamera != null) {
            Logger.i("onLocalCameraAdded: %s", localCamera.name);
        }
    }

    @Override
    public void onLocalCameraSelected(final LocalCamera localCamera) {
        if (localCamera != null) {
            Logger.i("onLocalCameraSelected: %s", localCamera.name);
            this.lastSelectedLocalCamera = localCamera;

//            localCamera.setTargetBitRate(800000);
//            localCamera.setMaxConstraint(320, 240, 1_000_000_000 / 5);
        }
    }

    @Override
    public void onLocalCameraStateUpdated(LocalCamera localCamera, Device.DeviceState deviceState) {

    }

    @Override
    public void onLocalCameraRemoved(LocalCamera localCamera) {
        if (localCamera != null) {
            Logger.i("onLocalCameraRemoved: %s", localCamera.name);
        }
    }

    @Override
    public void onLog(LogRecord logRecord) {
        /* Write log into a custom file */
    }

    private void updateView() {
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int width = size.x;
        int height = size.y;

        FrameLayout.LayoutParams videoViewParams = new FrameLayout.LayoutParams(width, height);
        videoView.setLayoutParams(videoViewParams);

        videoView.addOnLayoutChangeListener(this);
        videoView.requestLayout();
    }

    @Override
    public void onLocalMicrophoneAdded(LocalMicrophone localMicrophone) {

    }

    @Override
    public void onLocalMicrophoneRemoved(LocalMicrophone localMicrophone) {

    }

    @Override
    public void onLocalMicrophoneSelected(LocalMicrophone localMicrophone) {

    }

    @Override
    public void onLocalMicrophoneStateUpdated(LocalMicrophone localMicrophone, Device.DeviceState deviceState) {

    }

    @Override
    public void onLocalSpeakerAdded(LocalSpeaker localSpeaker) {

    }

    @Override
    public void onLocalSpeakerRemoved(LocalSpeaker localSpeaker) {

    }

    @Override
    public void onLocalSpeakerSelected(LocalSpeaker localSpeaker) {
    }

    @Override
    public void onLocalSpeakerStateUpdated(LocalSpeaker localSpeaker, Device.DeviceState deviceState) {

    }

    @Override
    public void onParticipantJoined(Participant participant) {
        Logger.i("Participant joined: %s", participant.getUserId());
    }

    @Override
    public void onParticipantLeft(Participant participant) {
        Logger.i("Participant left: %s", participant.getUserId());
    }

    @Override
    public void onDynamicParticipantChanged(ArrayList<Participant> arrayList) {

    }

    @Override
    public void onLoudestParticipantChanged(Participant participant, boolean b) {

    }
}