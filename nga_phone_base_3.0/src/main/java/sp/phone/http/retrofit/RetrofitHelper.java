package sp.phone.http.retrofit;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.webkit.CookieManager;
import android.webkit.WebSettings;

import java.net.URLDecoder;
import java.util.List;

import gov.anzong.androidnga.base.util.ContextUtils;
import gov.anzong.androidnga.base.util.PreferenceUtils;
import gov.anzong.androidnga.base.util.StringUtils;
import gov.anzong.androidnga.base.util.ThreadUtils;
import gov.anzong.androidnga.common.PreferenceKey;
import gov.anzong.androidnga.debug.Debugger;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import sp.phone.common.UserManagerImpl;
import sp.phone.http.cookie.CookieHeaderUtil;
import sp.phone.http.cookie.NgaCookieStore;
import sp.phone.http.retrofit.converter.JsonStringConvertFactory;
import sp.phone.util.ForumUtils;
import sp.phone.util.NLog;
import sp.phone.view.webview.WebViewEx;

/**
 * Created by Justwen on 2017/10/10.
 */

public class RetrofitHelper {

    private Retrofit mRetrofit;

    private static final String URL_NGA_BASE_CC = "https://bbs.ngacn.cc/";

    private String mBaseUrl;

    private String mUserAgent = "";

    private RetrofitHelper() {
        Context context = ContextUtils.getContext();
        SharedPreferences sp = context.getSharedPreferences(PreferenceKey.PERFERENCE, Context.MODE_PRIVATE);
        mBaseUrl = ForumUtils.getAvailableDomain();
        mRetrofit = createRetrofit();

        sp.registerOnSharedPreferenceChangeListener((sp1, key) -> {
            if (key.equals(PreferenceKey.KEY_NGA_DOMAIN)) {
                mBaseUrl = ForumUtils.getAvailableDomain();
                mRetrofit = createRetrofit();
            }
        });

        mUserAgent = sp.getString(PreferenceKey.USER_AGENT, null);

        if (TextUtils.isEmpty(mUserAgent)) {
            mUserAgent = WebSettings.getDefaultUserAgent(context);
            PreferenceUtils.putData(PreferenceKey.USER_AGENT, mUserAgent);
        }

    }

    public void setUserAgent(String userAgent) {
        mUserAgent = userAgent;
    }

    public String getUserAgent() {
        return mUserAgent;
    }

    public Retrofit createRetrofit() {
        return createRetrofit(mBaseUrl, null);
    }

    public Retrofit createRetrofit(String baseUrl) {
        return createRetrofit(baseUrl, null);
    }

    public Retrofit createRetrofit(OkHttpClient.Builder builder) {
        return createRetrofit(mBaseUrl, builder);
    }

    public Retrofit createRetrofit(String baseUrl, OkHttpClient.Builder builder) {
        if (builder == null) {
            builder = createOkHttpClientBuilder();
        }
        return new Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(JsonStringConvertFactory.create())
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .client(builder.build())
                .build();
    }

    public OkHttpClient.Builder createOkHttpClientBuilder() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.addInterceptor(chain -> {
            Request original = chain.request();

            HttpUrl url = original.url();

            String explicitCookie = original.header("Cookie");
            String baseCookie = explicitCookie != null ? explicitCookie : UserManagerImpl.getInstance().getCookie();

            String webViewCookie = null;
            try {
                webViewCookie = CookieManager.getInstance().getCookie(url.toString());
            } catch (Throwable ignored) {
            }

            String persistedCookie = PreferenceUtils.getData(PreferenceKey.KEY_WEBVIEW_COOKIE, "");
            if (!TextUtils.isEmpty(persistedCookie)) {
                String activeUid = UserManagerImpl.getInstance().getUserId();
                String storedUid = CookieHeaderUtil.parseCookieHeader(persistedCookie).get("ngaPassportUid");
                if (!TextUtils.isEmpty(activeUid) && !TextUtils.isEmpty(storedUid) && !activeUid.equals(storedUid)) {
                    persistedCookie = "";
                }
            }
            String storedCookie = NgaCookieStore.getInstance().getCookieHeader(url);
            String mergedCookie = CookieHeaderUtil.mergeCookieHeaders(storedCookie, baseCookie, persistedCookie, webViewCookie);

            Request.Builder requestBuilder = original.newBuilder()
                    .header("User-Agent", mUserAgent)
                    .header("X-User-Agent", "Nga_Official")
                    .method(original.method(), original.body());
            if (!TextUtils.isEmpty(mergedCookie)) {
                requestBuilder.header("Cookie", mergedCookie);
            } else {
                requestBuilder.removeHeader("Cookie");
            }
            return chain.proceed(requestBuilder.build());
        });
        builder.addInterceptor(chain -> {
            Request request = chain.request();
            try {
                if (request.method().equalsIgnoreCase("post")) {
                    String body = StringUtils.requestBody2String(request.body());
                    body = URLDecoder.decode(body, "utf-8");
                    if (body.contains("charset=gbk")) {
                        request = request.newBuilder().post(RequestBody.create(MediaType.parse("application/x-www-form-urlencoded;charset=GBK"), body)).build();
                    }
                }
            } catch (Exception e) {
                NLog.e(e.getMessage());
            }
            return chain.proceed(request);
        });
        builder.addInterceptor(chain -> {
            Request request = chain.request();
            Debugger.collectRequest(request);
            return chain.proceed(request);
        });
        builder.addInterceptor(chain -> {
            Request request = chain.request();
            Response response = chain.proceed(request);
            List<String> setCookies = response.headers("Set-Cookie");
            if (setCookies != null && !setCookies.isEmpty()) {
                try {
                    NgaCookieStore.getInstance().saveFromResponse(request.url(), setCookies);
                } catch (Throwable ignored) {
                }
            }
            return response;
        });
        return builder;
    }

    public static RetrofitHelper getInstance() {
        return SingleTonHolder.sInstance;
    }

    public Object getService(Class<?> service) {
        return mRetrofit.create(service);
    }

    public RetrofitService getService() {
        return mRetrofit.create(RetrofitService.class);
    }

    public static RetrofitService getAuthCodeService() {
        return new Retrofit.Builder()
                .baseUrl(URL_NGA_BASE_CC)
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .build()
                .create(RetrofitService.class);
    }

    public static RetrofitService getDefault() {
        return new Retrofit.Builder()
                .baseUrl(URL_NGA_BASE_CC)
                .addConverterFactory(JsonStringConvertFactory.create())
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .build()
                .create(RetrofitService.class);
    }

    private static class SingleTonHolder {

        static final RetrofitHelper sInstance = new RetrofitHelper();
    }
}
