# 当前 release 未开启代码压缩，此文件仅作占位。
# 如果以后开启 minifyEnabled，Room 生成的实现类需要保留：
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**
