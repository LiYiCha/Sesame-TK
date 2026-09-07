# Consumer Proguard rules for the updater module.
-keep class com.updater.** { *; }
-keep interface com.updater.** { *; }
-keep class * implements java.io.Serializable { *; }
-dontwarn com.updater.**
