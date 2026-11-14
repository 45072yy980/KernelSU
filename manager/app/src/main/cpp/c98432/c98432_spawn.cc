/*
 * 把 tun 描述符交给 libc98432.so 的启动器。
 *
 * 为什么需要它：VpnService 拿到的 tun fd 是 Java 侧的 fd 编号，ProcessBuilder
 * 只能继承 0/1/2，没法把任意 fd 交给子进程。所以这里 fork 之后 dup2 到 fd 3，
 * 再 exec。子进程的 stdout/stderr 重定向到日志文件，父进程 waitpid 拿退出码。
 */

#include <jni.h>

#include <fcntl.h>
#include <sys/wait.h>
#include <unistd.h>

#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

namespace {

std::string toUtf(JNIEnv *env, jstring s) {
    if (s == nullptr) return {};
    const char *c = env->GetStringUTFChars(s, nullptr);
    if (c == nullptr) return {};
    std::string out(c);
    env->ReleaseStringUTFChars(s, c);
    return out;
}

}  // namespace

extern "C"
JNIEXPORT jint JNICALL
Java_me_diksu_ultra_exploit_c98432_NativeSpawner_run(JNIEnv *env, jclass,
                                                     jstring jpath,
                                                     jobjectArray jargs,
                                                     jint tunFd,
                                                     jstring jlogPath) {
    const std::string path = toUtf(env, jpath);
    const std::string logPath = toUtf(env, jlogPath);
    if (path.empty()) return -2;

    std::vector<std::string> owned;
    std::vector<char *> argv;
    owned.emplace_back(path);
    const jsize n = jargs == nullptr ? 0 : env->GetArrayLength(jargs);
    for (jsize i = 0; i < n; i++) {
        auto item = static_cast<jstring>(env->GetObjectArrayElement(jargs, i));
        owned.push_back(toUtf(env, item));
        env->DeleteLocalRef(item);
    }
    for (auto &s : owned) argv.push_back(const_cast<char *>(s.c_str()));
    argv.push_back(nullptr);

    pid_t pid = fork();
    if (pid < 0) return -1;
    if (pid == 0) {
        // 只留一条日志通道：1/2 都指到同一个文件，父进程按顺序读就行。
        int logfd = logPath.empty() ? -1 : open(logPath.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0600);
        if (logfd >= 0) {
            dup2(logfd, STDOUT_FILENO);
            dup2(logfd, STDERR_FILENO);
            if (logfd > STDERR_FILENO) close(logfd);
        }
        int devnull = open("/dev/null", O_RDONLY);
        if (devnull >= 0) {
            dup2(devnull, STDIN_FILENO);
            if (devnull > STDERR_FILENO) close(devnull);
        }
        // fd 3 是约定：载荷的 --tun-fd 读这个编号。
        if (tunFd >= 0 && dup2(tunFd, 3) < 0) _exit(126);
        if (tunFd > 3) close(tunFd);
        execv(argv[0], argv.data());
        _exit(127);
    }

    int status = 0;
    // 载荷自己会限制轮数，超时由 Kotlin 侧的工作线程负责。
    if (waitpid(pid, &status, 0) < 0) return -1;
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    if (WIFSIGNALED(status)) return 128 + WTERMSIG(status);
    return -1;
}
