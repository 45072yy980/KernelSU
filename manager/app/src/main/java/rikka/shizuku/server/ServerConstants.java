package rikka.shizuku.server;

public class ServerConstants {

    public static final int MANAGER_APP_NOT_FOUND = 50;
    public static final int SYSTEM_SERVICE_TIMEOUT = 51;

    public static final String PERMISSION = "moe.shizuku.manager.permission.API_V23";

    /**
     * 管理端（KernelSU Manager）应用包名。
     *
     * <p>默认取编译时的 {@code BuildConfig.APPLICATION_ID}，但在"隐藏管理器"
     * （随机包名）场景下，实际安装包名与编译时不一致。此时由
     * {@link rikka.shizuku.server.ShizukuService#main(String[])} 从系统属性
     * {@code shizuku.manager.packageName} 读取实际包名并调用
     * {@link #overrideManagerApplicationId(String)} 覆盖。
     *
     * <p>注意：此变量不再是 {@code final}，因为运行时可能被覆盖。所有引用方
     * 都应在需要时读取当前值，而不是缓存早期值。
     */
    public static String MANAGER_APPLICATION_ID = me.diksu.ultra.BuildConfig.APPLICATION_ID;
    public static String REQUEST_PERMISSION_ACTION = MANAGER_APPLICATION_ID + ".intent.action.REQUEST_PERMISSION";

    /**
     * 运行时覆盖管理器包名（隐藏管理器/随机包名场景）。
     *
     * <p>调用后会同步更新 {@link #MANAGER_APPLICATION_ID} 和
     * {@link #REQUEST_PERMISSION_ACTION}，确保 server 端所有依赖包名的逻辑
     * （查找管理器 ApplicationInfo、投递 Binder、拉起授权 Activity 等）都指向
     * 实际运行的管理器。
     *
     * @param packageName 实际运行的管理器包名；为空或 null 时忽略
     */
    public static void overrideManagerApplicationId(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return;
        }
        MANAGER_APPLICATION_ID = packageName;
        REQUEST_PERMISSION_ACTION = packageName + ".intent.action.REQUEST_PERMISSION";
    }

    public static final int BINDER_TRANSACTION_getApplications = 10001;

    /** manager-only：读取某 uid 的分权（shellOnly）标记 */
    public static final int BINDER_TRANSACTION_getShellOnly = 10002;

    /** manager-only：设置某 uid 的分权（shellOnly）标记 */
    public static final int BINDER_TRANSACTION_setShellOnly = 10003;

    /** manager-only: read the server's persisted log (returns text) */
    public static final int BINDER_TRANSACTION_getLog = 10004;

    /** manager-only: clear the server's persisted log */
    public static final int BINDER_TRANSACTION_clearLog = 10005;
}
