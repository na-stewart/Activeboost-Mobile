package com.na_stewart.activeboost;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
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
import java.util.ArrayList;
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
    private int selectedGroupId = 0; // Easy reference id for updating/deleting selected group.
    private int selectedChallengeId = 0; // Easy reference id for updating/deleting selected challenge.

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
        addValuesToChallengeThresholdSpinner();
        fillPublicGroups();
    }

    private void addContainersToManager() {
        componentManager.addComponent("init", findViewById(R.id.initContainer));
        componentManager.addComponent("login", findViewById(R.id.oAuthContainer));
        componentManager.addComponent("home", findViewById(R.id.home));
        componentManager.addComponent("groupsAndChallenges", findViewById(R.id.groupsAndChallenges));
        componentManager.addComponent("profile", findViewById(R.id.profile));
        componentManager.addComponent("editGroup", findViewById(R.id.editGroup));
        componentManager.addComponent("editChallenge", findViewById(R.id.editChallenge));
        componentManager.addComponent("groupInfo", findViewById(R.id.groupInfo));
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

    public void onNavigate(View view){
        String navClicked = getResources().getResourceEntryName(view.getId());
        switch (navClicked) {
            case "homeNav":
                componentManager.switchView("home");
                fillPublicGroups();
                break;
            case "activeNav":
                componentManager.switchView("groupsAndChallenges");
                fillMyGroups();
                break;
            case "profileNav":
                componentManager.switchView("profile");
                break;
        }
    }

    // HOME

    private void fillPublicGroups() {
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
        String inviteCode = group.getString("invite_code");
        joinButton.setOnClickListener((View v) -> onJoinGroup(inviteCode));
        parentLayout.addView(childLayout);
        parentLayout.addView(joinButton);
        return parentLayout;
    }

    private void onJoinGroup(String inviteCode) {
        String url = BASE_URL + String.format("group/join?invite-code=%s", inviteCode);
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

    // GROUPS & CHALLENGES

    public void onCreateGroup(View view) {
        // Opens edit group window, not used for API call.
        componentManager.switchView("editGroup");
        selectedGroupId = 0;
        ((Button) findViewById(R.id.groupUpdate)).setText("Create");
    }

    private void fillMyGroups() {
        LinearLayout myGroupsContainer = findViewById(R.id.yourGroups);
        myGroupsContainer.removeAllViews();
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(() -> {
            try (Response response = httpClient.newCall(new Request.Builder().url(BASE_URL + "group").build()).execute()) {
                if (response.code() == 200)
                {
                    JSONObject json = new JSONObject(response.body().string());
                    JSONArray data = json.getJSONArray( "data");
                    for (int i = 0; i < data.length(); i++) {
                        JSONObject item = data.getJSONObject(i);
                        LinearLayout publicGroupView = getMyGroupView(item);
                        runOnUiThread(() ->  myGroupsContainer.addView(publicGroupView));
                    }
                }
                else
                    runOnUiThread(() -> logout(null));
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
        });
    }

    private LinearLayout getMyGroupView(JSONObject group) throws JSONException {
        LinearLayout parentLayout = new LinearLayout(getApplicationContext());
        parentLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        ));
        parentLayout.setOrientation(LinearLayout.VERTICAL);
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
        Button infoButton = new Button(getApplicationContext());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(dpToPx(70), dpToPx(37), 1.0f);
        infoButton.setLayoutParams(buttonParams);
        infoButton.setText("Info");
        infoButton.setTextSize(10);
        infoButton.setOnClickListener((View v) -> openGroupInfo(group)); // Here is where the view button event is defined.
        parentLayout.addView(titleTextView);
        parentLayout.addView(descriptionTextView);
        parentLayout.addView(infoButton);
        return parentLayout;
    }

    private void openGroupInfo(JSONObject group)  {
        componentManager.switchView("groupInfo");
        try {
            selectedGroupId = group.getInt("id");
            ((TextView) findViewById(R.id.groupTitle)).setText(group.getString("title"));
            ((TextView) findViewById(R.id.groupInfoDescription)).setText(group.getString("description"));
            ((TextView) findViewById(R.id.groupInviteCode)).setText(String.format("Invite code: %s", group.getString("invite_code")));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    private void fillMyChallenges() {
        // https://activeboost.na-stewart.com/api/v1/group/challenge/you
        // See methods above for populating data via views. First you create the view
        // then add it to the container.
        LinearLayout myChallengesContainer = findViewById(R.id.yourChallenges);
        myChallengesContainer.removeAllViews();


    }

    private LinearLayout getMyChallengeView(JSONObject challenge) throws JSONException {
        return null;
        // Once the view is defined, the button should redeem the challenge via
        // https://activeboost.na-stewart.com/api/v1/group/challenge/redeem?id=1 (challenge id)
    }

    // PROFILE



    // GROUP INFO

    public void onCreateChallenge(View view) {
        // Opens create challenge window, not use for API call.
        componentManager.switchView("editChallenge");
        ((Button) findViewById(R.id.challengeUpdate)).setText("Create");
    }

    public void onEditGroup(View view) {
        // Opens edit group window, not used for API call.
        // selectedGroupId set via info button clicked in groups & challenges window.
        componentManager.switchView("editGroup");
        ((Button) findViewById(R.id.groupUpdate)).setText("Update");
    }

    private void fillLeaderboard() {
        // https://activeboost.na-stewart.com/api/v1/group/leaderboard?id=1 (group id)
        // See methods above for populating data via views. First you create the view
        // then add it to the container.
        LinearLayout myGroupsContainer = findViewById(R.id.groupLeaderboard);
        myGroupsContainer.removeAllViews();
    }

    private LinearLayout getLeaderboardView(JSONObject participant) throws JSONException {
        return null;
    }

    private void fillGroupChallenges() {
        // https://activeboost.na-stewart.com/api/v1/group/challenge?group=1 (group id)
        // See methods above for populating data via views. First you create the view
        // then add it to the container.
        LinearLayout myGroupsContainer = findViewById(R.id.groupChallenges);
        myGroupsContainer.removeAllViews();
    }

    private LinearLayout getGroupChallengesView(JSONObject participant) throws JSONException {
        return null;
        // Once the view is defined, the button should open the edit challenge window.
    }

    // EDIT GROUP

    public void onPutGroup(View view) {
        // Method triggered by button for updating group.
        // https://activeboost.na-stewart.com/api/v1/group PUT
        // Form data: title, description, private.
    }

    public void onPostGroup(View view) {
        // Method triggered by button for creating group.
        // https://activeboost.na-stewart.com/api/v1/group POST
        // Form data: title, description, private.
        selectedGroupId = 0; // Set to returned group id when created.
    }

    // EDIT CHALLENGE

    public void onPutChallenge(View view) {
        // Method triggered by button for updating group.
        // https://activeboost.na-stewart.com/api/v1/group/challenge?group=1 PUT
        // Form data: title, description, reward, threshold, period, threshold-type
    }

    public void onPostChallenge(View view) {
        // Method triggered by button for creating group.
        // https://activeboost.na-stewart.com/api/v1/group/challenge?group=1 POST
        // Form data: title, description, reward, threshold, period, threshold-type
        selectedChallengeId = 0; // Set to returned challenge id when created.
    }

    private void addValuesToChallengeThresholdSpinner() {
        // Spinner is a dropdown.
        List<String> spinnerValues = new ArrayList<>();
        spinnerValues.add("steps");
        spinnerValues.add("distance");
        spinnerValues.add("calories");
        spinnerValues.add("minutesVeryActive");
        spinnerValues.add("minutesFairlyActive");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                spinnerValues
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        Spinner thresholdSelector = findViewById(R.id.challengeThresholdType);
        thresholdSelector.setAdapter(adapter);
    }

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