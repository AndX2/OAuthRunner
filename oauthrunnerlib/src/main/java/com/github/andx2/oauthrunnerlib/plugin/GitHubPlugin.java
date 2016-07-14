package com.github.andx2.oauthrunnerlib.plugin;

import android.os.AsyncTask;
import android.util.Log;

import com.github.andx2.oauthrunnerlib.ConstManager;
import com.github.andx2.oauthrunnerlib.Runner;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import static com.github.andx2.oauthrunnerlib.ConstManager.isDebug;

/**
 * Created by savos on 10.07.2016.
 */

public class GitHubPlugin implements Plugin {

    private final static String BASE_URL = "https://github.com/login/oauth/authorize";
    private final static String OAUTH_ACCESS_TOKEN_URL = "https://github.com/login/oauth/access_token";
    private String clientId;
    private String redirectUri;
    private String clientSecret;
    private int timeOutPost = 5000;
    private Runner.Callback mCallback;
    private Runner.IsDone mIsDone;


    private GitHubPlugin(String clientId, String clisntSecret, String redirectUri) {
        this.clientId = clientId;
        this.redirectUri = redirectUri;
        this.clientSecret = clisntSecret;
    }

    public void setTimeOut(int miliSeconds) {
        timeOutPost = miliSeconds;
    }

    @Override
    public String getUrl() {
        String params = BASE_URL + "?" +
                "client_id=" + clientId;
        Log.d(ConstManager.TAG_GIT, params);
        return params;
    }

    @Override
    public boolean isContainsBody(String urlString) {
        if (urlString.contains("code=")) return true;
        return false;
    }

    @Override
    public PluginResponse proceed(String response, Runner.Callback callback, Runner.IsDone isDone) {
        mIsDone = isDone;
        mCallback = callback;
        if (isDebug) Log.d(ConstManager.TAG_GIT, "proceed(response):" + response);
        AsyncPost asyncPost = new AsyncPost();
        String codePart = response.substring(response.indexOf("code=") + "code=".length());
        if (isDebug) Log.d(ConstManager.TAG_GIT, "proceed(codePart):" + codePart);
        String[] strings = {codePart};
        asyncPost.execute(strings);

        return null;
    }

    @Override
    public void onFailure(String response, Runner.Callback callback) {
        mCallback = callback;
        callback.onFailure(response);
    }

    public static class Builder {
        private String clientId;
        private String redirectUri;
        private String clientSecret;

        public Builder() {
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public void setRedirectUri(String redirectUri) {
            this.redirectUri = redirectUri;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public GitHubPlugin build() {
            GitHubPlugin gitHubPlugin = new GitHubPlugin(clientId, clientSecret, redirectUri);
            return gitHubPlugin;
        }
    }


    public class GitHubResponse extends PluginResponse {
        private String accessToken;

        public GitHubResponse(String accessToken) {
            this.accessToken = accessToken;
        }

        public String getAccessToken() {
            return accessToken;
        }

    }

    private class AsyncPost extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... strings) {
            String partCode = strings[0];
            HashMap<String, String> postDataParams = new HashMap<>();
            postDataParams.put("grant_type", "authorization_code");
            postDataParams.put("client_id", clientId);
            postDataParams.put("client_secret", clientSecret);
            postDataParams.put("code", partCode);
            postDataParams.put("redirect_uri", redirectUri);
            try {
                HttpURLConnection httpURLConnection =
                        (HttpURLConnection) new URL(OAUTH_ACCESS_TOKEN_URL).openConnection();
                httpURLConnection.setRequestMethod("POST");
                httpURLConnection.setReadTimeout(timeOutPost);
                httpURLConnection.setConnectTimeout(timeOutPost);
                httpURLConnection.setDoOutput(true);
                httpURLConnection.setDoInput(true);
                OutputStream outputStream = httpURLConnection.getOutputStream();
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(outputStream, "UTF-8"));
                writer.write(getPostDataString(postDataParams));
                writer.flush();
                writer.close();
                outputStream.close();
                if (httpURLConnection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(
                            httpURLConnection.getInputStream()));
                    String inputLine;
                    StringBuffer response = new StringBuffer();

                    while ((inputLine = bufferedReader.readLine()) != null) {
                        response.append(inputLine);
                    }
                    bufferedReader.close();

                    if (isDebug) Log.d(ConstManager.TAG_GIT, "response = " + response.toString());
                    return response.toString();

                } else {
                    if (isDebug) Log.d(ConstManager.TAG_GIT, "POST request not worked");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String response) {
            super.onPostExecute(response);
            if (!response.contains("access_token=")) {
                mCallback.onFailure("Error. response= " + response);
                return;
            }
            String accessToken = response.substring(response.indexOf("access_token=") + "access_token=".length(),
                    response.indexOf("&"));
            GitHubResponse gitHubResponse = new GitHubResponse(accessToken);
            mCallback.onSuccess(gitHubResponse);
            if (mIsDone != null) mIsDone.done();

        }

        private String getPostDataString(HashMap<String, String> params) {
            StringBuilder result = new StringBuilder();
            boolean first = true;
            try {
                for (Map.Entry<String, String> entry : params.entrySet()) {
                    if (first)
                        first = false;
                    else
                        result.append("&");
                    result.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
                    result.append("=");
                    result.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
                }
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                if (isDebug) Log.d(ConstManager.TAG_GIT, "Error getPostDataString");
            }
            if (isDebug) Log.d(ConstManager.TAG_GIT, "getPostDataString = " + result.toString());
            return result.toString();
        }
    }

}
