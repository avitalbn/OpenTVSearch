# Keep Hilt-generated components
-keep class dagger.hilt.** { *; }
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keepclassmembers class * { @dagger.hilt.android.lifecycle.HiltViewModel <init>(...); }
