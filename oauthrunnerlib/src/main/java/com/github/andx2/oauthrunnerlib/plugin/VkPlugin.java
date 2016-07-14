package com.github.andx2.oauthrunnerlib.plugin;

import android.util.Log;

import com.github.andx2.oauthrunnerlib.ConstManager;
import com.github.andx2.oauthrunnerlib.Runner;

import java.util.ArrayList;
import java.util.List;

import static com.github.andx2.oauthrunnerlib.ConstManager.isDebug;

/**
 * Created by savos on 10.07.2016.
 */

public class VkPlugin implements Plugin {

    private final static String BASE_URL = "https://oauth.vk.com/authorize";
    private final static String VERSION_API = "5.52";
    private String clientId;
    private String redirectUri;
    private String state;
    private List<VkScope> scopes;

    private VkPlugin(String clientId, String redirectUri, String state, List<VkScope> scopes) {
        this.clientId = clientId;
        this.redirectUri = redirectUri;
        this.scopes = scopes;
        this.state = state;
    }


    @Override
    public String getUrl() {
        String params = BASE_URL + "?" +
                "client_id=" + clientId +
                "&display=mobile&redirect_uri=" + redirectUri +
                "&scope=" + getScope() + "&response_type=token&v="
                + VERSION_API + "&state=" + state;
        if (isDebug) Log.d(ConstManager.TAG_VK, params);
        return params;
    }

    @Override
    public boolean isContainsBody(String urlString) {
        if (urlString.contains("access_token") && urlString.contains("user_id")) return true;
        return false;
    }

    @Override
    public PluginResponse proceed(String response, Runner.Callback callback, Runner.IsDone isDone) {
        if (isDebug) Log.d(ConstManager.TAG_VK, "proceed(response):" + response);
        String[] partsResponse = response.split("&");
        String accessToken = partsResponse[0].substring(partsResponse[0].indexOf("=") + 1);
        String idUserVK = partsResponse[2].substring(partsResponse[2].indexOf("=") + 1);
        VkResponse vkResponse = new VkResponse(idUserVK, accessToken);
        if (isDebug)
            Log.d(ConstManager.TAG_VK, "vkResponse:" + vkResponse.getId() + "   " + vkResponse.getAccessToken());
        callback.onSuccess(vkResponse);
        if (isDone != null) isDone.done();
        return null;
    }

    @Override
    public void onFailure(String response, Runner.Callback callback) {
        callback.onFailure(response);
    }

    private String getScope() {
        StringBuilder stringBuilder = new StringBuilder();
        for (VkScope scope : scopes) {
            stringBuilder.append(",");
            stringBuilder.append(scope.getValue());
        }
        return stringBuilder.toString().substring(1);
    }

    public enum VkScope {
        OFFLINE("offline"),
        FRIENDS("friends"),
        PHOTOS("photos"),
        AUDIO("audio"),
        VIDEO("video"),
        DOCS("docs"),
        NOTES("notes"),
        PAGES("pages"),
        STATUS("status"),
        WALL("wall"),
        GROUPS("groups"),
        MESSAGES("messages"),
        EMAIL("email"),
        NOTIFICATIONS("notifications"),
        STATS("stats"),
        MARKET("market"),
        NOTIFY("notify");

        private final String value;

        VkScope(String value) {
            this.value = value;
        }

        private String getValue() {
            return value;
        }
    }

    public static class Builder {
        private String clientId;
        private String redirectUri;
        private String state = "";
        private List<VkScope> scopes = null;

        public Builder() {
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public void setRedirectUri(String redirectUri) {
            this.redirectUri = redirectUri;
        }

        public void setState(String state) {
            if (state != null) this.state = state;
        }

        public void setScopes(List<VkScope> scopes) {
            if (scopes != null) this.scopes = scopes;
        }

        public VkPlugin build() {
            if (scopes == null) scopes = new ArrayList<>();
            scopes.add(VkScope.OFFLINE);
            VkPlugin vkPlugin = new VkPlugin(clientId, redirectUri, state, scopes);
            return vkPlugin;
        }
    }

    public class VkResponse extends PluginResponse {
        private String accessToken;
        private String id;

        public VkResponse(String id, String accessToken) {
            this.id = id;
            this.accessToken = accessToken;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public String getId() {
            return id;
        }
    }

}
