# Unity calls these classes through JNI by name, so R8 can't see the callers.
-keep class io.github.khbworks.gameplatform.unity.** { *; }
