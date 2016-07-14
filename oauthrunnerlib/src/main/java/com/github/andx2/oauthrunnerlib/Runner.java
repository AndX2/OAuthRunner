package com.github.andx2.oauthrunnerlib;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.github.andx2.oauthrunnerlib.plugin.Plugin;
import com.github.andx2.oauthrunnerlib.plugin.PluginResponse;
import com.github.andx2.oauthrunnerlib.ui.OAuthActivity;

import java.net.MalformedURLException;
import java.net.URL;

import static com.github.andx2.oauthrunnerlib.ConstManager.isDebug;


/**
 * Created by savos on 10.07.2016.
 */

public class Runner {

    private static Runner instance;

    private Context mContext;
    private Plugin mPlugin;
    private WebView mWebView;
    private Callback mCallback;
    private IsDone mIsDone;

    public Runner(Context context, Plugin plugin) {
        this.mContext = context;
        this.mPlugin = plugin;
    }

    public static Runner getInstance() {
        return instance;
    }

    public Callback getCallback() {
        return mCallback;
    }

    public void setDoneCallback(IsDone isDone) {
        this.mIsDone = isDone;
    }

    public void setWebView(WebView webView) {
        this.mWebView = webView;
    }

    public void execute(final Callback callback) {
        this.mCallback = callback;
        if (mWebView == null) {
            instance = this;
            Intent intent = new Intent(mContext, OAuthActivity.class);
            mContext.startActivity(intent);
        } else {

            mWebView.setWebViewClient(new WebViewClient() {
                public boolean shouldOverrideUrlLoading(WebView view, String urlString) {
                    if (isDebug) Log.d(ConstManager.TAG_RUNNER, urlString);
                    String response = "";
                    try {
                        URL url = new URL(urlString);
                        response = url.getRef();
                        if (isDebug) Log.d(ConstManager.TAG_RUNNER, "getRef" + response);
                        if ((urlString != null) && mPlugin.isContainsBody(urlString)) {
                            mPlugin.proceed(urlString, callback, mIsDone);
                        }

                    } catch (MalformedURLException e) {
                        e.printStackTrace();

                    }
                    if (response == null) {
                        return false;
                    }
                    return true;
                }

            });

            mWebView.loadUrl(mPlugin.getUrl());


        }
    }

    public interface Callback {
        void onSuccess(PluginResponse response);

        void onFailure(String failureMessage);
    }

    public interface IsDone {
        void done();
    }


}
