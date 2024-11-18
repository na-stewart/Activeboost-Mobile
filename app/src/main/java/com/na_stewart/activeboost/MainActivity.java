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


import com.na_stewart.activeboost.ui.ComponentManager;

import okhttp3.OkHttpClient;;

public class MainActivity extends AppCompatActivity {

    ComponentManager componentManager = new ComponentManager();
    OkHttpClient client = new OkHttpClient();


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
                    // Once the page finishes loading, you can extract the cookies
                    CookieManager cookieManager = CookieManager.getInstance();
                    String cookies = cookieManager.getCookie(url);
                    Log.d("Login Cookies", "Cookies: " + cookies);
                    componentManager.addComponent("login", loginLayout);
                    // Get request and add cookie to okhttp cookie jar
                }
            });

            // Load the OAuth URL in the WebView
            webView.loadUrl(oauthUrl);
        });

    }
}