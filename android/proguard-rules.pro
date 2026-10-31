# Scene2D Skin styles are looked up by class name/type. Preserve native bindings.
-keep class com.badlogic.gdx.scenes.scene2d.ui.** { *; }
-keep class com.badlogic.gdx.backends.android.** { *; }
-keepclasseswithmembernames,includedescriptorclasses class * { native <methods>; }
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
# Wire messages use Gson JsonObject, so no gameplay DTO reflective field rules are needed.
-dontwarn org.puremvc.java.**
