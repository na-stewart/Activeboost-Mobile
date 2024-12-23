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
import android.widget.CheckBox;
import android.widget.EditText;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Cookie;
import okhttp3.HttpUrl;
import okhttp3.MultipartBody;
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
    private JSONObject selectedGroup; // Easy reference for updating/deleting selected group.
    private JSONObject selectedChallenge;  // Easy reference for updating/deleting selected challenge.

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

    public void onNavigate(View view) {
        String navClicked = getResources().getResourceEntryName(view.getId());
        switch (navClicked) {
            case "homeNav":
                componentManager.switchView("home");
                fillPublicGroups();
                break;
            case "activeNav":
                componentManager.switchView("groupsAndChallenges");
                fillMyGroups();
                fillMyChallenges();
                break;
            case "profileNav":
                componentManager.switchView("profile");
                refreshProfile();
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
                if (response.code() == 200) {
                    JSONObject json = new JSONObject(response.body().string());
                    JSONArray data = json.getJSONArray("data");
                    for (int i = 0; i < data.length(); i++) {
                        JSONObject item = data.getJSONObject(i);
                        LinearLayout publicGroupView = getPublicGroupView(item);
                        runOnUiThread(() -> publicGroupsContainer.addView(publicGroupView));
                    }
                } else {
                    String errorBody = response.body().string();
                    JSONObject errorJson = new JSONObject(errorBody);
                    runOnUiThread(() -> {
                        toast(errorJson.optString("message", "An unexpected error occurred."));
                        logout(null);
                    });
                }

            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
        });
    }

    private LinearLayout getPublicGroupView(JSONObject group) throws JSONException {
        LinearLayout parentLayout = new LinearLayout(getApplicationContext());
        parentLayout.setPadding(0, 10, 0, 0);
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

    public void joinViaCode(View view) {
        onJoinGroup(((EditText) findViewById(R.id.inviteCode)).getText().toString());
    }

    private void onJoinGroup(String inviteCode) {
        String url = BASE_URL + String.format("group/join?invite-code=%s", inviteCode);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(() -> {
            try (Response response = httpClient.newCall(new Request.Builder().url(url)
                    .put(RequestBody.create(new byte[0], null)).build()).execute()) {
                if (response.code() == 200)
                    toast("Group joined successfully, you will find it in your groups & challenges.");
                else {
                    String errorBody = response.body().string();
                    JSONObject errorJson = new JSONObject(errorBody);
                    runOnUiThread(() -> toast(errorJson.optString("message", "An unexpected error occurred.")));
                }
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
        });
    }

    // GROUPS & CHALLENGES

    public void onCreateGroup(View view) {
        // Opens edit group window, not used for API call.
        componentManager.switchView("editGroup");
        selectedGroup = null;
        ((Button) findViewById(R.id.groupUpdate)).setText("Create");
        ((EditText) findViewById(R.id.groupTitleField)).setText("");
        ((EditText) findViewById(R.id.groupDescription)).setText("");
        ((CheckBox) findViewById(R.id.groupPrivate)).setChecked(false);
        LinearLayout groupChallenges = findViewById(R.id.groupEditableChallenges);
        LinearLayout groupMembers = findViewById(R.id.groupMembers);
        groupChallenges.removeAllViews();
        groupMembers.removeAllViews();
    }

    private void fillMyGroups() {
        LinearLayout myGroupsContainer = findViewById(R.id.yourGroups);
        myGroupsContainer.removeAllViews();
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(() -> {
            try (Response response = httpClient.newCall(new Request.Builder().url(BASE_URL + "group/you").build()).execute()) {
                if (response.code() == 200) {
                    JSONObject json = new JSONObject(response.body().string());
                    JSONArray data = json.getJSONArray("data");
                    for (int i = 0; i < data.length(); i++) {
                        JSONObject item = data.getJSONObject(i);
                        LinearLayout publicGroupView = getMyGroupView(item);
                        runOnUiThread(() -> myGroupsContainer.addView(publicGroupView));
                    }
                } else {
                    String errorBody = response.body().string();
                    JSONObject errorJson = new JSONObject(errorBody);
                    runOnUiThread(() -> toast(errorJson.optString("message", "An unexpected error occurred.")));
                }
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
        });
    }

    private LinearLayout getMyGroupView(JSONObject group) throws JSONException {
        LinearLayout parentLayout = new LinearLayout(getApplicationContext());
        parentLayout.setPadding(0, 10, 0, 0);
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


    private void fillMyChallenges() {
        LinearLayout myChallengesContainer = findViewById(R.id.yourChallenges);
        myChallengesContainer.removeAllViews();
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(() -> {
            try (Response response = httpClient.newCall(new Request.Builder().url(BASE_URL + "group/challenge/you").build()).execute()) {
                if (response.code() == 200) {
                    JSONObject json = new JSONObject(response.body().string());
                    JSONArray data = json.getJSONArray("data");
                    for (int i = 0; i < data.length(); i++) {
                        JSONObject item = data.getJSONObject(i);
                        LinearLayout myChallengeView = getChallengeView(item, "Redeem");
                        runOnUiThread(() -> myChallengesContainer.addView(myChallengeView));
                    }
                } else {
                    String errorBody = response.body().string();
                    JSONObject errorJson = new JSONObject(errorBody);
                    runOnUiThread(() -> toast(errorJson.optString("message", "An unexpected error occurred.")));
                }
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
        });
    }

    private LinearLayout getChallengeView(JSONObject challenge, String buttonType) throws JSONException {
        LinearLayout linearLayout = new LinearLayout(getApplicationContext());
        linearLayout.setPadding(0, 10, 0, 0);
        linearLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        ));
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        TextView titleTextView = new TextView(getApplicationContext());
        titleTextView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        titleTextView.setText(challenge.getString("title"));
        titleTextView.setTextSize(20);
        linearLayout.addView(titleTextView);
        TextView descriptionTextView = new TextView(getApplicationContext());
        descriptionTextView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        descriptionTextView.setText(challenge.getString("description"));
        linearLayout.addView(descriptionTextView);
        TextView fitnessPointsTextView = new TextView(getApplicationContext());
        fitnessPointsTextView.setId(View.generateViewId());
        fitnessPointsTextView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        fitnessPointsTextView.setText(String.format("Fitness Points: %s", challenge.getString("reward")));
        linearLayout.addView(fitnessPointsTextView);
        TextView requiresTextView = new TextView(getApplicationContext());
        requiresTextView.setId(View.generateViewId());
        requiresTextView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        String thresholdType = challenge.getString("threshold_type");
        requiresTextView.setText(String.format("Requires: %s %s", challenge.getString("completion_threshold"),
                thresholdType.equals("distance") ? "km" : thresholdType));
        linearLayout.addView(requiresTextView);
        TextView expiresTextView = new TextView(getApplicationContext());
        expiresTextView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        expiresTextView.setText(String.format("Expires: %s", challenge.getString("expiration_date").substring(0, 10)));
        linearLayout.addView(expiresTextView);
        Button button = new Button(getApplicationContext());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(dpToPx(70), dpToPx(37), 1.0f);
        buttonParams.weight = 1;
        button.setLayoutParams(buttonParams);
        button.setText(buttonType);
        String challengeId = challenge.getString("id");
        if (buttonType.equals("Redeem"))
            button.setOnClickListener((View v) -> redeemChallenge(challengeId));
        else if (buttonType.equals("Join"))
            button.setOnClickListener((View v) -> joinChallenge(challengeId));
        else if (buttonType.equals("Edit"))
            button.setOnClickListener((View v) -> {
                selectedChallenge = challenge;
                componentManager.switchView("editChallenge");
                ((Button) findViewById(R.id.challengeUpdate)).setText("Update");
                try {
                    ((TextView) findViewById(R.id.challengeTitle)).setText(challenge.getString("title"));
                    ((TextView) findViewById(R.id.challengeDescription)).setText(challenge.getString("description"));
                    ((TextView) findViewById(R.id.challengeReward)).setText(challenge.getString("reward"));
                    ((TextView) findViewById(R.id.challengeCompletionThreshold)).setText(challenge.getString("completion_threshold"));
                    Spinner thresholdTypeSpinner = findViewById(R.id.challengeThresholdType);
                    ArrayAdapter<String> retrievedAdapter = (ArrayAdapter<String>) thresholdTypeSpinner.getAdapter();
                    int position = retrievedAdapter.getPosition(challenge.getString("threshold_type"));
                    if (position >= 0)
                        thresholdTypeSpinner.setSelection(position);
                    String expirationDateStr = challenge.getString("expiration_date");
                    String dateCreatedStr = challenge.getString("date_created");
                    String expirationDateClean = expirationDateStr.substring(0, 19);
                    String dateCreatedClean = dateCreatedStr.substring(0, 19);
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    LocalDateTime expirationDate = LocalDateTime.parse(expirationDateClean, formatter);
                    LocalDateTime dateCreated = LocalDateTime.parse(dateCreatedClean, formatter);
                    ((TextView) findViewById(R.id.challengePeriod)).setText(String.valueOf((int) ChronoUnit.DAYS.between(dateCreated, expirationDate)));
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            });
        button.setTextSize(10);
        linearLayout.addView(button);
        return linearLayout;
    }

    private void redeemChallenge(String challengeId) {
        String url = BASE_URL + "group/challenge/redeem?id=" + challengeId;
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(() -> {
            try (Response response = httpClient.newCall(
                    new Request.Builder().url(url).put(RequestBody.create(new byte[0], null)).build()
            ).execute()) {
                if (response.code() == 200) {
                    runOnUiThread(() -> toast("Challenge redeemed successfully!"));
                    fillMyChallenges(); // Refresh challenges
                } else {
                    String errorBody = response.body().string();
                    JSONObject errorJson = new JSONObject(errorBody);
                    runOnUiThread(() -> toast(errorJson.optString("message", "An unexpected error occurred.")));
                }
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
        });
    }

    // PROFILE

    private void fillProfile() {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(() -> {
            try (Response response = httpClient.newCall(new Request.Builder().url(BASE_URL + "security/account").build()).execute()) {
                if (response.code() == 200) {
                    JSONObject data = new JSONObject(response.body().string()).getJSONObject("data");
                    ((TextView) findViewById(R.id.username)).setText(data.getString("username"));
                    ((TextView) findViewById(R.id.bio)).setText(data.getString("bio"));
                } else {
                    String errorBody = response.body().string();
                    JSONObject errorJson = new JSONObject(errorBody);
                    runOnUiThread(() -> toast(errorJson.optString("message", "An unexpected error occurred.")));
                }
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
        });
    }

    public void updateProfile(View view) {
        String username = ((TextView) findViewById(R.id.username)).getText().toString();
        String bio = ((TextView) findViewById(R.id.bio)).getText().toString();
        MultipartBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("username", username)
                .addFormDataPart("bio", bio)
                .build();
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Request request = new Request.Builder().url(BASE_URL + "security/account").put(body).build();
        executorService.execute(() -> {
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    runOnUiThread(() -> toast("Profile updated successfully!"));
                } else {
                    String errorBody = response.body().string();
                    JSONObject errorJson = new JSONObject(errorBody);
                    runOnUiThread(() -> toast(errorJson.optString("message", "An unexpected error occurred.")));
                }
            } catch (IOException | JSONException e) {
                e.printStackTrace();
                runOnUiThread(() -> toast("An error occurred while editing the group."));
            }
        });
    }

    public void loadRecentActivities() {
        String urlStr = BASE_URL + "fitbit/activity/list/weekly";
        LinearLayout recentContainer = findViewById(R.id.recentActivitiesContainer);
        recentContainer.removeAllViews();
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(() -> {
            try (Response response = httpClient.newCall(new Request.Builder().url(urlStr).build()).execute()) {
                if (response.code() == 200) {
                    JSONObject json = new JSONObject(response.body().string());
                    JSONArray data = json.getJSONObject("data").getJSONArray("activities");
                    for (int i = 0; i < data.length(); i++) {
                        JSONObject item = data.getJSONObject(i);
                        TextView textView = new TextView(this);
                        textView.setPadding(0, 0, 0, 20);
                        textView.setText(String.format("%s - %s steps - %s calories - %s heart rate - %s mins",
                                item.getString("activityName"), item.optInt("steps", 0),
                                item.getInt("calories"),
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
                if (response.code() == 200) {
                    double total = 0;
                    JSONArray data = new JSONObject(response.body().string()).getJSONObject("data").getJSONArray(String.format("activities-%s", type));
                    for (int i = 0; i < data.length(); i++) {
                        total += data.getJSONObject(i).getDouble("value");
                    }
                    view.setText(String.valueOf(Math.round(total)));
                    if (Objects.equals(type, "distance"))
                        view.setText(String.format("%s km", view.getText()));
                } else {
                    String errorBody = response.body().string();
                    JSONObject errorJson = new JSONObject(errorBody);
                    runOnUiThread(() -> toast(errorJson.optString("message", "An unexpected error occurred.")));
                }
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
        });
    }

    public void refreshProfile() {
        loadTodaysStats(findViewById(R.id.stepsTaken), "steps");
        loadTodaysStats(findViewById(R.id.caloriesBurned), "calories");
        loadTodaysStats(findViewById(R.id.timeActive), "distance");
        loadRecentActivities();
        fillProfile();
    }

    // GROUP INFO

    private void openGroupInfo(JSONObject group) {
        componentManager.switchView("groupInfo");
        try {
            selectedGroup = group;
            ((TextView) findViewById(R.id.groupTitle)).setText(group.getString("title"));
            ((TextView) findViewById(R.id.groupInfoDescription)).setText(group.getString("description"));
            ((TextView) findViewById(R.id.groupInviteCode)).setText(String.format("Invite code: %s", group.getString("invite_code")));

        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        fillGroupChallenges();
        fillGroupLeaderboard();

    }

    public void onCreateChallenge(View view) {
        // Opens create challenge window, not use for API call.
        if (selectedGroup != null) {
            selectedChallenge = null;
            componentManager.switchView("editChallenge");
            ((Button) findViewById(R.id.challengeUpdate)).setText("Create");
            ((TextView) findViewById(R.id.challengeTitle)).setText("");
            ((TextView) findViewById(R.id.challengeDescription)).setText("");
            ((TextView) findViewById(R.id.challengeReward)).setText("");
            ((TextView) findViewById(R.id.challengeCompletionThreshold)).setText("");
            ((TextView) findViewById(R.id.challengePeriod)).setText("");
            Spinner thresholdTypeSpinner = findViewById(R.id.challengeThresholdType);
            thresholdTypeSpinner.setSelection(1);
        } else
            toast("You must first create a group.");
    }

    public void onEditGroup(View view) {
        // Opens edit group window, not used for API call.
        // selectedGroupId set via info button clicked in groups & challenges window.
        componentManager.switchView("editGroup");
        try {
            ((EditText) findViewById(R.id.groupTitleField)).setText(selectedGroup.getString("title"));
            ((EditText) findViewById(R.id.groupDescription)).setText(selectedGroup.getString("description"));
            ((CheckBox) findViewById(R.id.groupPrivate)).setChecked(selectedGroup.getBoolean("private"));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        fillGroupMembers();
        fillEditGroupChallenges();
    }

    public void onLeaveGroup(View view) {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(() -> {
            try (Response response = httpClient.newCall(
                    new Request.Builder().url(BASE_URL + "group/leave?id=" + selectedGroup.getString("id")).put(RequestBody.create(new byte[0], null)).build()
            ).execute()) {
                if (response.code() == 200) {
                    runOnUiThread(() -> {
                        toast("Group left successfully!");
                        componentManager.switchView("home");
                    });
                    selectedGroup = null;
                } else {
                    toast(new JSONObject(response.body().string()).getString("message"));
                }
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
        });
    }

    private void fillGroupChallenges() {
        LinearLayout groupChallenges = findViewById(R.id.groupChallenges);
        groupChallenges.removeAllViews();
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(() -> {
            try (Response response = httpClient.newCall(new Request.Builder().url(BASE_URL + "group/challenge?id="
                    + selectedGroup.getString("id")).build()).execute()) {
                if (response.code() == 200) {
                    JSONObject json = new JSONObject(response.body().string());
                    JSONArray data = json.getJSONArray("data");
                    for (int i = 0; i < data.length(); i++) {
                        JSONObject item = data.getJSONObject(i);
                        selectedChallenge = item;
                        LinearLayout myChallengeView = getChallengeView(item, "Join");
                        runOnUiThread(() -> groupChallenges.addView(myChallengeView));
                    }
                } else
                    toast(new JSONObject(response.body().string()).getString("message"));
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
        });
    }

    private void joinChallenge(String challengeId) {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(() -> {
            try (Response response = httpClient.newCall(
                    new Request.Builder().url(BASE_URL + "group/challenge/join?group=" + selectedGroup.getString("id")
                            + "&id=" + challengeId).put(RequestBody.create(new byte[0], null)).build()
            ).execute()) {
                if (response.code() == 200) {
                    runOnUiThread(() -> toast("Challenge joined successfully!"));
                    fillMyChallenges();
                } else {
                    toast(new JSONObject(response.body().string()).getString("message"));
                }
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
        });
    }

    private void fillGroupLeaderboard() {
        LinearLayout groupLeaderboard = findViewById(R.id.groupLeaderboard);
        groupLeaderboard.removeAllViews();
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(() -> {
            try (Response response = httpClient.newCall(new Request.Builder().url(BASE_URL + "group/leaderboard?id=" + selectedGroup.getString("id")).build()).execute()) {
                if (response.code() == 200) {
                    JSONObject json = new JSONObject(response.body().string());
                    JSONArray data = json.getJSONArray("data");
                    for (int i = 0; i < data.length(); i++) {
                        JSONObject item = data.getJSONObject(i);
                        LinearLayout leaderboardView = getLeaderboardView(item);
                        runOnUiThread(() -> groupLeaderboard.addView(leaderboardView));
                    }
                } else
                    toast(new JSONObject(response.body().string()).getString("message"));
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
        });
    }

    private LinearLayout getLeaderboardView(JSONObject member) throws JSONException {
        LinearLayout linearLayout = new LinearLayout(getApplicationContext());
        linearLayout.setPadding(0, 10, 0, 0);
        linearLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        ));
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        TextView nameTextView = new TextView(getApplicationContext());
        nameTextView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        nameTextView.setText(member.getJSONObject("member").getString("username"));
        nameTextView.setTextAlignment(TextView.TEXT_ALIGNMENT_CENTER);
        nameTextView.setTextSize(20);
        linearLayout.addView(nameTextView);
        TextView pointsTextView = new TextView(getApplicationContext());
        pointsTextView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        pointsTextView.setText(String.format("%s Fitness Points", member.getString("fitness_points")));
        pointsTextView.setTextAlignment(TextView.TEXT_ALIGNMENT_CENTER);
        linearLayout.addView(pointsTextView);
        return linearLayout;
    }

    // EDIT GROUP

    public void editGroup(View view) {
        Button button = (Button) view;
        if (button.getText().equals("Create")) {
            editGroup("post");
        } else if (button.getText().equals("Update")) {
            editGroup("put");
        }
    }

    public void deleteGroup(View view) {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(() -> {
            try (Response response = httpClient.newCall(new Request.Builder().url(BASE_URL + "group?id="
                    + selectedGroup.getString("id")).delete().build()).execute()) {
                if (response.isSuccessful()) {
                    runOnUiThread(() -> {
                        toast("Group deleted successfully!");
                        componentManager.switchView("groupsAndChallenges");
                        fillMyGroups();
                    });
                } else {
                    String errorBody = response.body().string();
                    JSONObject errorJson = new JSONObject(errorBody);
                    runOnUiThread(() -> toast(errorJson.optString("message", "An unexpected error occurred.")));
                }
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
        });
    }

    private void editGroup(String requestType) {
        String title = ((TextView) findViewById(R.id.groupTitleField)).getText().toString();
        String description = ((TextView) findViewById(R.id.groupDescription)).getText().toString();
        boolean isPrivate = ((CheckBox) findViewById(R.id.groupPrivate)).isChecked();
        if (title.isEmpty() || description.isEmpty()) {
            toast("Title and description cannot be empty!");
            return;
        }
        MultipartBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("title", title)
                .addFormDataPart("description", description)
                .addFormDataPart("private", String.valueOf(isPrivate))
                .build();
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Request request;
        if (requestType.equals("put")) {
            try {
                request = new Request.Builder().url(BASE_URL + "group?id=" + selectedGroup.getString("id")).put(body).build();
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        } else
            request = new Request.Builder().url(BASE_URL + "group").post(body).build();
        executorService.execute(() -> {
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    selectedGroup = new JSONObject(response.body().string()).getJSONObject("data");
                    runOnUiThread(() -> {
                        toast("Group processed successfully!");
                        ((Button) findViewById(R.id.groupUpdate)).setText("Update");
                    });
                } else
                    toast(new JSONObject(response.body().string()).getString("message"));
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
        });
    }

    private void fillEditGroupChallenges() {
        LinearLayout groupChallenges = findViewById(R.id.groupEditableChallenges);
        groupChallenges.removeAllViews();
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(() -> {
            try (Response response = httpClient.newCall(new Request.Builder().url(BASE_URL +
                    "group/challenge?group=" + selectedGroup.getString("id")).build()).execute()) {
                if (response.code() == 200) {
                    JSONObject json = new JSONObject(response.body().string());
                    JSONArray data = json.getJSONArray("data");
                    for (int i = 0; i < data.length(); i++) {
                        JSONObject item = data.getJSONObject(i);
                        LinearLayout myChallengeView = getChallengeView(item, "Edit");
                        runOnUiThread(() -> groupChallenges.addView(myChallengeView));
                    }
                } else
                    toast(new JSONObject(response.body().string()).getString("message"));
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
        });
    }

    private void fillGroupMembers() {
        LinearLayout groupMembers = findViewById(R.id.groupMembers);
        groupMembers.removeAllViews();
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(() -> {
            try (Response response = httpClient.newCall(new Request.Builder().url(BASE_URL + "group/members?id=" + selectedGroup.getString("id")).build()).execute()) {
                if (response.code() == 200) {
                    JSONObject json = new JSONObject(response.body().string());
                    JSONArray data = json.getJSONArray("data");
                    for (int i = 0; i < data.length(); i++) {
                        JSONObject item = data.getJSONObject(i);
                        LinearLayout myChallengeView = getMemberView(item);
                        runOnUiThread(() -> groupMembers.addView(myChallengeView));
                    }
                } else
                    toast(new JSONObject(response.body().string()).getString("message"));
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
        });
    }

    private LinearLayout getMemberView(JSONObject member) throws JSONException {
        LinearLayout linearLayout = new LinearLayout(getApplicationContext());
        linearLayout.setPadding(0, 10, 0, 0);
        linearLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        ));
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setGravity(android.view.Gravity.CENTER | android.view.Gravity.TOP);
        TextView nameTextView = new TextView(getApplicationContext());
        nameTextView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        nameTextView.setText(member.getString("username"));
        nameTextView.setTextAlignment(TextView.TEXT_ALIGNMENT_CENTER);
        nameTextView.setTextSize(20);
        linearLayout.addView(nameTextView);
        Button kickButton = new Button(getApplicationContext());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(dpToPx(75), dpToPx(32));
        kickButton.setLayoutParams(buttonParams);
        kickButton.setId(View.generateViewId());
        kickButton.setText("Kick");
        String memberId = member.getString("id");
        kickButton.setOnClickListener((View view) -> kickMember(memberId));
        kickButton.setTextSize(10);
        linearLayout.addView(kickButton);
        return linearLayout;
    }

    public void kickMember(String member) {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(() -> {
            try (Response response = httpClient.newCall(new Request.Builder().url(BASE_URL + "group/kick?id="
                    + selectedGroup.getString("id") + "&account=" + member).put(RequestBody.create(new byte[0], null)).build()).execute()) {
                if (response.isSuccessful()) {
                    runOnUiThread(() -> {
                        toast("Member kicked successfully!");
                        fillGroupMembers();;
                    });
                } else {
                    String errorBody = response.body().string();
                    JSONObject errorJson = new JSONObject(errorBody);
                    runOnUiThread(() -> toast(errorJson.optString("message", "An unexpected error occurred.")));
                }
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
        });
    }

    // EDIT CHALLENGE

    public void editChallenge(View view) {
        Button button = (Button) view;
        if (button.getText().equals("Create")) {
            onEditChallenge("post");
        } else if (button.getText().equals("Update")) {
            onEditChallenge("put");
        }
    }

    private void onEditChallenge(String requestType) {
        String title = ((TextView) findViewById(R.id.challengeTitle)).getText().toString();
        String description = ((TextView) findViewById(R.id.challengeDescription)).getText().toString();
        String challengeReward = ((TextView) findViewById(R.id.challengeReward)).getText().toString();
        String completionThreshold = ((TextView) findViewById(R.id.challengeCompletionThreshold)).getText().toString();
        String period = ((TextView) findViewById(R.id.challengePeriod)).getText().toString();
        Spinner thresholdTypeSpinner = findViewById(R.id.challengeThresholdType);
        String thresholdType = thresholdTypeSpinner.getSelectedItem().toString();
        if (title.isEmpty() || description.isEmpty()) {
            toast("Title and description cannot be empty!");
            return;
        }
        MultipartBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("title", title)
                .addFormDataPart("description", description)
                .addFormDataPart("reward", challengeReward)
                .addFormDataPart("threshold", completionThreshold)
                .addFormDataPart("threshold-type", thresholdType)
                .addFormDataPart("period", period)
                .build();
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Request request;
        try {
            if (requestType.equals("put")) {
                request = new Request.Builder().url(BASE_URL + "group/challenge?group=" +
                        selectedGroup.getString("id") + "&id=" + selectedChallenge.getString("id")).put(body).build();
            } else
                request = new Request.Builder().url(BASE_URL + "group/challenge?group=" + selectedGroup.getString("id")).post(body).build();
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        executorService.execute(() -> {
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    selectedChallenge = new JSONObject(response.body().string()).getJSONObject("data");
                    runOnUiThread(() -> {
                        toast("Challenge processed successfully!");
                        ((Button) findViewById(R.id.challengeUpdate)).setText("Update");
                    });
                } else {
                    String errorBody = response.body().string();
                    JSONObject errorJson = new JSONObject(errorBody);
                    runOnUiThread(() -> toast(errorJson.optString("message", "An unexpected error occurred.")));
                }
            } catch (IOException | JSONException e) {
                e.printStackTrace();
                runOnUiThread(() -> toast("An error occurred while editing the challenge."));
            }
        });
    }

    public void deleteChallenge(View view) {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(() -> {
            try (Response response = httpClient.newCall(new Request.Builder().url(BASE_URL + "group/challenge?id=" + selectedChallenge.getString("id")
                    + "&group=" + selectedGroup.getString("id")).delete().build()).execute()) {
                if (response.isSuccessful()) {
                    runOnUiThread(() -> {
                        toast("Challenge deleted successfully!");
                        componentManager.switchView("groupInfo");
                        fillGroupChallenges();
                    });
                } else {
                    String errorBody = response.body().string();
                    JSONObject errorJson = new JSONObject(errorBody);
                    runOnUiThread(() -> toast(errorJson.optString("message", "An unexpected error occurred.")));
                }
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
        });
    }

    private void addValuesToChallengeThresholdSpinner() {
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

    private void toast(String text) {
        runOnUiThread(() -> Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG).show());
    }
}