# RePlugin Host Gradle

RePlugin Host Gradle是一个Gradle插件，由 **主程序** 负责引入。

该Gradle插件主要负责在主程序的编译期中做一些事情，此外，开发者可通过修改其属性而做一些自定义的操作。

大致包括：

* 生成带 RePlugin 插件坑位的 AndroidManifest.xml（允许自定义数量）
* 生成HostBuildConfig类，方便插件框架读取并自定义其属性
* 生成 plugins-builtin.json，json中含有插件应用的信息，包名，插件名，插件路径等。



知识点:
* replugin-host-gradle.properties 文件名用来指定插件名，即在宿主中使用插件时的 apply plugin: 'replugin-host-gradle' 就是文件名
* 文件中的implementation-class用来指定插件实现类