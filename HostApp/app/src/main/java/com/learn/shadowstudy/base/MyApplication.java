package com.learn.shadowstudy.base;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.webkit.WebView;

import com.learn.shadowstudy.manager.FixedPathPmUpdater;
import com.learn.shadowstudy.utils.AndroidLoggerFactory;
import com.tencent.shadow.core.common.LoggerFactory;
import com.tencent.shadow.dynamic.host.DynamicPluginManager;
import com.tencent.shadow.dynamic.host.DynamicRuntime;
import com.tencent.shadow.dynamic.host.PluginManager;

import java.io.File;
import java.util.concurrent.Future;

import static android.os.Process.myPid;

/**
 * Created by zhangyu on 2022/3/26.
 */
public class MyApplication extends Application {
    public static String PLUGIN_MANAGER_APK_FILE_PATH = "/data/local/tmp/pluginmanager-debug.apk";

    @Override
    public void onCreate() {
        super.onCreate();

        Log.d(Constants.TAG,"PLUGIN_MANAGER_APK_FILE_PATH: "+PLUGIN_MANAGER_APK_FILE_PATH);
        onApplicationCreate(this);
    }

    /**
     * 这个PluginManager对象在Manager升级前后是不变的。它内部持有具体实现，升级时更换具体实现。
     */
    private static PluginManager sPluginManager;

    public static PluginManager getPluginManager() {
        return sPluginManager;
    }

    public static void onApplicationCreate(Application application) {
        //Log接口Manager也需要使用，所以主进程也初始化。
        LoggerFactory.setILoggerFactory(new AndroidLoggerFactory());

        if (isProcess(application, ":plugin")) {
            //在全动态架构中，Activity组件没有打包在宿主而是位于被动态加载的runtime，
            //为了防止插件crash后，系统自动恢复crash前的Activity组件，此时由于没有加载runtime而发生classNotFound异常，导致二次crash
            //因此这里恢复加载上一次的runtime
            DynamicRuntime.recoveryRuntime(application);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                WebView.setDataDirectorySuffix("plugin");
            }
        }

        FixedPathPmUpdater fixedPathPmUpdater
                = new FixedPathPmUpdater(new File(PLUGIN_MANAGER_APK_FILE_PATH));
        boolean needWaitingUpdate
                = fixedPathPmUpdater.wasUpdating()//之前正在更新中，暗示更新出错了，应该放弃之前的缓存
                || fixedPathPmUpdater.getLatest() == null;//没有本地缓存
        Future<File> update = fixedPathPmUpdater.update();
        if (needWaitingUpdate) {
            try {
                update.get();//这里是阻塞的，需要业务自行保证更新Manager足够快。
            } catch (Exception e) {
                throw new RuntimeException("Sample程序不容错", e);
            }
        }
        sPluginManager = new DynamicPluginManager(fixedPathPmUpdater);
    }

    private static boolean isProcess(Context context, String processName) {
        String currentProcName = "";
        ActivityManager manager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningAppProcessInfo processInfo : manager.getRunningAppProcesses()) {
            if (processInfo.pid == myPid()) {
                currentProcName = processInfo.processName;
                break;
            }
        }

        return currentProcName.endsWith(processName);
    }
}