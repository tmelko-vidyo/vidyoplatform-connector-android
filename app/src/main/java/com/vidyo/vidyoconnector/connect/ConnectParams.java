package com.vidyo.vidyoconnector.connect;

/**
 * VidyoPlatform credentials
 */
public final class ConnectParams {

    /**
     * Designated cloud or on-premises portal of customer
     */
    public static final String PORTAL_HOST = "chen-vp1.vidyo.us.rd.eilab.biz";

    /**
     * The room key of the video conference. All the endpoints must share the same room key to join the same video conference.
     */
    public static final String ROOM_KEY = "Pk9V3lSpw3";

    /**
     * The name displayed to other participants in a video conference. For example, "John Smith".
     */
    public static final String ROOM_DISPLAY_NAME = "John Doe";

    /**
     * If necessary, the room pin to enter the room key of the video conference.
     */
    public static final String ROOM_PIN = "";
}