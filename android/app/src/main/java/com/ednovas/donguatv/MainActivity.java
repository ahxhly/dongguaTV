package com.ednovas.donguatv;

import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager; // 新增：用于Window全屏Flag
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient; // 新增：监听WebView全屏事件
import android.webkit.WebView;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    private int statusBarHeight = 0;
    private ViewGroup webViewParent = null;
    private boolean needsManualPadding = false;
    private boolean isFullScreen = false; // 新增：标记是否处于全屏状态

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
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        
        // 获取 WebView 并设置其父容器的 padding
        WebView webView = getBridge().getWebView();
        if (webView != null && webView.getParent() instanceof ViewGroup) {
            webViewParent = (ViewGroup) webView.getParent();
            
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
            
            // 新增：监听WebView全屏事件（兼容H5视频全屏触发）
            webView.setWebChromeClient(new WebChromeClient() {
                // H5视频进入全屏时自动触发原生全屏
                @Override
                public void onShowCustomView(View view, CustomViewCallback callback) {
                    super.onShowCustomView(view, callback);
                    enterFullscreen(); // 调用原有全屏方法
                }

                // H5视频退出全屏时自动触发原生退出全屏
                @Override
                public void onHideCustomView() {
                    super.onHideCustomView();
                    exitFullscreen(); // 调用原有退出全屏方法
                }
            });

            // 添加 JavaScript 接口用于全屏控制（保留原有逻辑）
            webView.addJavascriptInterface(new FullscreenInterface(), "AndroidFullscreen");
        }
    }
    
    // 获取状态栏高度（像素）- 保留原有逻辑
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
    
    // 进入全屏模式 - 优化系统UI隐藏逻辑（核心修改）
    private void enterFullscreen() {
        runOnUiThread(() -> {
            // 保留原有：Android 15+ 移除手动padding
            if (needsManualPadding && webViewParent != null) {
                webViewParent.setPadding(0, 0, 0, 0);
            }
            
            Window window = getWindow(); // 新增：获取Window对象
            // 新增：添加全屏Flag，确保彻底隐藏系统栏
            window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS); // 新增：布局延伸到全屏
            
            // 优化系统UI参数，确保导航栏彻底隐藏
            View decorView = window.getDecorView();
            decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY // 粘性沉浸式（核心）
                | View.SYSTEM_UI_FLAG_FULLSCREEN // 隐藏状态栏
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // 隐藏导航栏
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION // 布局延伸到导航栏区域
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN // 布局延伸到状态栏区域
                | View.SYSTEM_UI_FLAG_IMMERSIVE // 沉浸式（补充）
            );
            
            // 保留原有：锁定横屏
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        });
    }
    
    // 退出全屏模式 - 优化系统UI恢复逻辑
    private void exitFullscreen() {
        runOnUiThread(() -> {
            // 保留原有：Android 15+ 恢复padding
            if (needsManualPadding && webViewParent != null) {
                webViewParent.setPadding(
                    webViewParent.getPaddingLeft(),
                    statusBarHeight,
                    webViewParent.getPaddingRight(),
                    webViewParent.getPaddingBottom()
                );
            }
            
            Window window = getWindow(); // 新增：获取Window对象
            // 新增：清除全屏Flag
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            
            // 优化：完整恢复系统UI状态
            View decorView = window.getDecorView();
            decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_VISIBLE // 恢复默认UI
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
            
            // 保留原有：解锁屏幕方向
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            
            // 新增：重置状态栏背景色（防止退出全屏后样式异常）
            window.setStatusBarColor(0xFF141414);
        });
    }
    
    // JavaScript 接口类 - 保留原有逻辑
    public class FullscreenInterface {
        @JavascriptInterface
        public void enter() {
            enterFullscreen();
        }
        
        @JavascriptInterface
        public void exit() {
            exitFullscreen();
        }
    }
    
    // TV 遥控器返回键处理 - 保留原有逻辑
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

    // 新增：页面恢复时重新检查全屏状态（防止切后台后导航栏恢复）
    @Override
    protected void onResume() {
        super.onResume();
        if (isFullScreen) { // 检测全屏状态
            enterFullscreen(); // 重新应用全屏配置
        }
    }

    // 新增：记录全屏状态（配合onResume使用）
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && isFullScreen) {
            enterFullscreen();
        }
    }
}
