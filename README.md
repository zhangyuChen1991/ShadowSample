# ShadowSample
使用腾讯Shadow实现插件化的Demo

下面记录一下接入流程和遇到的问题，方便以后查看。

#### 结构
整个Demo由3个工程构成，分别是宿主App，插件管理App，插件App，三个工程相互完全独立。
```
|--HostApp //宿主
|
|--PluginManager  //负责加载插件，管理插件
|
|--BusinessApp  //插件app
   |--plugin-app  //业务app
   |--sample-loader  //配置了插件Activity到壳Activity的对应关系等
   |--sample-runtime  //定义了宿主中占位的Activity等
   ```
   
   
####    Shadow主要接入流程
1. Clone Shadow的代码 [Shdow Github地址](https://github.com/Tencent/Shadow),然后编译，上传本地maven库或者远程maven库，以便后续应用，不建议直接引用sdk代码，里面的库分很多块，三个工程各自需要引用的模块不一样，操作不方便，用maven可以分别按需引用。
2. 参照腾讯Shadow的Sample中Maven下面的sample(结构也是一样的三个工程)，分别创建工程，引用相应的库，实现必要的接口(占位的Activity、Service，插件管理类等)，分别编译通过。
3. PluginManager编译之后生成apk文件，BusinessApp编译之后生成一个zip包，需要将他们分别放到宿主指定的文件目录之下，让宿主能够加载到。

  >BusinessApp编译用如下命令：
  `gradlew packageDebugPlugin`  
放文件的位置是宿主中配置的，pluginManager的apk存放位置在初始化管理类的时候定义的，关键代码是
`FixedPathPmUpdater fixedPathPmUpdater
                = new FixedPathPmUpdater(new File(PLUGIN_MANAGER_APK_FILE_PATH));`
插件zip包的位置可以在启动插件四大组件的时候传：
``bundle.putString("plugin_path", "/data/local/tmp/plugin-debug.zip"); ``
也可以直接在PluginManager中硬编码：
`` InstalledPlugin installedPlugin = installPlugin(pluginZipPath, null, true);``
在PluginManager里面根据part_key来判断是哪个插件，然后直接判断出地址要方便一些
4. 安装宿主App，测试是否可以加载插件app的页面、服务等。
 
==第二步的步骤很多==，可以[参考这里](https://www.jianshu.com/p/f00dc837227f)  

##### 小注意点：
> 1. 插件的包名和宿主的包名要一致
>  2. 有一些类要求路径和类名是固定的，像loader里面的CoreLoaderFactoryImpl，写的时候要注意，弄错了很难排查问题。
>  3. 占位的activity在宿主的manifest里面注册，在插件的runtime里面声明，在插件的loader里面写对应关系，类似这些地方要注意，包类名要对应上，不要写错


##### 遇到问题：
1.实际按照这个博客做的时候，最后从宿主App跳到插件的时候报了如下错，
```
java.lang.ClassCastException: Cannot cast androidx.core.app.CoreComponentFactory to com.tencent.shadow.core.runtime.ShadowAppComponentFactory
```
没能找到具体原因，最后重新建了一个BusinessApp工程引入官方demo中Maven下面的Plugin-project，改了相关的配置，然后才测试通过的；  

2.在插件的app配置时，业务app的build.gradle配置loader和runtime的apk路径已经插件模块的partKey等信息的时候，报了apkName无法识别的错误，没有解决，最后注释了这一行，后面也能测试通过，暂时不知道还有什么影响。
```
shadow {
    packagePlugin {
        pluginTypes {
            debug {
                loaderApkConfig = new Tuple2('sample-loader-debug.apk', ':sample-loader:assembleDebug')
                runtimeApkConfig = new Tuple2('sample-runtime-debug.apk', ':sample-runtime:assembleDebug')
                pluginApks {
                    pluginApk1 {
                        businessName = 'my-plugin'
//businessName相同的插件，context获取的Dir是相同的。businessName留空，表示和宿主相同业务，直接使用宿主的Dir。
                        partKey = 'my-plugin'
                        buildTask = 'assemblePluginDebug'
//                        apkName = 'plugin-app-plugin-debug.apk'
                        apkPath = 'plugin-app/build/outputs/apk/plugin/debug/plugin-app-plugin-debug.apk'
                    }
                }
            }

            release {
                loaderApkConfig = new Tuple2('sample-loader-release.apk', ':sample-loader:assembleRelease')
                runtimeApkConfig = new Tuple2('sample-runtime-release.apk', ':sample-runtime:assembleRelease')
                pluginApks {
                    pluginApk1 {
                        businessName = 'my-plugin'
                        partKey = 'my-plugin'
                        buildTask = 'assemblePluginRelease'
//                        apkName = 'plugin-app-plugin-release.apk'
                        apkPath = 'plugin-app/build/outputs/apk/plugin/release/plugin-app-plugin-release.apk'
                    }
                }
            }
        }

        loaderApkProjectPath = 'sample-loader'

        runtimeApkProjectPath = 'sample-runtime'

        version = 4
        compactVersion = [1, 2, 3]
        uuidNickName = "1.1.5"
    }
}
```