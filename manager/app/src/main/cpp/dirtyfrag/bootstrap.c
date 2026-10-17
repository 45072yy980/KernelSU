#include <dirent.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <unistd.h>

#define BLKROSET   0x125d
/*
 * 这两个路径原来写死成 /data/user_de/0/df.root/...，只有包名叫 df.root 的
 * 应用才存在那样的目录。搬进本项目之后改成跟随 bootstrap 自己的位置：
 * 内核模块用绝对路径 exec 它，argv[0] 就是那个路径，dirname 之后就是暂存目录。
 */
static char g_ksud_stage[256] = "/data/user_de/0/df.root/ksud";
#define KSUD       "/data/adb/ksud"
static char g_prefs_path[256] = "/data/user_de/0/df.root/shared_prefs/dfroot.xml";
#define MODULES_DIR "/data/adb/modules"

static int pref_true(const char *buf, const char *key)
{
    char needle[64];
    snprintf(needle, sizeof(needle), "name=\"%s\"", key);
    char *p = strstr(buf, needle);
    if (!p) return 0;
    char *tag_end = strchr(p, '>');
    char *v = strstr(p, "value=\"true\"");
    return v && tag_end && v < tag_end;
}

static int read_prefs(char *su_manager, size_t su_manager_size, int *soft_reboot,
                      int *disable_modules)
{
    int fd = open(g_prefs_path, O_RDONLY);
    if (fd < 0) return -1;

    char buf[4096];
    int n = read(fd, buf, sizeof(buf) - 1);
    close(fd);
    if (n <= 0) return -1;
    buf[n] = '\0';

    char *p = strstr(buf, "name=\"su_manager\">");
    if (!p) return -1;
    p += strlen("name=\"su_manager\">");
    char *end = strchr(p, '<');
    if (!end) return -1;
    size_t len = end - p;
    if (len == 0 || len >= su_manager_size) return -1;
    memcpy(su_manager, p, len);
    su_manager[len] = '\0';

    *soft_reboot = pref_true(buf, "soft_reboot");
    *disable_modules = pref_true(buf, "disable_modules");

    return 0;
}

static int adopt_zygote_env(void)
{
    FILE *f = popen("pidof zygote64 zygote", "r");
    if (!f) return -1;
    int pid = 0;
    fscanf(f, "%d", &pid);
    pclose(f);
    if (!pid) return -1;

    char path[32];
    snprintf(path, sizeof(path), "/proc/%d/environ", pid);
    int fd = open(path, O_RDONLY);
    if (fd < 0) return -1;
    static char buf[16384];
    int n = read(fd, buf, sizeof(buf) - 1);
    close(fd);
    if (n <= 0) return -1;
    buf[n] = '\0';
    for (char *p = buf, *end = buf + n; p < end; p += strlen(p) + 1)
        putenv(p);
    return 0;
}

static int should_ro(const char *name)
{
    size_t len = strlen(name);
    if (!strcmp(name, "super"))  return 1;
    if (!strcmp(name, "misc"))   return 1;
    if (!strcmp(name, "steady")) return 1;
    if (len >= 2 && name[len - 2] == '_' &&
        (name[len - 1] == 'a' || name[len - 1] == 'b'))
        return 1;
    return 0;
}

static int set_partitions_ro(void)
{
    DIR *dir = opendir("/dev/block/by-name");
    if (!dir)
        return -1;

    struct dirent *ent;
    while ((ent = readdir(dir))) {
        if (!should_ro(ent->d_name))
            continue;

        char path[128];
        snprintf(path, sizeof(path), "/dev/block/by-name/%s", ent->d_name);

        int fd = open(path, O_RDONLY);
        if (fd < 0)
            continue;

        struct stat st;
        if (fstat(fd, &st) == 0 && S_ISBLK(st.st_mode)) {
            int on = 1;
            ioctl(fd, BLKROSET, &on);
        }
        close(fd);
    }

    closedir(dir);
    return 0;
}

static int late_load_running(void)
{
    DIR *d = opendir("/proc");
    if (!d)
        return 0;
    struct dirent *e;
    char path[64], buf[256];
    int found = 0;
    while ((e = readdir(d)) != NULL) {
        char *end;
        strtol(e->d_name, &end, 10);
        if (*end) continue;
        snprintf(path, sizeof(path), "/proc/%s/cmdline", e->d_name);
        int fd = open(path, O_RDONLY | O_CLOEXEC);
        if (fd < 0) continue;
        int n = read(fd, buf, sizeof(buf) - 1);
        close(fd);
        if (n <= 0) continue;
        buf[n] = '\0';
        for (int i = 0; i < n; i++)
            if (!buf[i]) buf[i] = ' ';
        if (strstr(buf, "ksud") && strstr(buf, "late-load")) {
            found = 1;
            break;
        }
    }
    closedir(d);
    return found;
}

static int run_ctx(const char *ctx, char *const argv[])
{
    pid_t pid = fork();
    if (pid < 0)
        return -1;
    if (pid == 0) {
        if (ctx) {
            int fd = open("/proc/self/attr/exec", O_WRONLY);
            if (fd >= 0) { write(fd, ctx, strlen(ctx)); close(fd); }
        }
        execv(argv[0], argv);
        _exit(127);
    }
    int status;
    waitpid(pid, &status, 0);
    return WIFEXITED(status) ? WEXITSTATUS(status) : -1;
}

static void touch(const char *path)
{
    int fd = open(path, O_CREAT | O_WRONLY, 0666);
    if (fd >= 0)
        close(fd);
}

/* Mark every installed module disabled before ksud runs. A broken module
 * otherwise loads on the next boot and bootloops the device. */
static int disable_modules(void)
{
    DIR *dir = opendir(MODULES_DIR);
    if (!dir)
        return -1;

    struct dirent *ent;
    while ((ent = readdir(dir))) {
        if (ent->d_name[0] == '.')
            continue;
        char path[256];
        snprintf(path, sizeof(path), MODULES_DIR "/%s/disable", ent->d_name);
        touch(path);
    }
    closedir(dir);
    return 0;
}

/* 从 argv[0] 推出暂存目录，供下面两处路径使用。 */
static void resolve_stage_dir(int argc, char **argv)
{
    if (argc < 1 || !argv[0] || argv[0][0] != '/')
        return;
    char base[240];
    snprintf(base, sizeof(base), "%s", argv[0]);
    char *slash = strrchr(base, '/');
    if (!slash)
        return;
    *slash = '\0';
    snprintf(g_ksud_stage, sizeof(g_ksud_stage), "%s/ksud", base);
    snprintf(g_prefs_path, sizeof(g_prefs_path), "%s/shared_prefs/dfroot.xml", base);
}

/* 内核模块只把 selinux_state 改成 permissive，自己加载完就退出、不会恢复。
 * 成功时 ksud 起来会接管（它自己切回 enforcing）；失败的时候没人收拾，
 * 机器就带着 permissive 一直在跑。这里把失败的收尾补上。 */
static void set_enforcing(int on)
{
    int fd = open("/sys/fs/selinux/enforce", O_WRONLY);
    if (fd < 0)
        return;
    const char c = on ? '1' : '0';
    write(fd, &c, 1);
    close(fd);
}

/* 失败收尾：恢复 enforcing，并删掉 shellcode 的互斥标记 /dev/df，
 * 这样用户可以立刻重试，不用先重启。 */
static int fail_close(int code)
{
    set_enforcing(1);
    unlink("/dev/df");
    return code;
}

int main(int argc, char **argv)
{
    resolve_stage_dir(argc, argv);
    touch("/dev/dfm1");
    char su_manager[256];
    int soft_reboot, disable_mods;
    if (read_prefs(su_manager, sizeof(su_manager), &soft_reboot, &disable_mods) != 0) {
        touch("/dev/dfme0");
        return fail_close(1);
    }

    touch("/dev/dfm2");
    if (adopt_zygote_env())
        touch("/dev/dfmw0");

    touch("/dev/dfm3");
    if (set_partitions_ro())
        touch("/dev/dfmw1");

    if (disable_mods) {
        touch("/dev/dfm4");
        if (disable_modules()) {
            touch("/dev/dfme1");
            return fail_close(1);
        }
    }

    touch("/dev/dfm5");
    // ksu only allows init to switch to ksud context and we are in kernel context now
    // once ksu starts, selinux is enforcing, but here its still permissive
    // switch our context while we still can
    pid_t pid = fork();
    if (pid < 0)
        return fail_close(1);
    if (pid == 0) {
        int fd = open("/proc/self/attr/current", O_WRONLY);
        if (fd >= 0) {
            const char *ctx = "u:r:init:s0";
            write(fd, ctx, strlen(ctx));
            close(fd);
        }
        if (run_ctx(NULL, (char *[]){ g_ksud_stage, "late-load", "--package-name", su_manager, NULL }) == 0) {
            touch("/dev/dfm6");
            if (soft_reboot) {
                while (late_load_running()) sleep(1);
                run_ctx("u:r:ksu:s0", (char *[]){ KSUD, "soft-reboot", NULL });
            }
        } else {
            touch("/dev/dfme2");
        }
        _exit(0);
    }
    int status;
    waitpid(pid, &status, 0);

    /* 子进程成功时会在最后 touch /dev/dfm6；没有它说明 late-load 没成，
     * 这时候同样要把机器恢复原样，而不是留一个 permissive 的内核在跑。 */
    if (access("/dev/dfm6", F_OK) != 0)
        return fail_close(1);

    return 0;
}
