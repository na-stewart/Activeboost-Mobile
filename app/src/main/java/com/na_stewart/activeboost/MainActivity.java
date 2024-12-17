package com.na_stewart.activeboost;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
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
import okhttp3.MediaType;
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

        addButtonListeners();

    }

    private void addButtonListeners() {
        // Single reference for the groupUpdate button
        Button groupUpdateButton = findViewById(R.id.groupUpdate);

        // Assign listener dynamically
        groupUpdateButton.setOnClickListener(view -> {
            if ("Create".equals(groupUpdateButton.getText().toString())) {
                onPostGroup(view);
            } else if ("Update".equals(groupUpdateButton.getText().toString())) {
                onPutGroup(view);
            }
        });

        // Listener for Create Challenge button
        Button createChallengeButton = findViewById(R.id.challengeUpdate);
        createChallengeButton.setOnClickListener(this::onPostChallenge);

        // Listener for Update Challenge button
        Button updateChallengeButton = findViewById(R.id.challengeUpdate);
        updateChallengeButton.setOnClickListener(view -> {
            if ("Create".equals(updateChallengeButton.getText().toString())) {
                onPostChallenge(view);
            } else if ("Update".equals(updateChallengeButton.getText().toString())) {
                onPutChallenge(view);
            }
        });

        // Navigation buttons
        findViewById(R.id.homeNav).setOnClickListener(this::onNavigate);
        findViewById(R.id.activeNav).setOnClickListener(this::onNavigate);
        findViewById(R.id.profileNav).setOnClickListener(this::onNavigate);

        // Login and Logout buttons
        findViewById(R.id.loginButton).setOnClickListener(this::login);
        findViewById(R.id.logout).setOnClickListener(this::logout);

        // Create Challenge navigation
        findViewById(R.id.createChallenge).setOnClickListener(this::onCreateChallenge);

        // Create Group navigation
        findViewById(R.id.createGroup).setOnClickListener(this::onCreateGroup);
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
        LinearLayout myChallengesContainer = findViewById(R.id.yourChallenges);
        myChallengesContainer.removeAllViews();

        // Execute the network request on a background thread
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(() -> {
            try {
                String url = BASE_URL + "group/challenge/you";
                Response response = httpClient.newCall(new Request.Builder().url(url).build()).execute();

                if (response.code() == 200) {
                    JSONObject json = new JSONObject(response.body().string());
                    JSONArray challenges = json.getJSONArray("data");

                    for (int i = 0; i < challenges.length(); i++) {
                        JSONObject challenge = challenges.getJSONObject(i);
                        LinearLayout challengeView = getChallengeView(challenge);
                        runOnUiThread(() -> myChallengesContainer.addView(challengeView));
                    }
                } else {
                    String errorBody = response.body().string();
                    JSONObject errorJson = new JSONObject(errorBody);
                    runOnUiThread(() -> toast(errorJson.optString("message", "Failed to fetch your challenges")));
                }
            } catch (IOException | JSONException e) {
                e.printStackTrace();
                runOnUiThread(() -> toast("An error occurred while fetching your challenges."));
            }
        });
    }

    private LinearLayout getMyChallengeView(JSONObject challenge) throws JSONException {
        LinearLayout parentLayout = new LinearLayout(getApplicationContext());
        parentLayout.setOrientation(LinearLayout.VERTICAL);
        parentLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        parentLayout.setPadding(dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10));

        // Title TextView
        TextView titleTextView = new TextView(getApplicationContext());
        titleTextView.setText(challenge.getString("title"));
        titleTextView.setTextSize(18);
        parentLayout.addView(titleTextView);

        // Description TextView
        TextView descriptionTextView = new TextView(getApplicationContext());
        descriptionTextView.setText(challenge.getString("description"));
        descriptionTextView.setTextSize(14);
        parentLayout.addView(descriptionTextView);

        // Reward TextView
        TextView rewardTextView = new TextView(getApplicationContext());
        rewardTextView.setText("Reward: " + challenge.getString("reward"));
        rewardTextView.setTextSize(14);
        parentLayout.addView(rewardTextView);

        // Redeem Button
        Button redeemButton = new Button(getApplicationContext());
        redeemButton.setText("Redeem");
        redeemButton.setOnClickListener(v -> redeemChallenge(challenge));
        parentLayout.addView(redeemButton);

        return parentLayout;
    }

    // PROFILE



    // GROUP INFO

    public void onCreateChallenge(View view) {
        componentManager.switchView("editChallenge");
        selectedChallengeId = 0;
        ((Button) findViewById(R.id.challengeUpdate)).setText("Create");
    }

    public void onEditGroup(View view) {
        // Opens edit group window, not used for API call.
        // selectedGroupId set via info button clicked in groups & challenges window.
        componentManager.switchView("editGroup");
        ((Button) findViewById(R.id.groupUpdate)).setText("Update");
    }

    private void fillLeaderboard() {
        LinearLayout leaderboardContainer = findViewById(R.id.groupLeaderboard);
        leaderboardContainer.removeAllViews();

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(() -> {
            try {
                String url = BASE_URL + "group/leaderboard?id=" + selectedGroupId;
                Response response = httpClient.newCall(new Request.Builder().url(url).build()).execute();

                if (response.code() == 200) {
                    JSONObject json = new JSONObject(response.body().string());
                    JSONArray participants = json.getJSONArray("data");

                    for (int i = 0; i < participants.length(); i++) {
                        JSONObject participant = participants.getJSONObject(i);
                        LinearLayout participantView = getLeaderboardView(participant);
                        runOnUiThread(() -> leaderboardContainer.addView(participantView));
                    }
                } else {
                    String errorBody = response.body().string();
                    JSONObject errorJson = new JSONObject(errorBody);
                    runOnUiThread(() -> toast(errorJson.optString("message", "Failed to fetch leaderboard")));
                }
            } catch (IOException | JSONException e) {
                e.printStackTrace();
                runOnUiThread(() -> toast("An error occurred while fetching leaderboard."));
            }
        });
    }

    private LinearLayout getLeaderboardView(JSONObject participant) throws JSONException {
        LinearLayout parentLayout = new LinearLayout(getApplicationContext());
        parentLayout.setOrientation(LinearLayout.HORIZONTAL);
        parentLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        parentLayout.setPadding(dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10));

        // Participant Name
        TextView nameTextView = new TextView(getApplicationContext());
        nameTextView.setText(participant.getString("name"));
        nameTextView.setTextSize(16);
        parentLayout.addView(nameTextView);

        // Participant Score
        TextView scoreTextView = new TextView(getApplicationContext());
        scoreTextView.setText("Points: " + participant.getString("points"));
        scoreTextView.setTextSize(16);
        parentLayout.addView(scoreTextView);

        return parentLayout;
    }

    private void fillGroupChallenges() {
        LinearLayout groupChallengesContainer = findViewById(R.id.groupChallenges);
        groupChallengesContainer.removeAllViews();

        // Execute the network request on a background thread
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(() -> {
            try {
                String url = BASE_URL + "group/challenge?group=" + selectedGroupId;
                Response response = httpClient.newCall(new Request.Builder().url(url).build()).execute();

                if (response.code() == 200) {
                    JSONObject json = new JSONObject(response.body().string());
                    JSONArray challenges = json.getJSONArray("data");

                    for (int i = 0; i < challenges.length(); i++) {
                        JSONObject challenge = challenges.getJSONObject(i);
                        LinearLayout challengeView = getChallengeView(challenge);
                        runOnUiThread(() -> groupChallengesContainer.addView(challengeView));
                    }
                } else {
                    String errorBody = response.body().string();
                    JSONObject errorJson = new JSONObject(errorBody);
                    runOnUiThread(() -> toast(errorJson.optString("message", "Failed to fetch challenges")));
                }
            } catch (IOException | JSONException e) {
                e.printStackTrace();
                runOnUiThread(() -> toast("An error occurred while fetching challenges."));
            }
        });
    }

    private LinearLayout getChallengeView(JSONObject challenge) throws JSONException {
        LinearLayout parentLayout = new LinearLayout(getApplicationContext());
        parentLayout.setOrientation(LinearLayout.VERTICAL);
        parentLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        parentLayout.setPadding(dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10));

        // Title TextView
        TextView titleTextView = new TextView(getApplicationContext());
        titleTextView.setText(challenge.getString("title"));
        titleTextView.setTextSize(18);
        parentLayout.addView(titleTextView);

        // Description TextView
        TextView descriptionTextView = new TextView(getApplicationContext());
        descriptionTextView.setText(challenge.getString("description"));
        descriptionTextView.setTextSize(14);
        parentLayout.addView(descriptionTextView);

        // Reward TextView
        TextView rewardTextView = new TextView(getApplicationContext());
        rewardTextView.setText("Reward: " + challenge.getString("reward"));
        rewardTextView.setTextSize(14);
        parentLayout.addView(rewardTextView);

        // Completion Threshold TextView
        TextView thresholdTextView = new TextView(getApplicationContext());
        thresholdTextView.setText("Threshold: " + challenge.getString("completion_threshold") + " " +
                challenge.getString("threshold_type"));
        thresholdTextView.setTextSize(14);
        parentLayout.addView(thresholdTextView);

        // Expiration Date TextView
        TextView expirationDateTextView = new TextView(getApplicationContext());
        expirationDateTextView.setText("Expires on: " + challenge.getString("expiration_date"));
        expirationDateTextView.setTextSize(14);
        parentLayout.addView(expirationDateTextView);

        // Edit or Join Button
        Button actionButton = new Button(getApplicationContext());
        actionButton.setText("Join");
        actionButton.setOnClickListener(v -> joinChallenge(challenge));
        parentLayout.addView(actionButton);

        return parentLayout;
    }

    private void joinChallenge(JSONObject challenge) {
        try {
            String challengeId = challenge.getString("id");
            String url = BASE_URL + "group/challenge/join?id=" + challengeId;

            // Execute the network request on a background thread
            ExecutorService executorService = Executors.newSingleThreadExecutor();
            executorService.execute(() -> {
                try (Response response = httpClient.newCall(
                        new Request.Builder().url(url).post(RequestBody.create(new byte[0], null)).build()
                ).execute()) {
                    if (response.code() == 200) {
                        runOnUiThread(() -> toast("Successfully joined the challenge!"));
                        fillGroupChallenges(); // Refresh challenges
                    } else {
                        String errorBody = response.body().string();
                        JSONObject errorJson = new JSONObject(errorBody);
                        runOnUiThread(() -> toast(errorJson.optString("message", "Failed to join the challenge")));
                    }
                } catch (IOException | JSONException e) {
                    e.printStackTrace();
                    runOnUiThread(() -> toast("An error occurred while joining the challenge."));
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
            toast("Invalid challenge data.");
        }
    }

    private void redeemChallenge(JSONObject challenge) {
        try {
            String challengeId = challenge.getString("id");
            String url = BASE_URL + "group/challenge/redeem?id=" + challengeId;

            // Execute the network request on a background thread
            ExecutorService executorService = Executors.newSingleThreadExecutor();
            executorService.execute(() -> {
                try (Response response = httpClient.newCall(
                        new Request.Builder().url(url).post(RequestBody.create(new byte[0], null)).build()
                ).execute()) {
                    if (response.code() == 200) {
                        runOnUiThread(() -> toast("Challenge redeemed successfully!"));
                        fillMyChallenges(); // Refresh challenges
                    } else {
                        String errorBody = response.body().string();
                        JSONObject errorJson = new JSONObject(errorBody);
                        runOnUiThread(() -> toast(errorJson.optString("message", "Failed to redeem the challenge")));
                    }
                } catch (IOException | JSONException e) {
                    e.printStackTrace();
                    runOnUiThread(() -> toast("An error occurred while redeeming the challenge."));
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
            toast("Invalid challenge data.");
        }
    }

    private LinearLayout getGroupChallengesView(JSONObject challenge) throws JSONException {
        LinearLayout parentLayout = new LinearLayout(getApplicationContext());
        parentLayout.setOrientation(LinearLayout.VERTICAL);
        parentLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        parentLayout.setPadding(dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10));

        // Title
        TextView titleTextView = new TextView(getApplicationContext());
        titleTextView.setText(challenge.getString("title"));
        titleTextView.setTextSize(18);
        parentLayout.addView(titleTextView);

        // Description
        TextView descriptionTextView = new TextView(getApplicationContext());
        descriptionTextView.setText(challenge.getString("description"));
        descriptionTextView.setTextSize(14);
        parentLayout.addView(descriptionTextView);

        // Reward
        TextView rewardTextView = new TextView(getApplicationContext());
        rewardTextView.setText("Reward: " + challenge.getString("reward"));
        rewardTextView.setTextSize(14);
        parentLayout.addView(rewardTextView);

        // Edit Button
        Button editButton = new Button(getApplicationContext());
        editButton.setText("Edit");
        editButton.setOnClickListener(v -> openEditChallenge(challenge));
        parentLayout.addView(editButton);

        return parentLayout;
    }

    private void openEditChallenge(JSONObject challenge) {
        try {
            selectedChallengeId = challenge.getInt("id");

            // Populate fields with existing data
            ((TextView) findViewById(R.id.challengeTitle)).setText(challenge.getString("title"));
            ((TextView) findViewById(R.id.challengeDescription)).setText(challenge.getString("description"));
            ((TextView) findViewById(R.id.challengeReward)).setText(challenge.getString("reward"));
            ((TextView) findViewById(R.id.challengeCompletionThreshold)).setText(challenge.getString("completion_threshold"));
            ((Spinner) findViewById(R.id.challengeThresholdType)).setSelection(
                    getThresholdTypeIndex(challenge.getString("threshold_type"))
            );
            ((TextView) findViewById(R.id.challengePeriod)).setText(challenge.getString("expiration_days"));

            // Switch to edit challenge view
            componentManager.switchView("editChallenge");
            ((Button) findViewById(R.id.challengeUpdate)).setText("Update");
        } catch (JSONException e) {
            e.printStackTrace();
            toast("Failed to load challenge data for editing.");
        }
    }

    // Helper method to get the index of a threshold type
    private int getThresholdTypeIndex(String thresholdType) {
        List<String> spinnerValues = List.of("steps", "distance", "calories", "minutesVeryActive", "minutesFairlyActive", "floors");
        int index = spinnerValues.indexOf(thresholdType);
        return index == -1 ? 0 : index; // Default to the first item if not found
    }

    // EDIT GROUP

    public void onPutGroup(View view) {
        String title = ((TextView) findViewById(R.id.groupTitleField)).getText().toString();
        String description = ((TextView) findViewById(R.id.groupDescription)).getText().toString();
        boolean isPrivate = ((CheckBox) findViewById(R.id.checkBox)).isChecked();

        if (title.isEmpty() || description.isEmpty()) {
            toast("Title and description cannot be empty!");
            return;
        }

        MultipartBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("id", String.valueOf(selectedGroupId))
                .addFormDataPart("title", title)
                .addFormDataPart("description", description)
                .addFormDataPart("private", String.valueOf(isPrivate))
                .build();

        Request request = new Request.Builder()
                .url(BASE_URL + "group")
                .put(body)
                .build();

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(() -> {
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    runOnUiThread(() -> {
                        toast("Group updated successfully!");
                        fillMyGroups();
                        componentManager.switchView("groupsAndChallenges");
                    });
                } else {
                    String errorBody = response.body().string();
                    JSONObject errorJson = new JSONObject(errorBody);
                    runOnUiThread(() -> toast(errorJson.optString("message", "Failed to update group")));
                }
            } catch (IOException | JSONException e) {
                e.printStackTrace();
                runOnUiThread(() -> toast("An error occurred while updating the group."));
            }
        });
    }

//    public void onPostGroup(View view) {
//        // Method triggered by button for creating group.
//        // https://activeboost.na-stewart.com/api/v1/group POST
//        // Form data: title, description, private.
//        selectedGroupId = 0; // Set to returned group id when created.
//    }

    public void onPostGroup(View view) {
        // Retrieve inputs from the form
        String title = ((TextView) findViewById(R.id.groupTitleField)).getText().toString();
        String description = ((TextView) findViewById(R.id.groupDescription)).getText().toString();
        boolean isPrivate = ((CheckBox) findViewById(R.id.checkBox)).isChecked();

        // Validate inputs
        if (title.isEmpty() || description.isEmpty()) {
            toast("Title and description cannot be empty!");
            return;
        }

        // Build the form-data body
        MultipartBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("title", title)
                .addFormDataPart("description", description)
                .addFormDataPart("private", String.valueOf(isPrivate))
                .build();

        // Create the HTTP POST request
        Request request = new Request.Builder()
                .url(BASE_URL + "group")
                .post(body)
                .build();

        // Execute the request in a separate thread
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(() -> {
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    // Parse the success response
                    JSONObject responseBody = new JSONObject(response.body().string());
                    runOnUiThread(() -> {
                        toast("Group created successfully!");

                        // Clear the input fields
                        ((TextView) findViewById(R.id.groupTitleField)).setText("");
                        ((TextView) findViewById(R.id.groupDescription)).setText("");
                        ((CheckBox) findViewById(R.id.checkBox)).setChecked(false);

                        // Refresh the groups list and switch to the home view
                        fillPublicGroups();
                        componentManager.switchView("home");
                    });
                } else {
                    // Handle server error response
                    String errorBody = response.body().string();
                    JSONObject errorJson = new JSONObject(errorBody);
                    runOnUiThread(() -> toast(errorJson.optString("message", "Failed to create group")));
                }
            } catch (IOException | JSONException e) {
                // Handle network or JSON parsing errors
                e.printStackTrace();
                runOnUiThread(() -> toast("An error occurred while creating the group."));
            }
        });
    }

    // EDIT CHALLENGE

    public void onPutChallenge(View view) {
        String title = ((TextView) findViewById(R.id.challengeTitle)).getText().toString();
        String description = ((TextView) findViewById(R.id.challengeDescription)).getText().toString();
        String reward = ((TextView) findViewById(R.id.challengeReward)).getText().toString();
        String completionThreshold = ((TextView) findViewById(R.id.challengeCompletionThreshold)).getText().toString();
        String thresholdType = ((Spinner) findViewById(R.id.challengeThresholdType)).getSelectedItem().toString();
        String expirationDays = ((TextView) findViewById(R.id.challengePeriod)).getText().toString();

        if (title.isEmpty() || description.isEmpty() || reward.isEmpty() || completionThreshold.isEmpty() || expirationDays.isEmpty()) {
            toast("All fields are required!");
            return;
        }

        MultipartBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("title", title)
                .addFormDataPart("description", description)
                .addFormDataPart("reward", reward)
                .addFormDataPart("completion_threshold", completionThreshold)
                .addFormDataPart("threshold_type", thresholdType) // Ensure this is correct
                .addFormDataPart("expiration_days", expirationDays)
                .build();

        String url = BASE_URL + "group/challenge?group=" + selectedGroupId;

        Request request = new Request.Builder()
                .url(url)
                .put(body)
                .build();

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(() -> {
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    runOnUiThread(() -> {
                        toast("Challenge updated successfully!");
                        fillGroupChallenges();
                        componentManager.switchView("groupInfo");
                    });
                } else {
                    String errorBody = response.body().string();
                    JSONObject errorJson = new JSONObject(errorBody);
                    runOnUiThread(() -> toast(errorJson.optString("message", "Failed to update challenge")));
                }
            } catch (IOException | JSONException e) {
                e.printStackTrace();
                runOnUiThread(() -> toast("An error occurred while updating the challenge."));
            }
        });
    }
    public void onPostChallenge(View view) {
        String title = ((TextView) findViewById(R.id.challengeTitle)).getText().toString();
        String description = ((TextView) findViewById(R.id.challengeDescription)).getText().toString();
        String reward = ((TextView) findViewById(R.id.challengeReward)).getText().toString();
        String completionThreshold = ((TextView) findViewById(R.id.challengeCompletionThreshold)).getText().toString();
        String thresholdType = ((Spinner) findViewById(R.id.challengeThresholdType)).getSelectedItem().toString();
        Log.d("ChallengeDebug", "Selected threshold_type: " + thresholdType);
        String expirationDays = ((TextView) findViewById(R.id.challengePeriod)).getText().toString();

        // Validate inputs
        if (title.isEmpty() || description.isEmpty() || reward.isEmpty() || completionThreshold.isEmpty() || expirationDays.isEmpty()) {
            toast("All fields are required!");
            return;
        }

        List<String> validThresholdTypes = List.of("calories", "distance", "elevation", "floors", "minutesVeryActive", "minutesFairlyActive");
        if (!validThresholdTypes.contains(thresholdType)) {
            toast("Invalid threshold type selected!");
            return;
        }

        if (selectedGroupId == 0) {
            toast("Please select a valid group before creating a challenge.");
            return;
        }

        // Build the form-data body
        MultipartBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("title", title)
                .addFormDataPart("description", description)
                .addFormDataPart("reward", reward)
                .addFormDataPart("threshold", completionThreshold)
                .addFormDataPart("threshold-type", thresholdType)
                .addFormDataPart("period", expirationDays)
                .build();

        String url = BASE_URL + "group/challenge?group=" + selectedGroupId;

        // Send the request
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(() -> {
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    JSONObject responseBody = new JSONObject(response.body().string());
                    runOnUiThread(() -> {
                        toast("Challenge created successfully!");
                        fillGroupChallenges();
                        componentManager.switchView("groupInfo");
                    });
                } else {
                    String errorBody = response.body().string();
                    JSONObject errorJson = new JSONObject(errorBody);
                    runOnUiThread(() -> toast(errorJson.optString("message", "Failed to create challenge")));
                }
            } catch (IOException | JSONException e) {
                e.printStackTrace();
                runOnUiThread(() -> toast("An error occurred while creating the challenge."));
            }
        });
    }

    private void addValuesToChallengeThresholdSpinner() {
        List<String> spinnerValues = List.of(
                "calories",
                "distance",
                "elevation",
                "floors",
                "minutesVeryActive",
                "minutesFairlyActive",
                "steps"
        );
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