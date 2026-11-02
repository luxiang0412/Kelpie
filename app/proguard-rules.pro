# sshj：算法、KeyProvider、Factory 大量通过反射 / 名字加载
-keep class net.schmizz.** { *; }
-keep class com.hierynomus.** { *; }
-dontwarn net.schmizz.**
-dontwarn com.hierynomus.**
# BouncyCastle 作为 JCA provider：Service 通过类名反射实例化
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
# 可选依赖
-dontwarn org.slf4j.**
-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**
-dontwarn com.jcraft.jzlib.**
-dontwarn net.i2p.crypto.eddsa.**
-keep class net.i2p.crypto.eddsa.** { *; }
# kotlinx.serialization：导航路由的序列化器
-keepattributes *Annotation*, InnerClasses, Signature
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class io.github.luxiang0412.kelpie.nav.Routes** { *; }
