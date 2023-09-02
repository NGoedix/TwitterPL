package com.github.NGoedix;

import com.github.NGoedix.twitterpl.TweetScrapper;
import com.google.gson.Gson;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;

public class Main {

    private static final Gson gson = new Gson();
    private static final TweetScrapper tweetScrapper = new TweetScrapper(gson);

    static {
        CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
    }

    public static void main(String[] args) {
        tweetScrapper.extractVideo("https://twitter.com/catshouldnt/status/1694470654043836441");
    }
}
