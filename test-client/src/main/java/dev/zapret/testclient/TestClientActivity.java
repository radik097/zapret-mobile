package dev.zapret.testclient;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class TestClientActivity extends Activity {
    private static final String TAG = "ZAPRET_TEST_CLIENT";
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        status = new TextView(this);
        status.setGravity(Gravity.CENTER);
        status.setTextSize(18);
        status.setText("Running traffic probe");
        setContentView(status);

        String mode = getIntent().getStringExtra("mode");
        if ("raw-http".equals(mode)) {
            String host = getIntent().getStringExtra("host");
            if (host == null || host.isBlank()) {
                host = "10.0.2.2";
            }
            int port = getIntent().getIntExtra("port", 18081);
            String path = getIntent().getStringExtra("path");
            if (path == null || path.isBlank()) {
                path = "/probe";
            }
            String authority = getIntent().getStringExtra("authority");
            if (authority == null || authority.isBlank()) {
                authority = "blocked.example";
            }
            runRawHttpProbe(host, port, path, authority);
        } else {
            String url = getIntent().getStringExtra("url");
            if (url == null || url.isBlank()) {
                url = "http://10.0.2.2:18080/probe";
            }
            runProbe(url);
        }
    }

    private void runProbe(String url) {
        new Thread(() -> {
            String result;
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout(10_000);
                connection.setReadTimeout(10_000);
                connection.setRequestProperty("Connection", "close");
                int code = connection.getResponseCode();
                String body = readBody(connection);
                result = "result=" + code + " body=" + body.trim();
            } catch (Exception error) {
                result = "error=" + error.getClass().getSimpleName() + " message=" + error.getMessage();
            }

            String finalResult = result;
            Log.i(TAG, finalResult);
            runOnUiThread(() -> status.setText(finalResult));
        }, "zapret-test-client-probe").start();
    }

    private void runRawHttpProbe(String host, int port, String path, String authority) {
        new Thread(() -> {
            String result;
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 10_000);
                socket.setSoTimeout(10_000);
                String request = "GET " + path + " HTTP/1.1\r\n"
                        + "Host: " + authority + "\r\n"
                        + "User-Agent: zapret-test-client/raw\r\n"
                        + "Connection: close\r\n"
                        + "\r\n";
                OutputStream output = socket.getOutputStream();
                output.write(request.getBytes(StandardCharsets.US_ASCII));
                output.flush();

                String response = readStream(socket.getInputStream());
                int code = parseStatusCode(response);
                String body = responseBody(response).trim();
                result = "raw_result=" + code + " body=" + body;
            } catch (Exception error) {
                result = "raw_error=" + error.getClass().getSimpleName() + " message=" + error.getMessage();
            }

            String finalResult = result;
            Log.i(TAG, finalResult);
            runOnUiThread(() -> status.setText(finalResult));
        }, "zapret-test-client-raw-http").start();
    }

    private static String readBody(HttpURLConnection connection) throws Exception {
        InputStream stream = connection.getInputStream();
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static String readStream(InputStream stream) throws Exception {
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static int parseStatusCode(String response) {
        if (response.length() < 12 || !response.startsWith("HTTP/")) {
            return -1;
        }
        try {
            return Integer.parseInt(response.substring(9, 12));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static String responseBody(String response) {
        int split = response.indexOf("\r\n\r\n");
        if (split < 0) {
            return response;
        }
        return response.substring(split + 4);
    }
}
