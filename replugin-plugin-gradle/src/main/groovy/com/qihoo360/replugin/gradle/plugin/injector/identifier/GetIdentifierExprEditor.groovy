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
 *
 */

package com.qihoo360.replugin.gradle.plugin.injector.identifier

import com.qihoo360.replugin.gradle.plugin.inner.CommonData
import javassist.CannotCompileException
import javassist.expr.ExprEditor
import javassist.expr.MethodCall

/**
 * @author RePlugin Team
 */
public class GetIdentifierExprEditor extends ExprEditor {

    public def filePath

    @Override
    void edit(MethodCall m) throws CannotCompileException {
        String clsName = m.getClassName()
        String methodName = m.getMethodName()

        /*
        方式一
            int indentify = getResources().getIdentifier(“com.test.demo:drawable/icon”,null,null);
            第一个参数格式是：包名 + : +资源文件夹名 + / +资源名；是这种格式 然后其他的可以为null

        方式二
            intindentify= getResources().getIdentifier(“icon”, “drawable”, “com.test.demo”);
            第一个参数为ID名，第二个为资源属性是ID或者是Drawable，第三个为包名。
         */
        if (clsName.equalsIgnoreCase('android.content.res.Resources')) {
            if (methodName == 'getIdentifier') {

                //edit(...)中，遍历到调用方为android.content.res.Resources且方法为getIdentifier的MethodCall，动态适配这些MethodCall中的方法参数
                //1）调用原型： int id = res.getIdentifier("com.qihoo360.replugin.sample.demo2:layout/from_demo1", null, null);
                //2）replace statement：'{ $3 = \"' + CommonData.appPackage + '\"; ' +'$_ = $proceed($$);' + ' }'，
                // 为特殊变量$3赋值，即动态修改参数3的值为插件的包名；
                // '$_ = $proceed($$);'表示按原样调用。

                //CommonData.appPackage 是插件的 包名
                m.replace('{ $3 = \"' + CommonData.appPackage + '\"; ' +
                        '$_ = $proceed($$);' +
                        ' }')

                //GetIdentifierCall => /Users/chenxiaokai/Downloads/github/RePlugin/replugin-plugin-library/replugin-plugin-lib/build/intermediates/bundles/default/classes/com/qihoo360/replugin/c.class getIdentifier():-1
                //GetIdentifierCall => /Users/chenxiaokai/Downloads/github/RePlugin/replugin-plugin-library/replugin-plugin-lib/build/intermediates/bundles/default/classes/com/qihoo360/replugin/c.class getIdentifier():-1
                println " GetIdentifierCall => ${filePath} ${} ${methodName}():${m.lineNumber}"
            }
        }
    }
}
