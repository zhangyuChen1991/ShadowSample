package com.learn.pluginmanager;

/**
 * Created by zhangyu on 2022/3/26.
 */
import android.app.Activity;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;

import com.tencent.shadow.core.manager.installplugin.InstalledPlugin;
import com.tencent.shadow.core.manager.installplugin.InstalledType;
import com.tencent.shadow.core.manager.installplugin.PluginConfig;
import com.tencent.shadow.dynamic.host.EnterCallback;
import com.tencent.shadow.dynamic.host.FailedException;
import com.tencent.shadow.dynamic.loader.PluginServiceConnection;
import com.tencent.shadow.dynamic.manager.PluginManagerThatUseDynamicLoader;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author Afra55
 * @date 2020/5/19
 * A smile is the best business card.
 */
public class MyPluginManager extends PluginManagerThatUseDynamicLoader {

    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private ExecutorService mFixedPool = Executors.newFixedThreadPool(4);

    public MyPluginManager(Context context) {
        super(context);
    }

    /**
     * TODO 下面内容需要自己实现
     * @return PluginManager实现的别名，用于区分不同PluginManager实现的数据存储路径
     */
    @Override
    protected String getName() {
        return "sample-manager";
    }

    /**
     * @return 宿主中注册的PluginProcessService实现的类名
     */
    protected String getPluginProcessServiceName() {
        return "com.learn.shadowstudy.service.MainPluginProcessService";
    }

    /**
     * TODO 下面内容需要自己实现
     * @param context context
     * @param fromId  标识本次请求的来源位置，用于区分入口
     * @param bundle  参数列表
     * @param callback 用于从PluginManager实现中返回View
     */
    @Override
    public void enter(final Context context, long fromId, Bundle bundle, final EnterCallback callback) {
        // 插件 zip 包地址，可以直接写在这里，也用Bundle可以传进来
        final String pluginZipPath = bundle.getString("plugin_path");
        final String partKey = bundle.getString("part_key");
        final String className = bundle.getString("activity_class_name");
        if (className == null) {
            throw new NullPointerException("className == null");
        }
        if (fromId == 12335) { // 打开 Activity 示例
            final Bundle extras = bundle.getBundle("extra_to_plugin_bundle");
            if (callback != null) {
                // 开始加载插件了，实现加载布局
                callback.onShowLoadingView(null);
            }
            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        InstalledPlugin installedPlugin
                                = installPlugin(pluginZipPath, null, true);//这个调用是阻塞的
                        Intent pluginIntent = new Intent();
                        pluginIntent.setClassName(
                                context.getPackageName(),
                                className
                        );
                        if (extras != null) {
                            pluginIntent.replaceExtras(extras);
                        }

                        startPluginActivity(context, installedPlugin, partKey, pluginIntent);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    if (callback != null) {
                        Handler uiHandler = new Handler(Looper.getMainLooper());
                        uiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                // 到这里插件就启动完成了
                                callback.onCloseLoadingView();
                                callback.onEnterComplete();
                            }
                        });
                    }
                }
            });

        } else if (fromId == 1012) { // 打开Server示例
            Intent pluginIntent = new Intent();
            pluginIntent.setClassName(context.getPackageName(), className);

            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        InstalledPlugin installedPlugin
                                = installPlugin(pluginZipPath, null, true);//这个调用是阻塞的

                        loadPlugin(installedPlugin.UUID, partKey);

                        Intent pluginIntent = new Intent();
                        pluginIntent.setClassName(context.getPackageName(), className);

                        boolean callSuccess = mPluginLoader.bindPluginService(pluginIntent, new PluginServiceConnection() {
                            @Override
                            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                                // 在这里实现AIDL进行通信操作
                            }

                            @Override
                            public void onServiceDisconnected(ComponentName componentName) {
                                throw new RuntimeException("onServiceDisconnected");
                            }
                        }, Service.BIND_AUTO_CREATE);

                        if (!callSuccess) {
                            throw new RuntimeException("bind service失败 className==" + className);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        } else {
            throw new IllegalArgumentException("不认识的fromId==" + fromId);
        }
    }



    public InstalledPlugin installPlugin(String zip, String hash , boolean odex) throws IOException, JSONException, InterruptedException, ExecutionException {
        final PluginConfig pluginConfig = installPluginFromZip(new File(zip), hash);
        final String uuid = pluginConfig.UUID;
        List<Future> futures = new LinkedList<>();
        if (pluginConfig.runTime != null && pluginConfig.pluginLoader != null) {
            Future odexRuntime = mFixedPool.submit(new Callable() {
                @Override
                public Object call() throws Exception {
                    oDexPluginLoaderOrRunTime(uuid, InstalledType.TYPE_PLUGIN_RUNTIME,
                            pluginConfig.runTime.file);
                    return null;
                }
            });
            futures.add(odexRuntime);
            Future odexLoader = mFixedPool.submit(new Callable() {
                @Override
                public Object call() throws Exception {
                    oDexPluginLoaderOrRunTime(uuid, InstalledType.TYPE_PLUGIN_LOADER,
                            pluginConfig.pluginLoader.file);
                    return null;
                }
            });
            futures.add(odexLoader);
        }
        for (Map.Entry<String, PluginConfig.PluginFileInfo> plugin : pluginConfig.plugins.entrySet()) {
            final String partKey = plugin.getKey();
            final File apkFile = plugin.getValue().file;
            Future extractSo = mFixedPool.submit(new Callable() {
                @Override
                public Object call() throws Exception {
                    extractSo(uuid, partKey, apkFile);
                    return null;
                }
            });
            futures.add(extractSo);
            if (odex) {
                Future odexPlugin = mFixedPool.submit(new Callable() {
                    @Override
                    public Object call() throws Exception {
                        oDexPlugin(uuid, partKey, apkFile);
                        return null;
                    }
                });
                futures.add(odexPlugin);
            }
        }

        for (Future future : futures) {
            future.get();
        }
        onInstallCompleted(pluginConfig,null);

        return getInstalledPlugins(1).get(0);
    }


    public void startPluginActivity(Context context, InstalledPlugin installedPlugin, String partKey, Intent pluginIntent) throws RemoteException, TimeoutException, FailedException {
        Intent intent = convertActivityIntent(installedPlugin, partKey, pluginIntent);
        if (!(context instanceof Activity)) {
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    public Intent convertActivityIntent(InstalledPlugin installedPlugin, String partKey, Intent pluginIntent) throws RemoteException, TimeoutException, FailedException {
        loadPlugin(installedPlugin.UUID, partKey);
        return mPluginLoader.convertActivityIntent(pluginIntent);
    }

    private void loadPluginLoaderAndRuntime(String uuid) throws RemoteException, TimeoutException, FailedException {
        if (mPpsController == null) {
            bindPluginProcessService(getPluginProcessServiceName());
            waitServiceConnected(10, TimeUnit.SECONDS);
        }
        loadRunTime(uuid);
        loadPluginLoader(uuid);
    }

    protected void loadPlugin(String uuid, String partKey) throws RemoteException, TimeoutException, FailedException {
        loadPluginLoaderAndRuntime(uuid);
        Map map = mPluginLoader.getLoadedPlugin();
        if (!map.containsKey(partKey)) {
            mPluginLoader.loadPlugin(partKey);
        }
        Boolean isCall = (Boolean) map.get(partKey);
        if (isCall == null || !isCall) {
            mPluginLoader.callApplicationOnCreate(partKey);
        }
    }
}
