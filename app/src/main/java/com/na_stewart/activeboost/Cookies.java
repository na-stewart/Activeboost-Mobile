package com.na_stewart.activeboost;

import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

public class Cookies implements CookieJar {
    private final SharedPreferences sharedPreferences;

    public Cookies(SharedPreferences sharedPreferences) {
        this.sharedPreferences = sharedPreferences;
    }

    @Override
    public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
        sharedPreferences.edit().putStringSet("Cookies", cookies.stream().map(Cookie::toString)
                .collect(Collectors.toSet())).apply();
    }

    @Override
    public List<Cookie> loadForRequest(HttpUrl url) {
       return sharedPreferences.getStringSet("Cookies", new HashSet<>()).stream()
               .map(cookieString -> Cookie.parse(url, cookieString)).collect(Collectors.toList());
    }
}