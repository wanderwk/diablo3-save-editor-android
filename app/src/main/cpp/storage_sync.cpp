// Looks like a generic native storage-sync helper. It is not -- see project
// history for why. Layer 1 of a two-layer signing-certificate check: runs
// from JNI_OnLoad (fires automatically on System.loadLibrary, no exported
// JNI-named function to grep for) and, on a confirmed mismatch, ends the
// process directly via kill(SIGKILL) -- no Java exception, no tombstone,
// no crash log. See CacheWarmup.kt for the independent, decoupled Java-side
// second layer.
//
// ANTI_TAMPER_ENABLED and EXPECTED_SIG_HASH are injected as compiler
// defines only for release builds (app/build.gradle.kts) -- this file
// compiles to a no-op in debug, so local/CI debug builds are unaffected.

#include <jni.h>
#include <unistd.h>
#include <signal.h>
#include <cstdint>

#if defined(ANTI_TAMPER_ENABLED)

namespace {

// Clears and swallows any pending Java exception, returning whether one was
// pending. Every JNI call below is followed by this: on any anomaly (a
// reflected class/method missing, a null return, an OEM PackageManager
// quirk) we bail out and do nothing, rather than kill a legitimate install
// over an edge case we didn't anticipate. Only a cleanly computed hash that
// actually mismatches reaches the kill() call at the bottom.
bool ClearPendingException(JNIEnv* env) {
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return true;
    }
    return false;
}

void CheckSignatureAndMaybeDie(JNIEnv* env) {
    jclass activityThreadClass = env->FindClass("android/app/ActivityThread");
    if (ClearPendingException(env) || activityThreadClass == nullptr) return;

    jmethodID currentApplicationMethod = env->GetStaticMethodID(
        activityThreadClass, "currentApplication", "()Landroid/app/Application;");
    if (ClearPendingException(env) || currentApplicationMethod == nullptr) return;

    jobject application = env->CallStaticObjectMethod(activityThreadClass, currentApplicationMethod);
    if (ClearPendingException(env) || application == nullptr) return;

    jclass contextClass = env->GetObjectClass(application);
    jmethodID getPackageManagerMethod = env->GetMethodID(
        contextClass, "getPackageManager", "()Landroid/content/pm/PackageManager;");
    jmethodID getPackageNameMethod = env->GetMethodID(
        contextClass, "getPackageName", "()Ljava/lang/String;");
    if (ClearPendingException(env) || getPackageManagerMethod == nullptr || getPackageNameMethod == nullptr) return;

    jobject pm = env->CallObjectMethod(application, getPackageManagerMethod);
    auto packageName = static_cast<jstring>(env->CallObjectMethod(application, getPackageNameMethod));
    if (ClearPendingException(env) || pm == nullptr || packageName == nullptr) return;

    jclass pmClass = env->GetObjectClass(pm);
    jmethodID getPackageInfoMethod = env->GetMethodID(
        pmClass, "getPackageInfo", "(Ljava/lang/String;I)Landroid/content/pm/PackageInfo;");
    if (ClearPendingException(env) || getPackageInfoMethod == nullptr) return;

    const jint kGetSignatures = 0x00000040;
    jobject packageInfo = env->CallObjectMethod(pm, getPackageInfoMethod, packageName, kGetSignatures);
    if (ClearPendingException(env) || packageInfo == nullptr) return;

    jclass packageInfoClass = env->GetObjectClass(packageInfo);
    jfieldID signaturesField = env->GetFieldID(
        packageInfoClass, "signatures", "[Landroid/content/pm/Signature;");
    if (ClearPendingException(env) || signaturesField == nullptr) return;

    auto signatures = static_cast<jobjectArray>(env->GetObjectField(packageInfo, signaturesField));
    if (ClearPendingException(env) || signatures == nullptr || env->GetArrayLength(signatures) == 0) return;

    jobject signature = env->GetObjectArrayElement(signatures, 0);
    if (ClearPendingException(env) || signature == nullptr) return;

    jclass signatureClass = env->GetObjectClass(signature);
    jmethodID toByteArrayMethod = env->GetMethodID(signatureClass, "toByteArray", "()[B");
    if (ClearPendingException(env) || toByteArrayMethod == nullptr) return;

    auto sigBytes = static_cast<jbyteArray>(env->CallObjectMethod(signature, toByteArrayMethod));
    if (ClearPendingException(env) || sigBytes == nullptr) return;

    // Hash via java.security.MessageDigest (also reached through raw JNI,
    // not a linked crypto lib) so this matches, byte for byte, the same
    // SHA-256(cert DER bytes) computed at build time -- see build.gradle.kts.
    jclass mdClass = env->FindClass("java/security/MessageDigest");
    if (ClearPendingException(env) || mdClass == nullptr) return;
    jmethodID getInstanceMethod = env->GetStaticMethodID(
        mdClass, "getInstance", "(Ljava/lang/String;)Ljava/security/MessageDigest;");
    if (ClearPendingException(env) || getInstanceMethod == nullptr) return;
    jstring algo = env->NewStringUTF("SHA-256");
    jobject md = env->CallStaticObjectMethod(mdClass, getInstanceMethod, algo);
    if (ClearPendingException(env) || md == nullptr) return;
    jmethodID digestMethod = env->GetMethodID(mdClass, "digest", "([B)[B");
    if (ClearPendingException(env) || digestMethod == nullptr) return;
    auto digestBytes = static_cast<jbyteArray>(env->CallObjectMethod(md, digestMethod, sigBytes));
    if (ClearPendingException(env) || digestBytes == nullptr || env->GetArrayLength(digestBytes) < 8) return;

    jbyte* raw = env->GetByteArrayElements(digestBytes, nullptr);
    if (raw == nullptr) return;
    uint64_t hash = 0;
    for (int i = 0; i < 8; i++) {
        hash = (hash << 8) | static_cast<uint8_t>(raw[i]);
    }
    env->ReleaseByteArrayElements(digestBytes, raw, JNI_ABORT);

    if (hash != EXPECTED_SIG_HASH) {
        kill(getpid(), SIGKILL);
    }
}

}  // namespace

#endif  // ANTI_TAMPER_ENABLED

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* /*reserved*/) {
#if defined(ANTI_TAMPER_ENABLED)
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_OK && env != nullptr) {
        CheckSignatureAndMaybeDie(env);
    }
#else
    (void) vm;
#endif
    return JNI_VERSION_1_6;
}
