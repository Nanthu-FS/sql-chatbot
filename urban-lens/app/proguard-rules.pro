# MapLibre, OkHttp, Room and WorkManager ship their own consumer rules.
# Keep line numbers for readable crash reports.
-keepattributes SourceFile,LineNumberTable

# Gson (used by MapLibre's GeoJSON classes) needs generic signatures to survive R8 full mode.
-keepattributes Signature
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
