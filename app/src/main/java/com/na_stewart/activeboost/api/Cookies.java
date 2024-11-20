package com.na_stewart.activeboost.api;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

public class Cookies implements CookieJar {

    private static final String PREFS_NAME = "CookiePrefs";
    private static final String COOKIES_KEY = "Cookies";
    private final SharedPreferences sharedPreferences;

    public Cookies(Context context) {
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @Override
    public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
        Set<String> cookieStrings = new HashSet<>();
        for (Cookie cookie : cookies)
            cookieStrings.add(cookie.toString());
        sharedPreferences.edit().putStringSet(COOKIES_KEY, cookieStrings).apply();
    }

    @Override
    public List<Cookie> loadForRequest(HttpUrl url) {
        // Load cookies from SharedPreferences
        Set<String> cookieStrings = sharedPreferences.getStringSet(COOKIES_KEY, new HashSet<>());
        List<Cookie> cookies = new ArrayList<>();
        for (String cookieString : cookieStrings) {
            Cookie cookie = Cookie.parse(url, cookieString);
            if (cookie != null)
                cookies.add(cookie);
        }
        return cookies;
    }
}