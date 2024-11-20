package com.na_stewart.activeboost;

import android.os.Bundle;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;


import com.na_stewart.activeboost.api.Cookies;
import com.na_stewart.activeboost.ui.ComponentManager;

import java.util.List;

import okhttp3.Cookie;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;;

public class MainActivity extends AppCompatActivity {

    ComponentManager componentManager = new ComponentManager();
    OkHttpClient httpClient = new OkHttpClient.Builder()
            .cookieJar(new Cookies(getApplicationContext()))
            .build();


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
        loginHandler();
    }

    private void loginHandler() {
        LinearLayout loginLayout = findViewById(R.id.oAuthContainer);
        componentManager.addComponent("login", loginLayout);
        Button loginButton = findViewById(R.id.loginButton);
        loginButton.setOnClickListener(view -> {
            componentManager.switchView("login");
            String oauthUrl = "https://activeboost.na-stewart.com/api/v1/security/login";
            WebView webView = findViewById(R.id.oAuthWebView);
            webView.clearCache(true);
            webView.getSettings().setJavaScriptEnabled(true);
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (url.contains("/callback")) {
                    HttpUrl httpUrl = HttpUrl.parse(url);
                    Cookie okHttpCookie = Cookie.parse(httpUrl, CookieManager.getInstance().getCookie(url).trim());
                    httpClient.cookieJar().saveFromResponse(httpUrl, List.of(okHttpCookie));
                }
                }
            });
            webView.loadUrl(oauthUrl);
        });

    }
}