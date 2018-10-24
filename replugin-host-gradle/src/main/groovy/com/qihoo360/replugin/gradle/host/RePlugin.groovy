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

package com.qihoo360.replugin.gradle.host

import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import com.qihoo360.replugin.gradle.host.creator.FileCreators
import com.qihoo360.replugin.gradle.host.creator.IFileCreator
import com.qihoo360.replugin.gradle.host.creator.impl.json.PluginBuiltinJsonCreator
import com.qihoo360.replugin.gradle.host.handlemanifest.ComponentsGenerator
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel

/**
 * @author RePlugin Team
 */
public class Replugin implements Plugin<Project> {

    def static TAG = AppConstant.TAG
    def project
    def config

    @Override
    public void apply(Project project) {
        println "${TAG} Welcome to replugin world ! "

        this.project = project

        /* Extensions */
        //将RepluginConfig类的常量配置信息赋值给AppConstant.USER_CONFIG，在接下来checkUserConfig(config)检查配置信息时有用到,主要检查配置信息数据类型是否正确。
        project.extensions.create(AppConstant.USER_CONFIG, RepluginConfig)

        //判断project是否有 使用插件 apply plugin: 'com.android.application'
        //如果希望判断是否有libraryPlugin，可以写 if (project.plugins.hasPlugin(LibraryPlugin))
        if (project.plugins.hasPlugin(AppPlugin)) {

            /*
            获取project中的AppExtension类型extension，即在你的app模块builde.gradle定义的闭包,如下

            android {
                ....
            }
             */
            def android = project.extensions.getByType(AppExtension)

            //如果是Android library库，那么就将applicationVariants替换为libraryVariants
            android.applicationVariants.all { variant ->

                /*
                  这里遍历次数是， productFlavors里面的渠道 * debug和release
                      例如这里productFlavors有 baidu 和 QQ 所以，遍历4次，分别对应  QQDebug QQRelease  baiduDebug baiduRelease
                 */

                addShowPluginTask(variant)

                if (config == null) {
                    config = project.extensions.getByName(AppConstant.USER_CONFIG)
                    checkUserConfig(config)
                }

                //得到值就是 => applicationId "com.qihoo360.replugin.sample.host"
                def appID = variant.generateBuildConfig.appPackageName

                def newManifest = ComponentsGenerator.generateComponent(appID, config)

                def variantData = variant.variantData
                def scope = variantData.scope

                //host generate task  创建rpGenerate{productFlavor}{Debug/Release}HostConfig Task
                def generateHostConfigTaskName = scope.getTaskName(AppConstant.TASK_GENERATE, "HostConfig") //生成gradle task名字
                def generateHostConfigTask = project.task(generateHostConfigTaskName)  //创建task

                generateHostConfigTask.doLast {
                    FileCreators.createHostConfig(project, variant, config)
                }

                //replugin-plugin
                generateHostConfigTask.group = AppConstant.TASKS_GROUP

                //depends on build config task       generate{productFlavor}{Debug/Release}BuildConfig
                String generateBuildConfigTaskName = variant.getVariantData().getScope().getGenerateBuildConfigTask().name
                def generateBuildConfigTask = project.tasks.getByName(generateBuildConfigTaskName)  //生成BuildConfig.java的task
                if (generateBuildConfigTask) {
                    /*
                        因为generateHostConfigTask中创建的RePluginHostConfig.java希望放置到编译输出目录
                        ..\replugin-sample\host\app\build\generated\source\buildConfig\{productFlavors}\{buildTypes}\...下，
                        所以此task依赖于生成 BuildConfig.java 的task并设置为 BuildConfigTask 执行完后，就执行HostConfigTask
                     */

                    generateHostConfigTask.dependsOn generateBuildConfigTask  //generateHostConfigTask 依赖 generateBuildConfigTask

                    //https://blog.csdn.net/lzyzsd/article/details/46935405
                    //finalizedBy 表示执行命令 gradle generateBuildConfigTask 运行后 运行 generateHostConfigTask,
                    // 不必直接 gralde generateHostConfigTask来运行 generateHostConfigTask
                    generateBuildConfigTask.finalizedBy generateHostConfigTask
                }

                //json generate task        rpGenerate{productFlavor}{Debug/Release}BuiltinJson
                def generateBuiltinJsonTaskName = scope.getTaskName(AppConstant.TASK_GENERATE, "BuiltinJson")
                def generateBuiltinJsonTask = project.task(generateBuiltinJsonTaskName)

                generateBuiltinJsonTask.doLast {
                    FileCreators.createBuiltinJson(project, variant, config)
                }
                generateBuiltinJsonTask.group = AppConstant.TASKS_GROUP

                //depends on mergeAssets Task             merge{productFlavor}{Debug/Release}Assets
                String mergeAssetsTaskName = variant.getVariantData().getScope().getMergeAssetsTask().name
                def mergeAssetsTask = project.tasks.getByName(mergeAssetsTaskName)
                if (mergeAssetsTask) {

                    /*
                    因为此task中创建的 plugins-builtin.json 希望放置到编译输出目录...\replugin-sample\host\app\build\intermediates\assets\{productFlavors}\{buildTypes}\...下，
                    所以此task依赖于merge assets文件 的task并设置为 mergeAssetsTask 执行完后，就执行BuiltinJsonTask。
                     */

                    generateBuiltinJsonTask.dependsOn mergeAssetsTask
                    mergeAssetsTask.finalizedBy generateBuiltinJsonTask
                }

                variant.outputs.each { output ->
                    output.processManifest.doLast {
                        output.processManifest.outputs.files.each { File file ->
                            def manifestFile = null;
                            //在gradle plugin 3.0.0之前，file是文件，且文件名为AndroidManifest.xml
                            //在gradle plugin 3.0.0之后，file是目录，且不包含AndroidManifest.xml，需要自己拼接
                            //除了目录和AndroidManifest.xml之外，还可能会包含manifest-merger-debug-report.txt等不相干的文件，过滤它
                            if ((file.name.equalsIgnoreCase("AndroidManifest.xml") && !file.isDirectory()) || file.isDirectory()) {
                                if (file.isDirectory()) {
                                    //3.0.0之后，自己拼接AndroidManifest.xml
                                    manifestFile = new File(file, "AndroidManifest.xml")
                                } else {
                                    //3.0.0之前，直接使用
                                    manifestFile = file
                                }
                                //检测文件是否存在
                                if (manifestFile != null && manifestFile.exists()) {
                                    println "${AppConstant.TAG} handle manifest: ${manifestFile}"
                                    def updatedContent = manifestFile.getText("UTF-8").replaceAll("</application>", newManifest + "</application>")
                                    manifestFile.write(updatedContent, 'UTF-8')
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 添加 【查看所有插件信息】 任务，task并未挂到android gradle task上，即task不会执行，这个task是方便调试时查看插件信息的
    def addShowPluginTask(def variant) {
        def variantData = variant.variantData
        def scope = variantData.scope

        //rpShowPlugins{productFlavor}{Debug/Release} 任务名
        def showPluginsTaskName = scope.getTaskName(AppConstant.TASK_SHOW_PLUGIN, "")
        def showPluginsTask = project.task(showPluginsTaskName)

        showPluginsTask.doLast {
            IFileCreator creator = new PluginBuiltinJsonCreator(project, variant, config)
            def dir = creator.getFileDir()

            if (!dir.exists()) {
                println "${AppConstant.TAG} The ${dir.absolutePath} does not exist "
                println "${AppConstant.TAG} pluginsInfo=null"
                return
            }

            String fileContent = creator.getFileContent()
            if (null == fileContent) {
                return
            }

            new File(dir, creator.getFileName()).write(fileContent, 'UTF-8')
        }
        showPluginsTask.group = AppConstant.TASKS_GROUP

        //get mergeAssetsTask name
        String mergeAssetsTaskName = variant.getVariantData().getScope().getMergeAssetsTask().name
        //get real gradle task
        def mergeAssetsTask = project.tasks.getByName(mergeAssetsTaskName)

        //depend on mergeAssetsTask so that assets have been merged
        if (mergeAssetsTask) {
            showPluginsTask.dependsOn mergeAssetsTask
        }

    }

    /**
     * 检查用户配置项
     */
    def checkUserConfig(config) {
/*
        def persistentName = config.persistentName

        if (persistentName == null || persistentName.trim().equals("")) {
            project.logger.log(LogLevel.ERROR, "\n---------------------------------------------------------------------------------")
            project.logger.log(LogLevel.ERROR, " ERROR: persistentName can'te be empty, please set persistentName in replugin. ")
            project.logger.log(LogLevel.ERROR, "---------------------------------------------------------------------------------\n")
            System.exit(0)
            return
        }
*/
        doCheckConfig("countProcess", config.countProcess)
        doCheckConfig("countTranslucentStandard", config.countTranslucentStandard)
        doCheckConfig("countTranslucentSingleTop", config.countTranslucentSingleTop)
        doCheckConfig("countTranslucentSingleTask", config.countTranslucentSingleTask)
        doCheckConfig("countTranslucentSingleInstance", config.countTranslucentSingleInstance)
        doCheckConfig("countNotTranslucentStandard", config.countNotTranslucentStandard)
        doCheckConfig("countNotTranslucentSingleTop", config.countNotTranslucentSingleTop)
        doCheckConfig("countNotTranslucentSingleTask", config.countNotTranslucentSingleTask)
        doCheckConfig("countNotTranslucentSingleInstance", config.countNotTranslucentSingleInstance)
        doCheckConfig("countTask", config.countTask)

        println '--------------------------------------------------------------------------'
//        println "${TAG} appID=${appID}"
        println "${TAG} useAppCompat=${config.useAppCompat}"
        // println "${TAG} persistentName=${config.persistentName}"
        println "${TAG} countProcess=${config.countProcess}"

        println "${TAG} countTranslucentStandard=${config.countTranslucentStandard}"
        println "${TAG} countTranslucentSingleTop=${config.countTranslucentSingleTop}"
        println "${TAG} countTranslucentSingleTask=${config.countTranslucentSingleTask}"
        println "${TAG} countTranslucentSingleInstance=${config.countTranslucentSingleInstance}"
        println "${TAG} countNotTranslucentStandard=${config.countNotTranslucentStandard}"
        println "${TAG} countNotTranslucentSingleTop=${config.countNotTranslucentSingleTop}"
        println "${TAG} countNotTranslucentSingleTask=${config.countNotTranslucentSingleTask}"
        println "${TAG} countNotTranslucentSingleInstance=${config.countNotTranslucentSingleInstance}"

        println "${TAG} countTask=${config.countTask}"
        println '--------------------------------------------------------------------------'
    }

    /**
     * 检查配置项是否正确
     * @param name 配置项
     * @param count 配置值
     */
    def doCheckConfig(def name, def count) {
        if (!(count instanceof Integer) || count < 0) {
            this.project.logger.log(LogLevel.ERROR, "\n--------------------------------------------------------")
            this.project.logger.log(LogLevel.ERROR, " ${TAG} ERROR: ${name} must be an positive integer. ")
            this.project.logger.log(LogLevel.ERROR, "--------------------------------------------------------\n")
            System.exit(0)
        }
    }
}

class RepluginConfig {

    /** 自定义进程的数量(除 UI 和 Persistent 进程) */
    def countProcess = 3

    /** 是否使用常驻进程？ */
    def persistentEnable = true

    /** 常驻进程名称（也就是上面说的 Persistent 进程，开发者可自定义）*/
    def persistentName = ':GuardService'

    /** 背景不透明的坑的数量 */
    def countNotTranslucentStandard = 6
    def countNotTranslucentSingleTop = 2
    def countNotTranslucentSingleTask = 3
    def countNotTranslucentSingleInstance = 2

    /** 背景透明的坑的数量 */
    def countTranslucentStandard = 2
    def countTranslucentSingleTop = 2
    def countTranslucentSingleTask = 2
    def countTranslucentSingleInstance = 3

    /** 宿主中声明的 TaskAffinity 的组数 */
    def countTask = 2

    /**
     * 是否使用 AppCompat 库
     * com.android.support:appcompat-v7:25.2.0
     */
    def useAppCompat = false

    /** HOST 向下兼容的插件版本 */
    def compatibleVersion = 10

    /** HOST 插件版本 */
    def currentVersion = 12

    /** plugins-builtin.json 文件名自定义,默认是 "plugins-builtin.json" */
    def builtInJsonFileName = "plugins-builtin.json"

    /** 是否自动管理 plugins-builtin.json 文件,默认自动管理 */
    def autoManageBuiltInJsonFile = true

    /** assert目录下放置插件文件的目录自定义,默认是 assert 的 "plugins" */
    def pluginDir = "plugins"

    /** 插件文件的后缀自定义,默认是".jar" 暂时支持 jar 格式*/
    def pluginFilePostfix = ".jar"

    /** 当发现插件目录下面有不合法的插件 jar (有可能是特殊定制 jar)时是否停止构建,默认是 true */
    def enablePluginFileIllegalStopBuild = true
}
