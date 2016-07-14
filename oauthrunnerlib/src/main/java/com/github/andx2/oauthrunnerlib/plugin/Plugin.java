package com.github.andx2.oauthrunnerlib.plugin;

import com.github.andx2.oauthrunnerlib.Runner;

/**
 * Created by savos on 10.07.2016.
 */

public interface Plugin {

    String getUrl();

    boolean isContainsBody(String urlString);

    PluginResponse proceed(String response, Runner.Callback callback, Runner.IsDone isDone);

    void onFailure(String response, Runner.Callback callback);
}
