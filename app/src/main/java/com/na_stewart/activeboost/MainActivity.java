package com.na_stewart.activeboost;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

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
import okhttp3.RequestBody;
import okhttp3.Response;
;

public class MainActivity extends AppCompatActivity {

    private final String BASE_URL = "https://activeboost.na-stewart.com/api/v1/";
    private final ComponentManager componentManager = new ComponentManager();
    private OkHttpClient httpClient;
    private SharedPreferences sharedPreferences;

    // INITIALIZATION

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
        fillPublicGroups();
    }

    private void addContainersToManager() {
        componentManager.addComponent("init", findViewById(R.id.initContainer));
        componentManager.addComponent("login", findViewById(R.id.oAuthContainer));
        componentManager.addComponent("home", findViewById(R.id.home));
        componentManager.addComponent("active", findViewById(R.id.active));
        componentManager.addComponent("profile", findViewById(R.id.profile));
    }

    public void login(View view) {
        componentManager.switchView("login");
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
                    componentManager.switchView("home");
                    findViewById(R.id.nav).setVisibility(View.VISIBLE);
                    fillPublicGroups();
                }
            }
        });
        webView.loadUrl(BASE_URL + "security/login");
    }

    public void logout(View view) {
        sharedPreferences.edit().remove("Cookies").apply();
        componentManager.switchView("init");
        findViewById(R.id.nav).setVisibility(View.GONE);
    }

    public void navigate(View view){
        String navClicked = getResources().getResourceEntryName(view.getId());
        switch (navClicked) {
            case "homeNav":
                componentManager.switchView("home");
                break;
            case "activeNav":
                componentManager.switchView("active");
                break;
            case "profileNav":
                componentManager.switchView("profile");
                break;
        }
    }

    // HOME

    private void fillPublicGroups(){
        LinearLayout publicGroupsContainer = findViewById(R.id.publicGroups);
        publicGroupsContainer.removeAllViews();
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(() -> {
            try (Response response = httpClient.newCall(new Request.Builder().url(BASE_URL + "group").build()).execute()) {
                if (response.code() == 200)
                {
                    JSONObject json = new JSONObject(response.body().string());
                    JSONArray data = json.getJSONArray( "data");
                    for (int i = 0; i < data.length(); i++) {
                        JSONObject item = data.getJSONObject(i);
                        LinearLayout publicGroupView = getPublicGroupView(item);
                        runOnUiThread(() ->  publicGroupsContainer.addView(publicGroupView));
                    }
                }
                else
                    runOnUiThread(() -> logout(null));
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
        });
    }

    private LinearLayout getPublicGroupView(JSONObject group) throws JSONException {
        LinearLayout parentLayout = new LinearLayout(getApplicationContext());
        parentLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        parentLayout.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout childLayout = new LinearLayout(getApplicationContext());
        childLayout.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(299),
                LinearLayout.LayoutParams.MATCH_PARENT));
        childLayout.setOrientation(LinearLayout.VERTICAL);
        TextView titleTextView = new TextView(getApplicationContext());
        titleTextView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        titleTextView.setText(group.getString("title"));
        titleTextView.setTextSize(20);
        TextView descriptionTextView = new TextView(getApplicationContext());
        descriptionTextView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        descriptionTextView.setText(group.getString("description"));
        descriptionTextView.setTextSize(12);
        childLayout.addView(titleTextView);
        childLayout.addView(descriptionTextView);
        Button joinButton = new Button(getApplicationContext());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        buttonParams.weight = 1;
        joinButton.setLayoutParams(buttonParams);
        joinButton.setText("Join");
        String id = group.getString("id");
        String inviteCode = group.getString("invite_code");
        joinButton.setOnClickListener((View v) -> joinGroup(id, inviteCode));
        parentLayout.addView(childLayout);
        parentLayout.addView(joinButton);
        return parentLayout;
    }

    private void joinGroup(String id, String inviteCode) {
        String url = BASE_URL + String.format("group/join?id=%s&invite-code=%s", id, inviteCode);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(() -> {
            try (Response response = httpClient.newCall(new Request.Builder().url(url)
                    .put(RequestBody.create(new byte[0], null)).build()).execute()) {
                if (response.code() == 200)
                    toast("Group joined successfully, you will find it in your groups & challenges.");
                else
                    toast(new JSONObject(response.body().string()));
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
        });
    }

    // ACTIVE

    // PROFILE

    // EDIT GROUP

    // EDIT CHALLENGE

    // MISC

    private int dpToPx(int dp) {
        return Math.round(dp * getApplicationContext().getResources().getDisplayMetrics().density);
    }

    private void toast(JSONObject error) throws JSONException {
        toast(error.getString("message"));
    }

    private void toast(String text) {
        runOnUiThread(() -> Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG).show());
    }
}