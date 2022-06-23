package com.vidyo.vidyoconnector.vitel.request;

import android.content.Context;

import com.squareup.okhttp.Authenticator;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.Credentials;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ResponseBody;
import com.vidyo.vidyoconnector.utils.AppUtils;
import com.vidyo.vidyoconnector.utils.UiUtils;
import com.vidyo.vidyoconnector.vitel.job.ExecutorImpl;
import com.vidyo.vidyoconnector.vitel.job.Job;
import com.vidyo.vidyoconnector.vitel.job.JobApi;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.StringReader;
import java.net.Proxy;

import javax.net.ssl.SSLContext;

public class RequestManager {

    private static final String USER_NAME = "admin";
    private static final String PASSWORD = "v1dy0123";

    private static final String EXTENSION = "8621";
    private static final String EXTENSION_PREFIX = "0234";
    private static final String ROOM_EXT = EXTENSION + EXTENSION_PREFIX;

    private static final String ROOM_PIN = "88888888";
    private static final String ROOM_NAME = "Test-Room";

    private static final String REQUEST_API = "https://chen-vp1.vidyo.us.rd.eilab.biz/services/v1_1/VidyoPortalUserService/";

    private static final String REQUEST_TAG = "Add.Member.Tag";

    public interface RequestCallback<T> {

        void onSuccess(T data);

        void onFailure(String reason);
    }

    private final OkHttpClient okHttpClient;

    private boolean canceled;

    public RequestManager(Context context) {
        this.okHttpClient = new OkHttpClient();
        SSLContext sslContext = AppUtils.getSslContextForCertificateFile(context, "ca_certificates.crt");
        this.okHttpClient.setSslSocketFactory(sslContext.getSocketFactory());
        this.okHttpClient.setAuthenticator(new Authenticator() {

            @Override
            public Request authenticate(Proxy proxy, Response response) {
                String credential = Credentials.basic(USER_NAME, PASSWORD);
                if (responseCount(response) >= 3) return null;
                return response.request().newBuilder().header("Authorization", credential).build();
            }

            @Override
            public Request authenticateProxy(Proxy proxy, Response response) {
                return null;
            }
        });
    }

    public void getVersion(RequestCallback<String> callback) {
        MediaType mediaType = MediaType.parse("text/xml");

        String soap = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:v1=\"http://portal.vidyo.com/user/v1_1\">\n" +
                "   <soapenv:Header/>\n" +
                "   <soapenv:Body>\n" +
                "      <v1:GetPortalVersionRequest/>\n" +
                "   </soapenv:Body>\n" +
                "</soapenv:Envelope>";

        RequestBody body = RequestBody.create(mediaType, soap);

        Request request = new Request.Builder()
                .url(REQUEST_API)
                .post(body)
                .addHeader("Content-Type", "text/xml")
                .addHeader("SOAPAction", "getPortalVersion")
                .build();

        if (canceled) {
            if (callback != null) callback.onFailure("Cancelled");
            return;
        }

        okHttpClient.newCall(request).enqueue(new Callback() {

            @Override
            public void onFailure(Request request, IOException e) {
                if (callback != null) callback.onFailure(e.getMessage());
                if (e != null) e.printStackTrace();
            }

            @Override
            public void onResponse(Response response) {
                if (callback != null) callback.onSuccess(parseResponse(response));
            }
        });
    }

    public void createRoom(RequestCallback<Room> callback) {
        if (canceled) {
            if (callback != null) callback.onFailure("Cancelled");
            return;
        }

        JobApi jobApi = new ExecutorImpl();
        Job job = new Job() {
            @Override
            public void run() {
                String lastBody = "none";
                try {
                    String createRoomBody = okHttpClient.newCall(createRoomRequest()).execute().body().string();
                    lastBody = createRoomBody;
                    String entityId = createRoomBody.split("<ns1:entityID>")[1].split("</ns1:entityID>")[0];

                    String createPin = okHttpClient.newCall(createRoomPin(entityId)).execute().body().string();
                    lastBody = createPin;
                    boolean isOk = isResponseOk(createPin);
                    if (!isOk)
                        throw new Exception("Failed to create Room Pin");

                    String roomEntityBody = okHttpClient.newCall(retrieveRoomRequest(entityId)).execute().body().string();
                    lastBody = roomEntityBody;
                    String roomUrl = roomEntityBody.split("<ns1:roomURL>")[1].split("</ns1:roomURL>")[0];

                    Room room = new Room(ROOM_NAME, entityId, roomUrl, ROOM_PIN);
                    callback.onSuccess(room);
                } catch (Exception e) {
                    callback.onFailure(e.getMessage() + "\n" + lastBody);
                }
            }
        };

        jobApi.postJob(job);
    }

    public void deleteRoom(Room room, RequestCallback<Boolean> callback) {
        if (canceled) {
            if (callback != null) callback.onFailure("Cancelled");
            return;
        }

        JobApi jobApi = new ExecutorImpl();
        Job job = new Job() {
            @Override
            public void run() {
                try {
                    String deleteRoomBody = okHttpClient.newCall(deleteRoom(room.getRoomId())).execute().body().string();
                    callback.onSuccess(isResponseOk(deleteRoomBody));
                } catch (Exception e) {
                    callback.onFailure(e.getMessage());
                }
            }
        };

        jobApi.postJob(job);
    }

    private Request createRoomRequest() {
        MediaType mediaType = MediaType.parse("text/xml");
        String soap = "<s11:Envelope xmlns:s11='http://schemas.xmlsoap.org/soap/envelope/'>\n" +
                "  <s11:Body>\n" +
                "    <ns1:CreateRoomRequest xmlns:ns1='http://portal.vidyo.com/user/v1_1'>\n" +
                "      <ns1:name>" + ROOM_NAME + "</ns1:name>\n" +
                "      <ns1:extension>" + ROOM_EXT + "</ns1:extension>\n" +
                "    </ns1:CreateRoomRequest>\n" +
                "  </s11:Body>\n" +
                "</s11:Envelope>";

        RequestBody body = RequestBody.create(mediaType, soap);
        return new Request.Builder()
                .url(REQUEST_API)
                .post(body)
                .addHeader("Content-Type", "text/xml")
                .addHeader("SOAPAction", "createRoom")
                .build();
    }

    private Request createRoomPin(String entityId) {
        MediaType mediaType = MediaType.parse("text/xml");
        String soap = "<s11:Envelope xmlns:s11='http://schemas.xmlsoap.org/soap/envelope/'>\n" +
                "  <s11:Body>\n" +
                "    <ns1:CreateRoomPINRequest xmlns:ns1='http://portal.vidyo.com/user/v1_1'>\n" +
                "<!-- Pattern: [0-9]+ -->\n" +
                "      <ns1:roomID>" + entityId + "</ns1:roomID>\n" +
                "      <ns1:PIN>" + ROOM_PIN + "</ns1:PIN>\n" +
                "    </ns1:CreateRoomPINRequest>\n" +
                "  </s11:Body>\n" +
                "</s11:Envelope>";

        RequestBody body = RequestBody.create(mediaType, soap);
        return new Request.Builder()
                .url(REQUEST_API)
                .post(body)
                .addHeader("Content-Type", "text/xml")
                .addHeader("SOAPAction", "createRoomPIN")
                .build();
    }

    private Request retrieveRoomRequest(String entityId) {
        MediaType mediaType = MediaType.parse("text/xml");
        String soap = "<s11:Envelope xmlns:s11='http://schemas.xmlsoap.org/soap/envelope/'>\n" +
                "  <s11:Body>\n" +
                "    <ns1:GetEntityByEntityIDRequest xmlns:ns1='http://portal.vidyo.com/user/v1_1'>\n" +
                "      <ns1:entityID>" + entityId + "</ns1:entityID>\n" +
                "    </ns1:GetEntityByEntityIDRequest>\n" +
                "  </s11:Body>\n" +
                "</s11:Envelope>";

        RequestBody body = RequestBody.create(mediaType, soap);
        return new Request.Builder()
                .url(REQUEST_API)
                .post(body)
                .addHeader("Content-Type", "text/xml")
                .addHeader("SOAPAction", "getEntityByEntityID")
                .build();
    }

    private Request deleteRoom(String entityId) {
        MediaType mediaType = MediaType.parse("text/xml");
        String soap = "<s11:Envelope xmlns:s11='http://schemas.xmlsoap.org/soap/envelope/'>\n" +
                "  <s11:Body>\n" +
                "    <ns1:DeleteRoomRequest xmlns:ns1='http://portal.vidyo.com/user/v1_1'>\n" +
                "      <ns1:roomID>" + entityId + "</ns1:roomID>\n" +
                "    </ns1:DeleteRoomRequest>\n" +
                "  </s11:Body>\n" +
                "</s11:Envelope>";

        RequestBody body = RequestBody.create(mediaType, soap);
        return new Request.Builder()
                .url(REQUEST_API)
                .post(body)
                .addHeader("Content-Type", "text/xml")
                .addHeader("SOAPAction", "deleteRoom")
                .build();
    }

    public void cancel() {
        canceled = true;

        if (UiUtils.isMainThread()) {
            Thread cancel = new Thread(() -> okHttpClient.cancel(REQUEST_TAG));
            cancel.start();
        } else {
            okHttpClient.cancel(REQUEST_TAG);
        }
    }

    private String parseResponse(Response response) {
        try {
            ResponseBody body = response.body();

            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
            XmlPullParser xpp = factory.newPullParser();

            String data = body.string();
            xpp.setInput(new StringReader(data));
            body.close();

            return data;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "Failed to parse a XML response";
    }

    private boolean isResponseOk(String response) {
        String value = response.split("<ns1:OK>")[1].split("</ns1:OK>")[0];
        return value.equalsIgnoreCase("OK");
    }

    private static int responseCount(Response response) {
        int result = 1;
        while ((response = response.priorResponse()) != null) {
            result++;
        }
        return result;
    }
}