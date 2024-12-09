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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
        refresh(null);
    }

    private void addContainersToManager() {
        componentManager.addComponent("init", findViewById(R.id.initContainer));
        componentManager.addComponent("login", findViewById(R.id.oAuthContainer));
        componentManager.addComponent("main", findViewById(R.id.todayContainer));
        componentManager.addComponent("main", findViewById(R.id.logout));
        componentManager.addComponent("main", findViewById(R.id.refresh));
        componentManager.addComponent("main", findViewById(R.id.recentActivitiesContainers));
        componentManager.addComponent("main", findViewById(R.id.mainTitle));
    }

    public void loadRecentActivities() {
        String urlStr = BASE_URL + "fitbit/activity/list/weekly";
        LinearLayout recentContainer = findViewById(R.id.recentActivitiesContainers);
        recentContainer.removeAllViews();
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(() -> {
            try (Response response = httpClient.newCall(new Request.Builder().url(urlStr).build()).execute()) {
                if (response.code() == 200)
                {
                    JSONObject json = new JSONObject(response.body().string());
                    JSONArray data = json.getJSONObject("data").getJSONArray("activities");
                    for (int i = 0; i < data.length(); i++) {
                        JSONObject item = data.getJSONObject(i);
                        TextView textView = new TextView(this);
                        textView.setPadding(0,0,0,20);
                        textView.setText(String.format("%s - %s steps - %s calories - %s km distance - %s heart rate - %s mins",
                                item.getString("activityName"), item.optInt("steps", 0),
                                item.getInt("calories"),
                                Math.round(item.getDouble("distance")),
                                item.getInt("averageHeartRate"),
                                item.getInt("duration") / 60000));
                        textView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                        runOnUiThread(() -> recentContainer.addView(textView));
                    }
                }
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
        });
    }

    public void loadTodaysStats(TextView view, String type) {
        String urlStr = BASE_URL + String.format("fitbit/activity/weekly?type=%s", type);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(() -> {
            try (Response response = httpClient.newCall(new Request.Builder().url(urlStr).build()).execute()) {
                if (response.code() == 200)
                {
                    double total = 0;
                    JSONArray data = new JSONObject(response.body().string()).getJSONObject("data").getJSONArray(String.format("activities-%s", type));
                    for (int i = 0; i < data.length(); i++) {
                        total += data.getJSONObject(i).getDouble("value");
                    }
                    view.setText(String.valueOf(Math.round(total)));
                }
                else
                    runOnUiThread(() ->  componentManager.onViewSwitchEvent("init"));
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
        });
    }

    public void refresh(View view) {
       loadTodaysStats(findViewById(R.id.stepsTaken), "steps");
       loadTodaysStats(findViewById(R.id.caloriesBurned), "calories");
       loadTodaysStats(findViewById(R.id.timeActive), "distance");
       loadRecentActivities();
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
                    refresh(null);
                }
            }
        });
        webView.loadUrl(BASE_URL + "security/login");
    }
}