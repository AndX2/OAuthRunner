# А не послать ли нам гонца?
====
Задумываясь об авторизации в мобильном приложении одной из первых мыслей, как правило, становится вход через соцсеть. Это удобно, да и пользователи уже успели привыкнуть, что не нужно хранить пароль к каждому сервису. Поскольку данная функциональность не является ни для какого приложения ключевой было бы логичным вынести реализацию в отдельный подключаемый модуль. Итак приступим...

##Шаг первый
Для создания модуля приложения нам понадобится... приложение! Создадим новый проект в Android Studio. И, не откладывая в долгий ящик, добавим в него новый модуль: 

![создать модуль](http://s6.uploads.ru/t/WfgBr.png)

Структура проекта должна стать примерно такой:

Обратите внимание, что в подключаемом модуле тоже имеется манифест. При сборке приложения содержимое этого манифеста будет мержиться с главным манифестом. Для нас важно запросить здесь разрешение на использование интернета: 
```groovy
<uses-permission android:name="android.permission.INTERNET">
</uses-permission>
```
##Шаг второй
Зарегистрируем приложение, из которого мы хотели бы проходить авторизацию, на соответствующем сервисе. В моем случае это будут ВКонтакте и GitHub.

###ВКонтакте
Заходим под своим аккаунтом в [раздел создания приложений https://vk.com/apps?act=manage](https://vk.com/apps?act=manage), создаем приложение. Заходим в него, сразу открываем настройки:

!(http://sa.uploads.ru/t/SEMKz.png)
Здесь важно забрать ID нашего приложения, его секретный ключ, и назначить адрес, по которому VK будет перенаправлять пользователя после авторизации. Этот параметр очень важен по двум причинам: во-первых при запросе из приложения осуществляется проверка соответствия redirectURI и, в случае несоответствия, магии не произойдет, а во-вторых ВКонтакт переправит не только самого пользователя по этому адресу но и отдаст туда в заголовке токен доступа. Хорошее решение - http://localhost, ничего из нашего устройства не уйдет.
Схема авторизации здесь очень проста: 
* открываем для пользователя страницу браузера https://oauth.vk.com/authorize с параметрами "client_id", "redirect_uri", "scope=", "VERSION_API", "response_type=token", "state"
* пользователь вводит логин/пароль
* в случае успешной валидации Вконтакте перенаправит браузер по адресу redirectUri c параметром access_token
* перехватываем запрос, забираем токен
* PROFIT

Разберем подробнее первый пункт. "client_id", "redirect_uri" - понятно из названия. VERSION_API - текущая версия 5.52, также поддерживаются более ранние версии для совместимости. Нам не надо. "scope" - права доступа приложения. Здесь лучше просмотреть [полный список](https://new.vk.com/dev/permissions). state - параметр-идентификатор будет добавлен без изменений в параметры редиректа ВКонтакте. Предназначен для определения от какого запроса мы получили токен.

###GitHub
Заходим под своим аккаунтом в настройки 

OAuth application -> Developer applications -> Register a new application

Картина такая же: ClientID, ClientSecret, RedirectURL
Схема авторизации чуть сложнее:
* открываем для пользователя страницу браузера https://github.com/login/oauth/authorize с параметром "client_id"
* в случае успешной валидации GitHub перенаправит браузер по адресу redirectUri c параметром предварительный токен
* перехватываем запрос, забираем предварительный токен
* отправляем POST запрос на https://github.com/login/oauth/access_token с параметрами "client_id", "client_secret", "grant_type=authorization_code", "redirect_uri, "code"(перехваченный предварительный токен)
* в ответ получаем access_token
* PROFIT


##Шаг третий
Техническое задание. Поскольку цель нашего проекта - подключаемый модуль, нам стоит подумать в первую очередь об удобстве его использования. Напишем себе задание:
* никаких внешних зависимостей кроме штатных возможностей SDK
* адаптация под API8...API24 без переконфигурирования
* наличие удобного фасада 
* расширяемость.

Определимся с архитектурой:
* класс-фасад, именно с ним мы будем работать в основном коде. Возврат результата выполним через кастомный интерфейс callback с двумя методами (успешной и неуспешной авторизации)
* частную реализацию для конкретных сервисов вынесем в подключаемые к фасаду плагины. Плагины будем подключать через реализуемый интерфейс. Поскольку создание и настройка плагинов будет заключаться в передаче необходимых и опциональных параметров создание из отдадим на откуп кастомному билдеру.
* ответ вернем в виде специфичного для данного плагина объекта реализующего как минимум один метод - String getAccessToken()
 
##Шаг четвертый
Каркас модуля. 
###Фасад
```java
public class Runner {
    private Context mContext;
    private Plugin mPlugin;
    private WebView mWebView;
    private Callback mCallback;
//В конструкторе передадим контекст (мало ли придется например активити запустить 
//и, разумеется, специфичный плагин по интерфейсу
public Runner(Context context, Plugin plugin) {
        this.mContext = context;
        this.mPlugin = plugin;
    }
//Интерфейс коллбека - возвращать результат
    public interface Callback {
        void onSuccess(PluginResponse response);

        void onFailure(String failureMessage);
    }
//Непосредственно работа с окном браузера
    public void execute(final Callback callback) {
    }
  
```
### Интерфейс плагина
здесь опишем методы взаимодействия фасада с подключенным плагином
```java
//Адрес для браузера
    String getUrl(); 
//Проверка, содержит ли ответ сервиса необходимые нам 
//данные токен или предварительный токен 
    boolean isContainsBody(String urlString); 
//Непосредственный запуск. Передаем строку ответа браузера и коллбек возврата результата.
    PluginResponse proceed(String response, Runner.Callback callback);

```
###Интерфейс результата
```java
public interface PluginResponse {
    String getAccessToken();
}
```

##Шаг пятый
Реализация

Фасад
```java
public class Runner {

    private Context mContext;
    private Plugin mPlugin;
    private WebView mWebView;
    private Callback mCallback;

    public Runner(Context context, Plugin plugin) {
        this.mContext = context;
        this.mPlugin = plugin;
    }

    public Callback getCallback() {
        return mCallback;
    }
//Передадим окно браузера, в котором будем проводить авторизацию
    public void setWebView(WebView webView) {
        this.mWebView = webView;
    }
//
    public void execute(final Callback callback) {
        this.mCallback = callback;
//Назначим нашему браузеру перехватчик переходов
            mWebView.setWebViewClient(new WebViewClient() {
                public boolean shouldOverrideUrlLoading(WebView view, String urlString) {
                    if (isDebug) Log.d(ConstManager.TAG_RUNNER, urlString);
//Проверяем содержит ли адрес перехода нужный нам параметр и запускаем выполнение 
//обрабочика из плагина
                        if ((urlString != null) && mPlugin.isContainsBody(urlString)) {
                            mPlugin.proceed(urlString, callback, mIsDone);
                        
                    }
//Возвращаемое в этом методе значение - указание браузеру обрабатывать ли
//переход самостоятельно. true - обрабатывать браузером
                    return true;
                }

            });
//Открываем в браузере страницу авторизации
            mWebView.loadUrl(mPlugin.getUrl());

        }
    }

}
```

VkPlugin
```java
public class VkPlugin implements Plugin {

    private final static String BASE_URL = "https://oauth.vk.com/authorize";
    private final static String VERSION_API = "5.52";
    private String clientId;
    private String redirectUri;
    private String state;
    private List<VkScope> scopes;
//Приватный конструктор нужен для билдера
    private VkPlugin(String clientId, String redirectUri, String state, List<VkScope> scopes) {
        this.clientId = clientId;
        this.redirectUri = redirectUri;
        this.scopes = scopes;
        this.state = state;
    }
//Отбает адрес страницы авторизации для браузера
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
//Проверка успешной авторизации по содержимому параметра
    @Override
    public boolean isContainsBody(String urlString) {
        if (urlString.contains("access_token") && urlString.contains("user_id")) return true;
        return false;
    }
//Обработаем ответ ВКонтакте
    @Override
    public PluginResponse proceed(String response, Runner.Callback callback, Runner.IsDone isDone) {
//Разделим ответ на массив строк, содержащих по одному параметру
        String[] partsResponse = response.split("&");
//Извлечем токен
        String accessToken = partsResponse[0].substring(partsResponse[0].indexOf("=") + 1);
//Извлечем пользователя
        String idUserVK = partsResponse[2].substring(partsResponse[2].indexOf("=") + 1);
//Сохраним результаты в объект - результат авторизации
        VkResponse vkResponse = new VkResponse(idUserVK, accessToken);
//Передадим результат в вызвавший метод
        callback.onSuccess(vkResponse);
        return null;
    }

    private String getScope() {
        StringBuilder stringBuilder = new StringBuilder();
        for (VkScope scope : scopes) {
            stringBuilder.append(",");
            stringBuilder.append(scope.getValue());
        }
        return stringBuilder.toString().substring(1);
    }
    
//Для удобства использования определим все допустимые значения scope
//в виде перечисления

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
//Билдер для сборки плагина ВК
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
//Реализация объекта-ответа об успешной авторизации в ВК. Простой объект ID и токен. 
    public class VkResponse implements PluginResponse {
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
```

GitHubPlugin
```java
public class GitHubPlugin implements Plugin {

    private final static String BASE_URL = "https://github.com/login/oauth/authorize";
    private final static String OAUTH_ACCESS_TOKEN_URL = "https://github.com/login/oauth/access_token";
    private String clientId;
    private String redirectUri;
    private String clientSecret;
    private int timeOutPost = 5000;
    private Runner.Callback mCallback;
//Приватный конструктор для билдера
    private GitHubPlugin(String clientId, String clisntSecret, String redirectUri) {
        this.clientId = clientId;
        this.redirectUri = redirectUri;
        this.clientSecret = clisntSecret;
    }

    public void setTimeOut(int miliSeconds) {
        timeOutPost = miliSeconds;
    }
//Возвращает адрес авторизации на GitHub для браузера
    @Override
    public String getUrl() {
        String params = BASE_URL + "?" +
                "client_id=" + clientId;
        return params;
    }
//Проверяет содержит ли ответ временный код (при успешной авторизации)
    @Override
    public boolean isContainsBody(String urlString) {
        if (urlString.contains("code=")) return true;
        return false;
    }
//Обработаем ответ GitHub
    @Override
    public PluginResponse proceed(String response, Runner.Callback callback) {
//"повысим" коллбек до поля класса
        mCallback = callback;
//Обращиться к сети необходимо в отдельном потоке. Сделаем это с помощью кастомного класса
//AsyncPost - наследника AsyncTask
        AsyncPost asyncPost = new AsyncPost();
//Извлечем из ответа GitHub временный токен, упакуем в первый элемент массива строк и 
//передадим на обработку в AsyncPost
        String codePart = response.substring(response.indexOf("code=") + "code=".length());
        String[] strings = {codePart};
        asyncPost.execute(strings);

        return null;
    }

    @Override
    public void onFailure(String response, Runner.Callback callback) {
        mCallback = callback;
        callback.onFailure(response);
    }
//Реализация билдера для GitHubPlugin
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

//Реализация ответа об успешной авторизации. Содержит только токен.
    public class GitHubResponse implements PluginResponse {
        private String accessToken;

        public GitHubResponse(String accessToken) {
            this.accessToken = accessToken;
        }

        public String getAccessToken() {
            return accessToken;
        }
    }
    
//Внутренний класс для отправки POST запроса принимает массив строк, содержащий временный токен
    private class AsyncPost extends AsyncTask<String, Void, String> {

//Метод выполняемый в параллельном потоке - содержит всю работу с сетью
        @Override
        protected String doInBackground(String... strings) {
//Соберем все параметры в словарь ключ/значение
            String partCode = strings[0];
            HashMap<String, String> postDataParams = new HashMap<>();
            postDataParams.put("grant_type", "authorization_code");
            postDataParams.put("client_id", clientId);
            postDataParams.put("client_secret", clientSecret);
            postDataParams.put("code", partCode);
            postDataParams.put("redirect_uri", redirectUri);
//Перехватим ошибки работы с сетью - возврат пустой строки в onPostExecute вызовем onFailure в коллбеке
            try {
//Откроем соединение, объявим метод запроса и другие параметры
                HttpURLConnection httpURLConnection =
                        (HttpURLConnection) new URL(OAUTH_ACCESS_TOKEN_URL).openConnection();
                httpURLConnection.setRequestMethod("POST");
                httpURLConnection.setReadTimeout(timeOutPost);
                httpURLConnection.setConnectTimeout(timeOutPost);
                httpURLConnection.setDoOutput(true);
                httpURLConnection.setDoInput(true);
//Соберем словарь параметров в одну строку и буферизованным потоком запишем в выходной поток соединения
                OutputStream outputStream = httpURLConnection.getOutputStream();
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(outputStream, "UTF-8"));
                writer.write(getPostDataString(postDataParams));
                writer.flush();
                writer.close();
                outputStream.close();
//В случае успешного ответа сервера создадим запишем входной поток в строку и завершим метод
                if (httpURLConnection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(
                            httpURLConnection.getInputStream()));
                    String inputLine;
                    StringBuffer response = new StringBuffer();

                    while ((inputLine = bufferedReader.readLine()) != null) {
                        response.append(inputLine);
                    }
                    bufferedReader.close();

                    return response.toString();

                } else {
                    if (isDebug) Log.d(ConstManager.TAG_GIT, "POST request not worked");
                    return ""
//В случае отказа сервера вернем пустую строку и в onPostExecute вызовем onFailure в коллбеке
                }
            } catch (IOException e) {
                e.printStackTrace();
                return "";
            }
            return "";
        }
//Метод выполняется после doInBackground в главном потоке
        @Override
        protected void onPostExecute(String response) {
            super.onPostExecute(response);
//В зависимости от содержимого ответа сервера либо вызовем onFailure в коллбеке или 
//создададим объект ответа и передадим его в метод onSuccess коллбека
            if (!response.contains("access_token=")) {
                mCallback.onFailure("Error. response= " + response);
                return;
            }
            String accessToken = response.substring(response.indexOf("access_token=") + "access_token=".length(),
                    response.indexOf("&"));
            GitHubResponse gitHubResponse = new GitHubResponse(accessToken);
            mCallback.onSuccess(gitHubResponse);

        }
//Вспомогательный метод сборки всех параметров POST запроса водну строку
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
```

##Шаг шестой
В принципе наш модуль соответствует и, формально, работа сделана. Однако, для совсем ленивых будующих пользователей нашего модуля, давайте сделаем параметр mWebView в Фасаде необязательным и, в случае его отсутствия при запуске метода execute() развернем своё активити с окном браузера.

Добавим активити в манифест модуля
```groovy
<activity android:name=".ui.OAuthActivity">
</activity>
```

Разметка активити - все пространство окно браузера
```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
             android:layout_width="match_parent"
             android:layout_height="match_parent">
    <WebView
        android:id="@+id/auth_web_view"
        android:layout_width="match_parent"
        android:layout_height="match_parent"/>
</FrameLayout>
```

Добавим в начало метода execute() нашего фасада проверку существования mWebView и в случае его отсутствия запустим свой активити

Runner
```java
public void execute(final Callback callback) {
        this.mCallback = callback;
        if (mWebView == null) {
            instance = this;
            Intent intent = new Intent(mContext, OAuthActivity.class);
            mContext.startActivity(intent);
        } else {
```

Добавим еще один интерфейс коллбека в фасад. В его методе void done() будем передавать событие окончания обработки сетевых запросов и, при успешном исходе, закрывать наш активити. Добавим этот коллбек в список аргументов метода proceed() интерфейса Plugin и реализующих его классов GitHubPlugin и VkPlugin. Таким образом в конце выполнения этого метода мы сможем автоматически закрывать активити.

GitHubPlugin и VkPlugin
```java
@Override
    public PluginResponse proceed(String response, Runner.Callback callback, Runner.IsDone isDone){}
    ...
    if (isDone != null) isDone.done();
```

Runner
```java
public interface IsDone {
        void done();
    }
```




OAuthActivity
```java
public class OAuthActivity extends AppCompatActivity {

    Runner mRunner;
    private WebView mWebView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_oauth);
        mWebView = (WebView) findViewById(R.id.auth_web_view);
    }

    @Override
    protected void onResume() {
        super.onResume();
//Очень важно стереть данные предыдущих запусков браузера поскольку и VK и GitHub записывают
//сведения об авторизации пользователя в куки
        clearCookies();

        mRunner = Runner.getInstance();
        mRunner.setWebView(mWebView);
        mRunner.execute(mRunner.getCallback());
        mRunner.setDoneCallback(new Runner.IsDone() {
            @Override
            public void done() {
                onBackPressed();
            }
        });
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }
//Очистка данных браузера о предыдущих запусках
    public void clearCookies() {
        CookieManager cookieManager = CookieManager.getInstance();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.removeAllCookies(null);
        } else {
            cookieManager.removeAllCookie();
        }
    }
}
```

##Шаг седьмой
Модуль авторизации готов. Для демонстрации его работы хорошо бы сделать простенькое приложение из одного активити. 

MainActivity
```java
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
                Toast.makeText(MainActivity.this, "Auth success. ID = " + vkResponse.getId() +
                        "accessToken = " + vkResponse.getAccessToken(), Toast.LENGTH_LONG).show();
            }

            @Override
            public void onFailure(String failureMessage) {
                Toast.makeText(MainActivity.this, "Error Auth with message: " + failureMessage,
                        Toast.LENGTH_LONG).show();
            }
        });

    }
}
```

Полная структура приложения

Для подключения модуля к приложению в скрипт сборки добавить зависимость на него:
```groovy
dependencies {
    compile project(path: ':oauthrunnerlib')
}
```


Все исходники [здесь https://github.com/AndX2/OAuthRunner](https://github.com/AndX2/OAuthRunner)

