# Keep external libraries and JNI/reflection entrypoints intact; rename application internals.
-dontshrink
-dontoptimize
-dontwarn
-keep class com.** { *; }
-keep class org.** { *; }
-keep public class vn.swordol.desktop.DesktopLauncher {
    public static void main(java.lang.String[]);
}
-keepclassmembers class vn.swordol.protocol.** { <fields>; }
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
-adaptclassstrings
