package com.github.NGoedix.twitterpl;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TwitterAPI {
    private final Gson gson;

    public TwitterAPI(Gson gson) {
        this.gson = gson;
    }

    public String[] getTokens(String tweetUrl) throws Exception {
        // Initial request to get HTML
        URL url = new URL(tweetUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        String response = readResponse(conn);

        // Find main.js URL
        Pattern pattern = Pattern.compile("https://abs.twimg.com/responsive-web/client-web-legacy/main.[^\\.]+.js");
        Matcher matcher = pattern.matcher(response);
        if (!matcher.find()) {
            throw new Exception("Failed to find main.js file. Tweet url: " + tweetUrl);
        }
        String mainJsUrl = matcher.group();

        // Request to get main.js
        url = new URL(mainJsUrl);
        conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        response = readResponse(conn);

        // Find bearer token
        pattern = Pattern.compile("AAAAAAAAA[^\"]+");
        matcher = pattern.matcher(response);
        if (!matcher.find()) {
            throw new Exception("Failed to find bearer token. Tweet url: " + tweetUrl + ", main.js url: " + mainJsUrl);
        }
        String bearerToken = matcher.group();

        // Get guest token
        url = new URL("https://api.twitter.com/1.1/guest/activate.json");
        conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("authorization", "Bearer " + bearerToken);
        response = readResponse(conn);

        // Parse guest token from JSON response
        GuestTokenResponse guestTokenResponse = gson.fromJson(response, GuestTokenResponse.class);
        if (guestTokenResponse.guest_token == null) {
            throw new Exception("Failed to find guest token. Tweet url: " + tweetUrl + ", main.js url: " + mainJsUrl);
        }

        return new String[] {bearerToken, guestTokenResponse.guest_token};
    }

    private static String readResponse(HttpURLConnection conn) throws Exception {
        if (conn.getResponseCode() != 200) {
            throw new Exception("HTTP error code: " + conn.getResponseCode());
        }

        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String inputLine;
        StringBuffer content = new StringBuffer();
        while ((inputLine = in.readLine()) != null) {
            content.append(inputLine);
        }
        in.close();
        conn.disconnect();

        return content.toString();
    }

    public String getTweetDetails(String tweetUrl, String guestToken, String bearerToken) throws Exception {
        // Extract tweet ID from URL
        Pattern pattern = Pattern.compile("(?<=status/)\\d+");
        Matcher matcher = pattern.matcher(tweetUrl);
        if (!matcher.find()) {
            throw new Exception("Could not parse tweet id from your url. Tweet url: " + tweetUrl);
        }
        String tweetId = matcher.group();

        // Load request details (features and variables) from file
        RequestDetails requestDetails;
        try (Reader reader = new FileReader("src/main/resources/request_details.json")) {
            requestDetails = gson.fromJson(reader, RequestDetails.class);
        }

        String url = getDetailsUrl(tweetId, requestDetails.features, requestDetails.variables);

        // Initial request for tweet details
        HttpURLConnection conn = makeGetRequest(url, bearerToken, guestToken);
        int maxRetries = 10;
        int curRetry = 0;
        while (conn.getResponseCode() == 400 && curRetry < maxRetries) {
            // Parse JSON response
            String response = readResponse(conn);
            JsonObject jsonResponse = JsonParser.parseString(response).getAsJsonObject();

            if (!jsonResponse.has("errors")) {
                throw new Exception("Failed to find errors in details error json. Tweet url: " + tweetUrl);
            }

            pattern = Pattern.compile("Variable '([^']+)'");
            for (JsonElement errorElement : jsonResponse.getAsJsonArray("errors")) {
                JsonObject error = errorElement.getAsJsonObject();
                matcher = pattern.matcher(error.get("message").getAsString());
                while (matcher.find()) {
                    requestDetails.variables.put(matcher.group(1), true);
                }
            }

            pattern = Pattern.compile("The following features cannot be null: ([^\"]+)");
            for (JsonElement errorElement : jsonResponse.getAsJsonArray("errors")) {
                JsonObject error = errorElement.getAsJsonObject();
                matcher = pattern.matcher(error.get("message").getAsString());
                while (matcher.find()) {
                    for (String feature : matcher.group(1).split(",")) {
                        requestDetails.features.put(feature.trim(), true);
                    }
                }
            }

            url = getDetailsUrl(tweetId, requestDetails.features, requestDetails.variables);
            conn = makeGetRequest(url, bearerToken, guestToken);
            curRetry++;

            if (conn.getResponseCode() == 200) {
                // Save new variables and features
                try (Writer writer = new FileWriter("request_details.json")) {
                    gson.toJson(requestDetails, writer);
                }
            }
        }

        if (conn.getResponseCode() != 200) {
            throw new Exception("Failed to get tweet details. Tweet url: " + tweetUrl);
        }

        return readResponse(conn);
    }


    private String getDetailsUrl(String tweetId, Map<String, Boolean> features, Map<String, Boolean> variables) {
        // Create a copy of variables - we don't want to modify the original
        Map<String, Object> newVariables = new HashMap<>(variables);
        newVariables.put("focalTweetId", tweetId);

        String variablesJson = gson.toJson(newVariables);
        String featuresJson = gson.toJson(features);

        String encodedVariables = URLEncoder.encode(variablesJson, StandardCharsets.UTF_8);
        String encodedFeatures = URLEncoder.encode(featuresJson, StandardCharsets.UTF_8);

        return "https://twitter.com/i/api/graphql/wTXkouwCKcMNQtY-NcDgAA/TweetDetail?variables=" + encodedVariables + "&features=" + encodedFeatures;
    }

    private static HttpURLConnection makeGetRequest(String url, String bearerToken, String guestToken) throws IOException {
        URL urlObj = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("authorization", "Bearer " + bearerToken);
        conn.setRequestProperty("x-guest-token", guestToken);
        return conn;
    }
}
