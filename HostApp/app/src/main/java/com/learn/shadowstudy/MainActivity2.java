package com.learn.shadowstudy;

import android.os.Bundle;
import android.view.View;

import com.learn.shadowstudy.base.MyApplication;
import com.tencent.shadow.dynamic.host.EnterCallback;
import com.tencent.shadow.dynamic.host.PluginManager;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Created by zhangyu on 2022/3/26.
 */
public class MainActivity2 extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.am_tv).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                PluginManager pluginManager = MyApplication.getPluginManager();
                /**
                 * @param context context
                 * @param formId  标识本次请求的来源位置，用于区分入口
                 * @param bundle  参数列表, 建议在参数列表加入自己的验证
                 * @param callback 用于从PluginManager实现中返回View
                 */
                Bundle bundle = new Bundle();
                // 插件 zip，这几个参数也都可以不传，直接在 PluginManager 中硬编码
                bundle.putString("plugin_path", "/data/local/tmp/plugin-debug.zip");
                // partKey 每个插件都有自己的 partKey 用来区分多个插件，如何配置在下面讲到
                bundle.putString("part_key", "my-plugin");
                // 路径举例：com.google.samples.apps.sunflower.GardenActivity
//                bundle.putString("activity_class_name", "com.learn.shadow.ui.HomeActivity");
                bundle.putString("activity_class_name", "com.tencent.shadow.sample.plugin.MainActivity");
                // 要传入到插件里的参数
                bundle.putBundle("extra_to_plugin_bundle", new Bundle());

                pluginManager.enter(MainActivity2.this, 12335, bundle, new EnterCallback() {
                    @Override
                    public void onShowLoadingView(View view) {
                    }

                    @Override
                    public void onCloseLoadingView() {

                    }

                    @Override
                    public void onEnterComplete() {
                        // 启动成功
                    }
                });
            }
        });
    }
}