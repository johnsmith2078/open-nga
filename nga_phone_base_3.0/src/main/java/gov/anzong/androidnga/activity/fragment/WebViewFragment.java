package gov.anzong.androidnga.activity.fragment;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.common.base.Strings;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import gov.anzong.androidnga.R;
import gov.anzong.androidnga.base.util.PreferenceUtils;
import gov.anzong.androidnga.common.PreferenceKey;
import gov.anzong.androidnga.ui.fragment.BaseFragment;
import okhttp3.HttpUrl;
import sp.phone.common.UserManagerImpl;
import sp.phone.http.cookie.NgaCookieStore;
import sp.phone.http.cookie.CookieHeaderUtil;

/**
 * @author yangyihang
 */
public class WebViewFragment extends BaseFragment {

    private WebView mWebView;

    private String mUrl;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle bundle = getArguments();
        if (bundle != null) {
            mUrl = bundle.getString("url", "");
        }
        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mWebView = createWebView(inflater.getContext());
        return mWebView;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.fragment_webview, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.menu_open_by_browser) {
            startExternalBrowser(getContext(), mWebView.getUrl());
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean startExternalBrowser(Context context, String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        try {
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException e) {
            e.printStackTrace();
        }
        return false;
    }

    private WebView createWebView(Context context) {
        WebView webView = new WebView(context);
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }
        webView.setWebViewClient(new WebViewClient(){

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrlLoading(view, request.getUrl().toString());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrlLoading(view, url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                setTitle(view.getTitle());
                super.onPageFinished(view, url);
            }
        });

        webView.setWebChromeClient(new WebChromeClient(){

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                return super.onShowFileChooser(webView, filePathCallback, fileChooserParams);
            }
        });

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setLoadWithOverviewMode(true);
        String userAgent = PreferenceUtils.getData(PreferenceKey.USER_AGENT, "");
        if (TextUtils.isEmpty(userAgent)) {
            userAgent = WebSettings.getDefaultUserAgent(context);
        }
        webSettings.setUserAgentString(userAgent);
        webSettings.setTextZoom(100);
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setUseWideViewPort(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        return webView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        if (mUrl != null) {
            syncCookies(mWebView, mUrl);
            mWebView.loadUrl(mUrl);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mWebView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mWebView.onPause();
    }


    @Override
    public boolean onBackPressed() {
        if (mWebView.canGoBack()) {
            mWebView.goBack();
            return true;
        }
        return super.onBackPressed();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mWebView.destroy();
        mWebView = null;
    }

    private boolean handleUrlLoading(WebView view, String url) {
        if (TextUtils.isEmpty(url)) {
            return false;
        }
        if (!url.startsWith("http")) {
            return false;
        }
        String host = Strings.nullToEmpty(Uri.parse(url).getHost());
        if (host.contains("nga") || host.contains("178")) {
            return false;
        }
        return startExternalBrowser(getContext(), url);
    }

    private void syncCookies(WebView webView, String url) {
        String storedCookie = PreferenceUtils.getData(PreferenceKey.KEY_WEBVIEW_COOKIE, "");
        if (!TextUtils.isEmpty(storedCookie)) {
            String activeUid = UserManagerImpl.getInstance().getUserId();
            if (!TextUtils.isEmpty(activeUid)) {
                String storedUid = CookieHeaderUtil.parseCookieHeader(storedCookie).get("ngaPassportUid");
                if (!TextUtils.isEmpty(storedUid) && !activeUid.equals(storedUid)) {
                    storedCookie = "";
                }
            }
        }
        String userCookie = UserManagerImpl.getInstance().getCookie();
        Map<String, String> baseCookies = new LinkedHashMap<>();
        mergeCookies(baseCookies, storedCookie);
        mergeCookies(baseCookies, userCookie);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }

        Set<String> hosts = new LinkedHashSet<>();
        if (!TextUtils.isEmpty(url)) {
            String host = Uri.parse(url).getHost();
            if (!TextUtils.isEmpty(host)) {
                hosts.add(host);
            }
        }
        String[] domains = webView.getResources().getStringArray(R.array.nga_domain_no_http);
        for (String domain : domains) {
            if (TextUtils.isEmpty(domain)) {
                continue;
            }
            hosts.add(domain.replace("\"", "").trim());
        }

        for (String host : hosts) {
            if (TextUtils.isEmpty(host)) {
                continue;
            }
            String baseUrl = host.startsWith("http") ? host : "https://" + host;
            Map<String, String> mergedCookies = new LinkedHashMap<>(baseCookies);
            HttpUrl hostUrl = HttpUrl.parse(baseUrl);
            if (hostUrl != null) {
                mergeCookies(mergedCookies, NgaCookieStore.getInstance().getCookieHeader(hostUrl));
            }
            if (mergedCookies.isEmpty()) {
                continue;
            }
            for (Map.Entry<String, String> entry : mergedCookies.entrySet()) {
                cookieManager.setCookie(baseUrl, entry.getKey() + "=" + entry.getValue() + "; Path=/");
            }
        }
        cookieManager.flush();
    }

    private void mergeCookies(Map<String, String> target, String cookieHeader) {
        if (TextUtils.isEmpty(cookieHeader)) {
            return;
        }
        target.putAll(CookieHeaderUtil.parseCookieHeader(cookieHeader));
    }
}
