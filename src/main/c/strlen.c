#include <jni.h>
#include <string.h>

JNIEXPORT jint JNICALL Java_com_redos_CStringLength_getStringLength(JNIEnv *env, jobject obj, jstring javaString) {
    const char *nativeString = (*env)->GetStringUTFChars(env, javaString, 0);
    jint length = strlen(nativeString);
    (*env)->ReleaseStringUTFChars(env, javaString, nativeString);
    return length;
}
