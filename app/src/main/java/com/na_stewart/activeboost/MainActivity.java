package com.na_stewart.activeboost;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.na_stewart.activeboost.api.Cookies;
import com.na_stewart.activeboost.ui.ComponentManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Cookie;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;;

public class MainActivity extends AppCompatActivity {

    private final String BASE_URL = "https://activeboost.na-stewart.com/api/v1/";
    private final ComponentManager componentManager = new ComponentManager();
    private OkHttpClient httpClient;
    private SharedPreferences sharedPreferences;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        sharedPreferences = getApplicationContext().getSharedPreferences("CookiePrefs", Context.MODE_PRIVATE);
        httpClient = new OkHttpClient.Builder().cookieJar(new Cookies(sharedPreferences)).build();
        addContainersToManager();
    }

    private void addContainersToManager() {
        componentManager.addComponent("init", findViewById(R.id.initContainer));
        componentManager.addComponent("login", findViewById(R.id.oAuthContainer));
    }


    public void logout(View view) {
        sharedPreferences.edit().remove("Cookies").apply();
        componentManager.onViewSwitchEvent("init");
    }

    public void login(View view) {
        componentManager.onViewSwitchEvent("login");
        WebView webView = findViewById(R.id.oAuthWebView);
        CookieManager cookieManager = CookieManager.getInstance();
        webView.clearCache(true);
        cookieManager.removeAllCookies(null);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (url.contains("/callback")) {
                    HttpUrl httpUrl = HttpUrl.parse(url);
                    httpClient.cookieJar().saveFromResponse(httpUrl,
                            List.of(Cookie.parse(httpUrl, cookieManager.getCookie(url).trim())));
                    componentManager.onViewSwitchEvent("main");
                }
            }
        });
        webView.loadUrl(BASE_URL + "security/login");
    }
}