package com.example.aftersalesmobile;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class ApiClient {
    public static final String BASE_URL = "http://123.206.124.228:8081/api/";
    private final SessionManager session;

    public ApiClient(SessionManager session) { this.session = session; }

    public JSONObject get(String path) throws Exception { return request("GET", path, null); }
    public JSONObject post(String path, JSONObject body) throws Exception { return request("POST", path, body); }
    public JSONObject patch(String path, JSONObject body) throws Exception { return request("PATCH", path, body); }

    private JSONObject request(String method, String path, JSONObject body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(BASE_URL + path).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        if (session.isLoggedIn()) connection.setRequestProperty("Authorization", "Bearer " + session.token());
        if (body != null) {
            connection.setDoOutput(true);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
        }

        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 400 ? connection.getInputStream() : connection.getErrorStream();
        StringBuilder text = new StringBuilder();
        if (stream != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) text.append(line);
            }
        }
        connection.disconnect();

        JSONObject response = text.length() == 0 ? new JSONObject() : new JSONObject(text.toString());
        int code = response.optInt("code", status);
        if (status < 200 || status >= 300 || code != 200) {
            throw new ApiException(status, response.optString("message", "请求失败"));
        }
        return response;
    }

    public static final class ApiException extends Exception {
        public final int status;
        public ApiException(int status, String message) { super(message); this.status = status; }
    }
}
