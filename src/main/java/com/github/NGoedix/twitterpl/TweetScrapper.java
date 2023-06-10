package com.github.NGoedix.twitterpl;

import com.google.gson.Gson;

public class TweetScrapper {

    private final Gson gson;
    private final TwitterAPI twitterAPI;
    private final VideoExtractor videoExtractor;

    public TweetScrapper(Gson gson) {
        this.gson = gson;
        this.twitterAPI = new TwitterAPI(gson);
        this.videoExtractor = new VideoExtractor();
    }

    public void extractVideo(String url) {
        try {
            String[] tokens = twitterAPI.getTokens(url);
            String tweet_details = twitterAPI.getTweetDetails(url, tokens[1], tokens[0]);
            System.out.println(videoExtractor.extractMp4s(tweet_details, url));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
