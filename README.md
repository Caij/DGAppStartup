# DGAppStartup
The DGAppStartup library provides a straightforward, performant way to initialize components at application startup. Both library developers and app developers can use DGAppStartup to streamline startup sequences and explicitly set the order of initialization.

At the same time, the DGAppStartup support async await and sync await. And topological ordering is used to ensure the initialization order of dependent components.

# Dependency
add this in your root build.gradle

```
allprojects {
    repositories {
        maven { url "https://www.jitpack.io" }
    }
}
```

Then, add the library to your module build.gradle
```
dependencies {
    implementation 'com.github.Caij:DGAppStartup:latest.release.version'
}
```

For example
```
Config config = new Config();
config.isStrictMode = BuildConfig.DEBUG;
new DGAppStartup.Builder()
        .add(new MainTaskA())
        .add(new MainTaskB())
        .add(new MainTaskC())
        .add(new TaskD())
        .add(new TaskE())
        .setConfig(config)
        .addTaskListener(new MonitorTaskListener(Tag.TAG, true))
        .setExecutorService(ThreadManager.getInstance().WORK_EXECUTOR)
        .addOnProjectExecuteListener(new OnProjectListener() {
            @Override
            public void onProjectStart() {

            }

            @Override
            public void onProjectFinish() {

            }

            @Override
            public void onStageFinish() {

            }
        })
        .create()
        .start()
        .await();
```
# Related Articles

[框架篇DGAppStartup](https://juejin.cn/post/7009961273009897502)

# License

    Copyright 2021 Caij

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
