/*
 * Copyright (C) 2005-2017 Qihoo 360 Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed To in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.qihoo360.replugin.gradle.host.handlemanifest

import groovy.xml.MarkupBuilder

/**
 * @author RePlugin Team
 */
class ComponentsGenerator {

    def static final infix = 'loader.a.Activity'

    def static final name = 'android:name'

    /*
    服务所在进程的名字。通常，一个应用的所有组件都运行在系统为这个应用所创建的默认进程中
        1): 如果被设置的进程名是以一个冒号开头的，则这个新的进程对于这个应用来说是私有的，当它被需要或者这个服务需要在新进程中运行的时候，这个新进程将会被创建。
        2): 如果这个进程的名字是以小写字符开头的，则这个服务将运行在一个以这个名字命名的全局的进程中，当然前提是它有相应的权限。这将允许在不同应用中的各种组件可以共享一个进程，从而减少资源的占用
     */
    def static final process = 'android:process'

    //android:taskAffinity 的作用 https://juejin.im/post/5935081d2f301e006b09cb9e
    def static final task = 'android:taskAffinity'
    def static final launchMode = 'android:launchMode'
    def static final authorities = 'android:authorities'

    /*
    官方解释
    android:multiprocess
        If the app runs in multiple processes, this attribute determines whether multiple instances of the content provder are created.
        If true, each of the app's processes has its own content provider object.
        If false, the app's processes share only one content provider object. The default value is false.

        Setting this flag to true may improve performance by reducing the overhead of interprocess(进程间) communication, but it also increases the memory footprint of each process.

    1. android:process=":fore"，android:multiprocess="true"：provider不会随应用的启动而加载，当调用到provider的时候才会加载，
       加载时provider是在调用者的进程中初始化的。这时候可能定义provider的fore进程还没有启动。

    2. android:process=":fore"（android:multiprocess默认情况下为"false"）：provider不会随应用的启动而加载，当调用到provider的时候才会加载，
       加载时provider是在“fore”进程中初始化的。

    3. android:multiprocess="true"：provider会随着应用启动的时候加载，加载时provider是在应用默认主进程中初始化的。对于android:multiprocess=true，
       意味着provider可以多实例，那么由调用者在自己的进程空间实例化一个ContentProvider对象，此时定义ContentProvider的App可能并没有启动。

    4. android:multiprocess="false"：provider会随着应用启动的时候加载，加载时provider是在应用默认主进程中初始化的。对于android:multiprocess=false（默认值），
       由系统把定义该ContentProvider的App启动起来(一个独立的Process)并实例化ContentProvider，这种ContentProvider只有一个实例，运行在自己App的Process中。所有调用者共享该ContentProvider实例，调用者与ContentProvider实例位于两个不同的Process。

       总之，android:multiprocess 应该理解为：是否允许在调用者的进程里实例化provider，而跟定义它的进程没有关系。
     */
    def static final multiprocess = 'android:multiprocess'

    def static final cfg = 'android:configChanges'
    def static final cfgV = 'keyboard|keyboardHidden|orientation|screenSize'

    /*
     Activity:
        在Activity中该属性用来表示：当前Activity是否可以被另一个Application的组件启动：true允许被启动；false不允许被启动。
        如果被设置为了false，那么这个Activity将只会被当前Application或者拥有同样user ID的Application的组件调用。
        exported 的默认值根据Activity中是否有intent filter 来定。没有任何的filter意味着这个Activity只有在详细的描述了他的class name后才能被唤醒
        .这意味着这个Activity只能在应用内部使用，因为其它application并不知道这个class的存在。所以在这种情况下，它的默认值是false。
        从另一方面讲，如果Activity里面至少有一个filter的话，意味着这个Activity可以被其它应用从外部唤起，这个时候它的默认值是true。

     Service:
        该属性用来表示，其它应用的组件是否可以唤醒service或者和这个service进行交互：true可以，false不可以。如果为false，
        只有同一个应用的组件或者有着同样user ID的应用可以启动这个service或者绑定这个service

     ContentProvider:
        当前内容提供者是否会被其它应用使用：
        true: 当前提供者可以被其它应用使用。任何应用可以使用Provider通过URI 来获得它，也可以通过相应的权限来使用Provider。

        false:当前提供者不能被其它应用使用。设置Android：exported=“false”来限制其它应用获得你应用的Provider。只有拥有同样的user ID 的
        应用可以获得当前应用的Provider。

     BroadcastReceiver:
        当前broadcast Receiver 是否可以从当前应用外部获取Receiver message 。true，可以；false 不可以。如果为false ,当前broadcast Receiver 只能收到同一个应用或者拥有同一 user ID 应用发出广播。
     */
    def static final exp = 'android:exported'
    def static final expV = 'false'

    def static final ori = 'android:screenOrientation'
    def static final oriV = 'portrait'

    def static final theme = 'android:theme'
    def static final themeTS = '@android:style/Theme.Translucent.NoTitleBar'

    def static final THEME_NTS_USE_APP_COMPAT = '@style/Theme.AppCompat'
    def static final THEME_NTS_NOT_USE_APP_COMPAT = '@android:style/Theme.NoTitleBar'
    def static themeNTS = THEME_NTS_NOT_USE_APP_COMPAT

    /**
     * 动态生成插件化框架中需要的组件
     *
     * @param applicationID 宿主的 applicationID
     * @param config 用户配置
     * @return String       插件化框架中需要的组件
     */
    def static generateComponent(def applicationID, def config) {
        // 是否使用 AppCompat 库（涉及到默认主题）
        if (config.useAppCompat) {
            themeNTS = THEME_NTS_USE_APP_COMPAT
        } else {
            themeNTS = THEME_NTS_NOT_USE_APP_COMPAT
        }

        def writer = new StringWriter()
        def xml = new MarkupBuilder(writer)

        //基于 Groovy 的 MarkupBuilder api，根据 RepluginConfig 类中的配置，拼出组件坑位的xml 字符串。
        /* UI 进程 */
        xml.application {

            /* 需要编译期动态修改进程名的组件*/

            String pluginMgrProcessName = config.persistentEnable ? config.persistentName : applicationID

            // 常驻进程Provider
            provider(
                    "${name}":"com.qihoo360.replugin.component.process.ProcessPitProviderPersist",
                    "${authorities}":"${applicationID}.loader.p.main",
                    //exp => android:exported
                    "${exp}":"false",
                    //process => android:process
                    "${process}":"${pluginMgrProcessName}")

            provider(
                    "${name}":"com.qihoo360.replugin.component.provider.PluginPitProviderPersist",
                    "${authorities}":"${applicationID}.Plugin.NP.PSP",
                    "${exp}":"false",
                    "${process}":"${pluginMgrProcessName}")

            // ServiceManager 服务框架
            provider(
                    "${name}":"com.qihoo360.mobilesafe.svcmanager.ServiceProvider",
                    "${authorities}":"${applicationID}.svcmanager",
                    "${exp}":"false",
                    "${multiprocess}":"false",
                    "${process}":"${pluginMgrProcessName}")

            service(
                    "${name}":"com.qihoo360.replugin.component.service.server.PluginPitServiceGuard",
                    "${process}":"${pluginMgrProcessName}")

            /* 透明坑 */
            config.countTranslucentStandard.times {
                activity(
                        "${name}": "${applicationID}.${infix}N1NRTS${it}",
                        "${cfg}": "${cfgV}",
                        "${exp}": "${expV}",
                        "${ori}": "${oriV}",
                        "${theme}": "${themeTS}")
            }
            config.countTranslucentSingleTop.times {
                activity(
                        "${name}": "${applicationID}.${infix}N1STPTS${it}",
                        "${cfg}": "${cfgV}",
                        "${exp}": "${expV}",
                        "${ori}": "${oriV}",
                        "${theme}": "${themeTS}",
                        "${launchMode}": "singleTop")
            }
            config.countTranslucentSingleTask.times {
                activity(
                        "${name}": "${applicationID}.${infix}N1STTS${it}",
                        "${cfg}": "${cfgV}",
                        "${exp}": "${expV}",
                        "${ori}": "${oriV}",
                        "${theme}": "${themeTS}",
                        "${launchMode}": "singleTask")
            }
            config.countTranslucentSingleInstance.times {
                activity(
                        "${name}": "${applicationID}.${infix}N1SITS${it}",
                        "${cfg}": "${cfgV}",
                        "${exp}": "${expV}",
                        "${ori}": "${oriV}",
                        "${theme}": "${themeTS}",
                        "${launchMode}": "singleInstance")
            }

            /* 不透明坑 */
            config.countNotTranslucentStandard.times {
                activity(
                        "${name}": "${applicationID}.${infix}N1NRNTS${it}",
                        "${cfg}": "${cfgV}",
                        "${exp}": "${expV}",
                        "${ori}": "${oriV}",
                        "${theme}": "${themeNTS}")
            }
            config.countNotTranslucentSingleTop.times {
                activity(
                        "${name}": "${applicationID}.${infix}N1STPNTS${it}",
                        "${cfg}": "${cfgV}",
                        "${exp}": "${expV}",
                        "${ori}": "${oriV}",
                        "${theme}": "${themeNTS}",
                        "${launchMode}": "singleTop")
            }
            config.countNotTranslucentSingleTask.times {
                activity(
                        "${name}": "${applicationID}.${infix}N1STNTS${it}",
                        "${cfg}": "${cfgV}",
                        "${exp}": "${expV}",
                        "${ori}": "${oriV}",
                        "${theme}": "${themeNTS}",
                        "${launchMode}": "singleTask",)
            }
            config.countNotTranslucentSingleInstance.times {
                activity(
                        "${name}": "${applicationID}.${infix}N1SINTS${it}",
                        "${cfg}": "${cfgV}",
                        "${exp}": "${expV}",
                        "${ori}": "${oriV}",
                        "${theme}": "${themeNTS}",
                        "${launchMode}": "singleInstance")
            }

            /* TaskAffinity */
            // N1TA0NRTS1：UI进程->第0组->standardMode->透明主题->第1个坑位 (T: Task, NR: Standard, TS: Translucent)
            config.countTask.times { i ->
                config.countTranslucentStandard.times { j ->
                    activity(
                            "${name}": "${applicationID}.${infix}N1TA${i}NRTS${j}",
                            "${cfg}": "${cfgV}",
                            "${exp}": "${expV}",
                            "${ori}": "${oriV}",
                            "${theme}": "${themeTS}",
                            "${task}": ":t${i}")
                }
                config.countTranslucentSingleTop.times { j ->
                    activity(
                            "${name}": "${applicationID}.${infix}N1TA${i}STPTS${j}",
                            "${cfg}": "${cfgV}",
                            "${exp}": "${expV}",
                            "${ori}": "${oriV}",
                            "${theme}": "${themeTS}",
                            "${task}": ":t${i}",
                            "${launchMode}": "singleTop")
                }
                config.countTranslucentSingleTask.times { j ->
                    activity(
                            "${name}": "${applicationID}.${infix}N1TA${i}STTS${j}",
                            "${cfg}": "${cfgV}",
                            "${exp}": "${expV}",
                            "${ori}": "${oriV}",
                            "${theme}": "${themeTS}",
                            "${task}": ":t${i}",
                            "${launchMode}": "singleTask")
                }

                config.countNotTranslucentStandard.times { j ->
                    activity(
                            "${name}": "${applicationID}.${infix}N1TA${i}NRNTS${j}",
                            "${cfg}": "${cfgV}",
                            "${exp}": "${expV}",
                            "${ori}": "${oriV}",
                            "${theme}": "${themeNTS}",
                            "${task}": ":t${i}")
                }
                config.countNotTranslucentSingleTop.times { j ->
                    activity(
                            "${name}": "${applicationID}.${infix}N1TA${i}STPNTS${j}",
                            "${cfg}": "${cfgV}",
                            "${exp}": "${expV}",
                            "${ori}": "${oriV}",
                            "${theme}": "${themeNTS}",
                            "${task}": ":t${i}",
                            "${launchMode}": "singleTop")
                }
                config.countNotTranslucentSingleTask.times { j ->
                    activity(
                            "${name}": "${applicationID}.${infix}N1TA${i}STNTS${j}",
                            "${cfg}": "${cfgV}",
                            "${exp}": "${expV}",
                            "${ori}": "${oriV}",
                            "${theme}": "${themeNTS}",
                            "${task}": ":t${i}",
                            "${launchMode}": "singleTask")
                }
            }
        }
        // 删除 application 标签
        def normalStr = writer.toString().replace("<application>", "").replace("</application>", "")

        // 将单进程和多进程的组件相加
        normalStr + generateMultiProcessComponent(applicationID, config)
    }

    /**
     * 生成多进程坑位配置
     */
    def static generateMultiProcessComponent(def applicationID, def config) {
        if (config.countProcess == 0) {
            return ''
        }

        def writer = new StringWriter()
        def xml = new MarkupBuilder(writer)

        /* 自定义进程 */
        xml.application {
            config.countProcess.times { p ->
                config.countTranslucentStandard.times {
                    activity(
                            "${name}": "${applicationID}.${infix}P${p}NRTS${it}",
                            "${cfg}": "${cfgV}",
                            "${exp}": "${expV}",
                            "${ori}": "${oriV}",
                            "${theme}": "${themeTS}",
                            "${process}": ":p${p}")
                }
                config.countTranslucentSingleTop.times {
                    activity(
                            "${name}": "${applicationID}.${infix}P${p}STPTS${it}",
                            "${cfg}": "${cfgV}",
                            "${exp}": "${expV}",
                            "${ori}": "${oriV}",
                            "${theme}": "${themeTS}",
                            "${process}": ":p${p}",
                            "${launchMode}": "singleTop")
                }
                config.countTranslucentSingleTask.times {
                    activity(
                            "${name}": "${applicationID}.${infix}P${p}STTS${it}",
                            "${cfg}": "${cfgV}",
                            "${exp}": "${expV}",
                            "${ori}": "${oriV}",
                            "${theme}": "${themeTS}",
                            "${process}": ":p${p}",
                            "${launchMode}": "singleTask")
                }
                config.countTranslucentSingleInstance.times {
                    activity(
                            "${name}": "${applicationID}.${infix}P${p}SITS${it}",
                            "${cfg}": "${cfgV}",
                            "${exp}": "${expV}",
                            "${ori}": "${oriV}",
                            "${theme}": "${themeTS}",
                            "${process}": ":p${p}",
                            "${launchMode}": "singleInstance")
                }
                config.countNotTranslucentStandard.times {
                    activity(
                            "${name}": "${applicationID}.${infix}P${p}NRNTS${it}",
                            "${cfg}": "${cfgV}",
                            "${exp}": "${expV}",
                            "${ori}": "${oriV}",
                            "${theme}": "${themeNTS}",
                            "${process}": ":p${p}")
                }
                config.countNotTranslucentSingleTop.times {
                    activity(
                            "${name}": "${applicationID}.${infix}P${p}STPNTS${it}",
                            "${cfg}": "${cfgV}",
                            "${exp}": "${expV}",
                            "${ori}": "${oriV}",
                            "${theme}": "${themeNTS}",
                            "${process}": ":p${p}",
                            "${launchMode}": "singleTop")
                }
                config.countNotTranslucentSingleTask.times {
                    activity(
                            "${name}": "${applicationID}.${infix}P${p}STNTS${it}",
                            "${cfg}": "${cfgV}",
                            "${exp}": "${expV}",
                            "${ori}": "${oriV}",
                            "${theme}": "${themeNTS}",
                            "${process}": ":p${p}",
                            "${launchMode}": "singleTask")
                }
                config.countNotTranslucentSingleInstance.times {
                    activity(
                            "${name}": "${applicationID}.${infix}P${p}SINTS${it}",
                            "${cfg}": "${cfgV}",
                            "${exp}": "${expV}",
                            "${ori}": "${oriV}",
                            "${theme}": "${themeNTS}",
                            "${process}": ":p${p}",
                            "${launchMode}": "singleInstance")
                }

                /* TaskAffinity */
                config.countTask.times { i ->
                    config.countTranslucentStandard.times { j ->
                        activity(
                                "${name}": "${applicationID}.${infix}P${p}TA${i}NRTS${j}",
                                "${cfg}": "${cfgV}",
                                "${exp}": "${expV}",
                                "${ori}": "${oriV}",
                                "${theme}": "${themeTS}",
                                "${process}": ":p${p}",
                                "${task}": ":t${i}")
                    }
                    config.countTranslucentSingleTop.times { j ->
                        activity(
                                "${name}": "${applicationID}.${infix}P${p}TA${i}STPTS${j}",
                                "${cfg}": "${cfgV}",
                                "${exp}": "${expV}",
                                "${ori}": "${oriV}",
                                "${theme}": "${themeTS}",
                                "${launchMode}": "singleTop",
                                "${process}": ":p${p}",
                                "${task}": ":t${i}")
                    }
                    config.countTranslucentSingleTask.times { j ->
                        activity(
                                "${name}": "${applicationID}.${infix}P${p}TA${i}STTS${j}",
                                "${cfg}": "${cfgV}",
                                "${exp}": "${expV}",
                                "${ori}": "${oriV}",
                                "${theme}": "${themeTS}",
                                "${launchMode}": "singleTask",
                                "${process}": ":p${p}",
                                "${task}": ":t${i}")
                    }
                    config.countNotTranslucentStandard.times { j ->
                        activity(
                                "${name}": "${applicationID}.${infix}P${p}TA${i}NRNTS${j}",
                                "${cfg}": "${cfgV}",
                                "${exp}": "${expV}",
                                "${ori}": "${oriV}",
                                "${theme}": "${themeNTS}",
                                "${process}": ":p${p}",
                                "${task}": ":t${i}")
                    }
                    config.countNotTranslucentSingleTop.times { j ->
                        activity(
                                "${name}": "${applicationID}.${infix}P${p}TA${i}STPNTS${j}",
                                "${cfg}": "${cfgV}",
                                "${exp}": "${expV}",
                                "${ori}": "${oriV}",
                                "${theme}": "${themeNTS}",
                                "${launchMode}": "singleTop",
                                "${process}": ":p${p}",
                                "${task}": ":t${i}")
                    }
                    config.countNotTranslucentSingleTask.times { j ->
                        activity(
                                "${name}": "${applicationID}.${infix}P${p}TA${i}STNTS${j}",
                                "${cfg}": "${cfgV}",
                                "${exp}": "${expV}",
                                "${ori}": "${oriV}",
                                "${theme}": "${themeNTS}",
                                "${launchMode}": "singleTask",
                                "${process}": ":p${p}",
                                "${task}": ":t${i}")
                    }
                }

                /* Provider */
                // 支持插件中的 Provider 调用
                provider("${name}": "com.qihoo360.replugin.component.provider.PluginPitProviderP${p}",
                        "android:authorities": "${applicationID}.Plugin.NP.${p}",
                        "${process}": ":p${p}",
                        "${exp}": "${expV}")

                // fixme hujunjie 100 不写死
                // 支持进程Provider拉起
                provider("${name}": "com.qihoo360.replugin.component.process.ProcessPitProviderP${p}",
                        "android:authorities": "${applicationID}.loader.p.mainN${100 - p}",
                        "${process}": ":p${p}",
                        "${exp}": "${expV}")

                /* Service */
                // 支持使用插件的Service坑位
                // Added by Jiongxuan Zhang
                service("${name}": "com.qihoo360.replugin.component.service.server.PluginPitServiceP${p}",
                        "${process}": ":p${p}",
                        "${exp}": "${expV}")
            }
        }

        // 删除 application 标签
        return writer.toString().replace("<application>", "").replace("</application>", "")
    }
}
