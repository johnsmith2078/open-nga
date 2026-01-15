package sp.phone.mvp.model.convert;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.net.UnknownHostException;

import gov.anzong.androidnga.R;
import gov.anzong.androidnga.base.util.ContextUtils;

/**
 * Created by Justwen on 2017/11/23.
 */
public class ErrorConvertFactory {

    public static String getErrorMessage(String js) {
        if (js == null || js.isEmpty()) {
            return ContextUtils.getString(R.string.network_error);
        } else if (js.contains("未登录")) {
            return "请重新登录";
        } else if (js.contains("无此页")) {
            return ContextUtils.getString(R.string.last_page_prompt);
        } else if (shouldOpenWithWebView(js)) {
            return "访问被限制（ERROR:15）。请先用内置浏览器打开一次以更新 Cookie，然后再返回应用重试。";
        } else {
            try {
                JSONObject obj = (JSONObject) JSON.parse(js);
                obj = (JSONObject) obj.get("data");
                obj = (JSONObject) obj.get("__MESSAGE");
                return obj.getString("1");
            } catch (Exception e) {
                return null;
            }
        }
    }

    public static boolean shouldOpenWithWebView(String responseBody) {
        if (responseBody == null || responseBody.isEmpty()) {
            return false;
        }
        return responseBody.contains("ERROR:15")
                || responseBody.contains("Error:15")
                || responseBody.contains("error:15")
                || responseBody.contains("访客不能直接访问")
                || responseBody.contains("游客不能直接访问")
                || (responseBody.contains("加载中") && responseBody.contains("请稍后") && responseBody.contains("ERROR:15"));
    }

    public static String getErrorMessage(Throwable throwable) {
        String error;
        if (throwable instanceof UnknownHostException) {
            error = ContextUtils.getString(R.string.network_error);
        } else {
            error = throwable.getMessage();
        }
        return error;
    }

}
