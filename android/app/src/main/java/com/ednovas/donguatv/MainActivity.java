package com.ednovas.donguatv;

import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    private static final String TAG = "MainActivity"; // 日志标签，方便排查问题
    private int statusBarHeight = 0;
    private ViewGroup webViewParent = null;
    private boolean needsManualPadding = false;
    private boolean isFullScreen = false;
    
    // 新增：保存H5视频全屏View，解决黑屏问题
    private View customVideoView;
    private WebChromeClient.CustomViewCallback videoCallback;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        
        // 设置状态栏背景色
        window.setStatusBarColor(0xFF141414);
        
        // 设置状态栏图标为浅色
        View decorView = window.getDecorView();
        int flags = decorView.getSystemUiVisibility();
        flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        decorView.setSystemUiVisibility(flags);
        
        // 检测是否是 Android 15+ (API 35+)
        needsManualPadding = Build.VERSION.SDK_INT >= 35;
        
        if (needsManualPadding) {
            // 获取状态栏高度
            statusBarHeight = getStatusBarHeight();
            Log.d(TAG, "Android 15+, status bar height: " + statusBarHeight);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        
        // 核心修复：添加WebView空值判断，打印日志排查
        WebView webView = getBridge().getWebView();
        if (webView == null) {
            Log.e(TAG, "WebView is NULL! Bridge object: " + getBridge());
            return;
        }
        Log.d(TAG, "WebView obtained successfully");
        
        if (webView.getParent() instanceof ViewGroup) {
            webViewParent = (ViewGroup) webView.getParent();
            Log.d(TAG, "WebView parent found: " + webViewParent.getClass().getName());
            
            // 只在 Android 15+ 上添加手动 padding
            if (needsManualPadding) {
                webViewParent.setPadding(
                    webViewParent.getPaddingLeft(),
                    statusBarHeight,
                    webViewParent.getPaddingRight(),
                    webViewParent.getPaddingBottom()
                );
                webViewParent.setBackgroundColor(0xFF141414);
            }
            
            // 核心修复：接管H5视频全屏View，解决黑屏+无声音
            webView.setWebChromeClient(new WebChromeClient() {
                // H5视频进入全屏时
                @Override
                public void onShowCustomView(View view, CustomViewCallback callback) {
                    super.onShowCustomView(view, callback);
                    Log.d(TAG, "H5 video enter fullscreen");
                    
                    // 保存视频View和回调，避免丢失
                    customVideoView = view;
                    videoCallback = callback;
                    
                    // 触发原生全屏
                    enterFullscreen();
                    
                    // 关键：将视频View添加到布局，解决黑屏
                    if (webViewParent != null) {
                        webViewParent.addView(view);
                        webView.setVisibility(View.GONE); // 隐藏原WebView
                    }
                }

                // H5视频退出全屏时
                @Override
                public void onHideCustomView() {
                    super.onHideCustomView();
                    Log.d(TAG, "H5 video exit fullscreen");
                    
                    // 恢复WebView显示，移除视频View
                    if (webViewParent != null && customVideoView != null) {
                        webViewParent.removeView(customVideoView);
                        webView.setVisibility(View.VISIBLE);
                    }
                    
                    // 释放资源
                    customVideoView = null;
                    if (videoCallback != null) {
                        videoCallback.onCustomViewHidden();
                        videoCallback = null;
                    }
                    
                    // 触发原生退出全屏
                    exitFullscreen();
                }
            });

            // 添加 JavaScript 接口用于全屏控制
            webView.addJavascriptInterface(new FullscreenInterface(), "AndroidFullscreen");
            Log.d(TAG, "Fullscreen JS interface added");
        } else {
            Log.e(TAG, "WebView parent is not a ViewGroup");
        }
    }
    
    // 获取状态栏高度（像素）
    private int getStatusBarHeight() {
        int result = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        if (result == 0) {
            result = (int) (24 * getResources().getDisplayMetrics().density);
        }
        return result;
    }
    
    // 进入全屏模式 - 简化逻辑，解决视频渲染冲突
    private void enterFullscreen() {
        runOnUiThread(() -> {
            Log.d(TAG, "Enter fullscreen, isFullScreen: " + isFullScreen);
            
            // Android 15+ 移除手动padding
            if (needsManualPadding && webViewParent != null) {
                webViewParent.setPadding(0, 0, 0, 0);
            }
            
            Window window = getWindow();
            // 仅保留必要的全屏Flag，移除冲突的LAYOUT_NO_LIMITS
            window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
            
            // 简化系统UI参数，避免WebView渲染冲突
            View decorView = window.getDecorView();
            decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            );
            
            // 锁定横屏
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            
            isFullScreen = true;
            Log.d(TAG, "Fullscreen mode enabled");
        });
    }
    
    // 退出全屏模式 - 恢复基础配置
    private void exitFullscreen() {
        runOnUiThread(() -> {
            Log.d(TAG, "Exit fullscreen, isFullScreen: " + isFullScreen);
            
            // Android 15+ 恢复padding
            if (needsManualPadding && webViewParent != null) {
                webViewParent.setPadding(
                    webViewParent.getPaddingLeft(),
                    statusBarHeight,
                    webViewParent.getPaddingRight(),
                    webViewParent.getPaddingBottom()
                );
            }
            
            Window window = getWindow();
            // 清除全屏Flag
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            
            // 恢复系统UI
            View decorView = window.getDecorView();
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            
            // 解锁屏幕方向
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            
            // 重置状态栏背景色
            window.setStatusBarColor(0xFF141414);
            
            isFullScreen = false;
            Log.d(TAG, "Fullscreen mode disabled");
        });
    }
    
    // JavaScript 接口类
    public class FullscreenInterface {
        @JavascriptInterface
        public void enter() {
            Log.d(TAG, "JS call enter fullscreen");
            enterFullscreen();
        }
        
        @JavascriptInterface
        public void exit() {
            Log.d(TAG, "JS call exit fullscreen");
            exitFullscreen();
        }
    }
    
    // TV 遥控器返回键处理
    @Override
    public void onBackPressed() {
        WebView webView = getBridge().getWebView();
        if (webView != null) {
            webView.evaluateJavascript(
                "(function() {" +
                "  if (window.vueApp && window.vueApp.showDetail) {" +
                "    if (window.vueApp.dp && window.vueApp.dp.fullScreen) {" +
                "      try { window.vueApp.dp.fullScreen.cancel('web'); } catch(e) {}" +
                "    }" +
                "    window.vueApp.closeDetail();" +
                "    return 'closed';" +
                "  }" +
                "  return 'none';" +
                "})()",
                result -> {
                    if (result != null && result.contains("none")) {
                        if (webView.canGoBack()) {
                            webView.goBack();
                        } else {
                            MainActivity.super.onBackPressed();
                        }
                    }
                }
            );
        } else {
            super.onBackPressed();
        }
    }

    // 修复：public修饰符，解决编译报错
    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume, isFullScreen: " + isFullScreen);
        if (isFullScreen) {
            enterFullscreen(); // 重新应用全屏配置
        }
    }

    // 保留public修饰符
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        Log.d(TAG, "onWindowFocusChanged, hasFocus: " + hasFocus + ", isFullScreen: " + isFullScreen);
        if (hasFocus && isFullScreen) {
            enterFullscreen();
        }
    }
}
