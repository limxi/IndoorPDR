# Realm
-keep class io.realm.annotations.RealmModule
-keep @io.realm.annotations.RealmModule class *
-keep class io.realm.internal.Keep
-keep @io.realm.internal.Keep class *
-dontwarn javax.**
-dontwarn io.realm.**

# MapView
-keep class com.onlylemi.mapview.library.** { *; }

# 项目模型类
-keep class com.audioar.wifipositioning.model.** { *; }