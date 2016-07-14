package com.github.andx2.oauthrunnersample;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.github.andx2.oauthrunnerlib.Runner;
import com.github.andx2.oauthrunnerlib.plugin.GitHubPlugin;
import com.github.andx2.oauthrunnerlib.plugin.PluginResponse;
import com.github.andx2.oauthrunnerlib.plugin.VkPlugin;

import static com.github.andx2.oauthrunnerlib.ConstManager.isDebug;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private Button btnCallVk, btnCallGit;
    private EditText etClientId, etRedirectUrl, etClientSecret;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        btnCallVk = (Button) findViewById(R.id.call_vk);
        btnCallVk.setOnClickListener(this);
        btnCallGit = (Button) findViewById(R.id.call_git);
        btnCallGit.setOnClickListener(this);
        etClientId = (EditText) findViewById(R.id.et_client_id);
        etRedirectUrl = (EditText) findViewById(R.id.et_redirect_url);
        etClientSecret = (EditText) findViewById(R.id.et_client_secret);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.call_vk:
                callVkAuth();
                break;
            case R.id.call_git:
                callGitAuth();
                break;
        }

    }

    private void callGitAuth() {
        GitHubPlugin.Builder builder = new GitHubPlugin.Builder();
        builder.setClientId(etClientId.getText().toString());
        builder.setClientSecret(etClientSecret.getText().toString());
        builder.setRedirectUri(etRedirectUrl.getText().toString());
        GitHubPlugin plugin = builder.build();
        Runner runner = new Runner(this, plugin);
        runner.execute(new Runner.Callback() {
            @Override
            public void onSuccess(PluginResponse response) {
                GitHubPlugin.GitHubResponse gitHubResponse = (GitHubPlugin.GitHubResponse) response;
                Log.d("MainLogTag", "Auth success. accessToken = " + gitHubResponse.getAccessToken());
                Toast.makeText(MainActivity.this, "Auth success. accessToken = "
                        + gitHubResponse.getAccessToken(), Toast.LENGTH_LONG).show();
            }

            @Override
            public void onFailure(String failureMessage) {
                Log.d("MainLogTag", "Auth failure. Message = " + failureMessage);
                Toast.makeText(MainActivity.this, "Error Auth with message: " + failureMessage,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void callVkAuth() {
        VkPlugin.Builder builder = new VkPlugin.Builder();
        builder.setClientId(etClientId.getText().toString());
        builder.setRedirectUri(etRedirectUrl.getText().toString());
        VkPlugin plugin = builder.build();
        Runner runner = new Runner(this, plugin);
        runner.execute(new Runner.Callback() {
            @Override
            public void onSuccess(PluginResponse response) {
                VkPlugin.VkResponse vkResponse = (VkPlugin.VkResponse) response;
                if (isDebug) Log.d("MainLogTag", "Auth success. ID = " + vkResponse.getId() +
                        "accessToken = " + vkResponse.getAccessToken());
                Toast.makeText(MainActivity.this, "Auth success. ID = " + vkResponse.getId() +
                        "accessToken = " + vkResponse.getAccessToken(), Toast.LENGTH_LONG).show();

            }

            @Override
            public void onFailure(String failureMessage) {
                if (isDebug) Log.d("MainLogTag", "Auth failure. Message = " + failureMessage);
                Toast.makeText(MainActivity.this, "Error Auth with message: " + failureMessage,
                        Toast.LENGTH_LONG).show();
            }
        });

    }
}
