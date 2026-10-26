# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ── Aalap: optimise, never rename or remove ─────────────────────────────────
# YouTube's stream decoding (NewPipe, Rhino), the JSON models, Ktor and the web logins'
# JavaScript bridges find classes and fields by name, so everything keeps its name and stays.
-dontobfuscate
-dontshrink
# Merging classes is the one optimisation that can break code found by name.
-optimizations !class/merging/*,!code/allocation/variable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable
-ignorewarnings

# Debug logging is stripped from the app people install (it built thousands of log lines,
# ten or more per recommendation pick, using the processor on every song). Warnings and
# errors stay.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
-assumenosideeffects class timber.log.Timber$Tree {
    public void v(...);
    public void d(...);
    public void i(...);
}
-assumenosideeffects class timber.log.Timber$Forest {
    public void v(...);
    public void d(...);
    public void i(...);
}
