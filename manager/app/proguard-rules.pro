# Protobuf
-shrinkunusedprotofields

# Commons-compress
-dontwarn com.github.luben.zstd.**

# ===== Built-in Shizuku (ported from FolkPatch) =====
# app_process launches the server entry by hardcoded class-name string;
# R8 must not rename or strip any of these, otherwise the server exits instantly
# with ClassNotFoundException in release builds.
-keep class rikka.parcelablelist.ParcelableListSlice { *; }
-keep class rikka.shizuku.server.** { *; }
-keep class moe.shizuku.server.** { *; }
-keep class moe.shizuku.api.BinderContainer { *; }
# ServiceStarter is launched by app_process via a hardcoded class-name string in
# the generated user-service command; obfuscating it breaks UserService launch.
-keep class moe.shizuku.starter.** { *; }
-keep class moe.shizuku.common.util.** { *; }
-keep class rikka.rish.** { *; }
-keep class rikka.hidden.compat.** { *; }

# Manager-side Shizuku bridge (provider/receiver/permission activity are
# referenced by the manifest and by the server via hardcoded class names)
-keep class me.weishu.kernelsu.shizuku.ShizukuManagerProvider { *; }
-keep class me.weishu.kernelsu.shizuku.ShizukuReceiver { *; }
-keep class me.weishu.kernelsu.shizuku.ShizukuPermissionActivity { *; }
