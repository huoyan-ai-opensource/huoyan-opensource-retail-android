#include <jni.h>
#include <unistd.h>
#include <pthread.h>
#include <errno.h>
#include <android/log.h>  // Android NDK 日志头文件
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

// 获取cpu核心数
int getCores() {
    int cores = sysconf(_SC_NPROCESSORS_CONF);
    return cores;
}

extern "C" JNIEXPORT jint JNICALL
Java_cc_huoyan_retail_ThreadAffinity_getCores(JNIEnv *env, jobject thiz) {
    return getCores();
}
// 绑定线程到指定cpu
extern "C" JNIEXPORT jint JNICALL
Java_cc_huoyan_retail_ThreadAffinity_bindThreadToCore(JNIEnv *env, jobject thiz, jint core) {
    int num = getCores();
    if (core >= num) {
        return -1;
    }
    cpu_set_t mask;
    CPU_ZERO(&mask);
    CPU_SET(core, &mask);
    if (sched_setaffinity(0, sizeof(mask), &mask) == -1) {
        return -1;
    }
    return 0;
}

// 绑定多个指定cpu
extern "C" JNIEXPORT jint JNICALL
Java_cc_huoyan_retail_ThreadAffinity_bindThreadToCores(JNIEnv *env, jobject thiz, jintArray cores) {
    // 获取核心数量
    int num_cores = getCores();

    // 获取传入的核心数组
    jint *core_array = env->GetIntArrayElements(cores, NULL);
    jsize core_count = env->GetArrayLength(cores);

    cpu_set_t mask;
    CPU_ZERO(&mask);

    // 设置所有指定的核心
    for (int i = 0; i < core_count; i++) {
        jint core = core_array[i];
        if (core >= 0 && core < num_cores) {
            CPU_SET(core, &mask);
        } else {
            // 如果有一个核心号无效，释放资源并返回错误
            env->ReleaseIntArrayElements(cores, core_array, 0);
            return -1;
        }
    }

    // 设置线程亲和性
    if (sched_setaffinity(0, sizeof(mask), &mask) == -1) {
        env->ReleaseIntArrayElements(cores, core_array, 0);
        return -1;
    }

    // 释放资源
    env->ReleaseIntArrayElements(cores, core_array, 0);
    return 0;
}

// 绑定进程程到指定cpu
extern "C" JNIEXPORT jint JNICALL
Java_cc_huoyan_retail_ThreadAffinity_bindPidToCore(JNIEnv *env, jobject thiz, jint pid,
                                                  jint core) {
    int num = getCores();
    if (core >= num) {
        return -1;
    }
    cpu_set_t mask;
    CPU_ZERO(&mask);
    CPU_SET(core, &mask);
    if (sched_setaffinity(pid, sizeof(mask), &mask) == -1) {
        return -1;
    }
    return 0;
}

// 绑定进程到多个 CPU 核心
extern "C" JNIEXPORT jint JNICALL
Java_cc_huoyan_retail_ThreadAffinity_bindPidToCores(
        JNIEnv* env,
        jobject thiz,
        jint pid,
        jintArray cores
) {
    // rk3588s 绑定小核必须要先写入到特定的文件里面
    FILE* su_process = popen("su", "w");
    if (su_process) {
        fprintf(su_process, "echo %d > /dev/cpuset/background/tasks\n", pid);
        fprintf(su_process, "exit\n");  // 退出 su
        pclose(su_process);
        __android_log_print(ANDROID_LOG_INFO, "cc.huoyan.retail", " 写入 PID成功！");
    } else {
        __android_log_print(ANDROID_LOG_ERROR, "cc.huoyan.retail", "Command failed");
    }

    // 获取 CPU 核心总数
    int num_cores = getCores();

    // 获取 Java 传入的核心数组
    jint* core_array = env->GetIntArrayElements(cores, nullptr);
    jsize core_count = env->GetArrayLength(cores);

    cpu_set_t mask;
    CPU_ZERO(&mask);  // 清空 CPU 集合

    // 设置所有指定的核心
    for (int i = 0; i < core_count; i++) {
        jint core = core_array[i];
        if (core >= 0 && core < num_cores) {
            CPU_SET(core, &mask);  // 将核心添加到集合
        } else {
            // 如果核心号无效，释放资源并返回错误
            env->ReleaseIntArrayElements(cores, core_array, 0);
            return -2;  // 使用-2表示无效的核心号
        }
    }

    // 设置进程的 CPU 亲和性
    if (sched_setaffinity(pid, sizeof(mask), &mask) == -1) {
        int error = errno;
        __android_log_print(ANDROID_LOG_INFO, "cc.huoyan.retail", "Error: %d (%s)", error, strerror(error));
        env->ReleaseIntArrayElements(cores, core_array, 0);
        return -1;  // -1表示设置亲和性失败
    }
    // 释放资源
    env->ReleaseIntArrayElements(cores, core_array, 0);
    return 0;  // 0表示成功
}