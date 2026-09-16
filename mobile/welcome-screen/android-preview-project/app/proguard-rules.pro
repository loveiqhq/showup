# proguard-rules.pro
# ShowUp · keep rules for the release build
#
# R8 removes every class, method and field it cannot prove is used, and rewrites what is left. That
# is what makes a release build smaller and faster, and it is also why anything reached WITHOUT a
# direct code reference has to be named here: R8 cannot see through reflection, resource lookups by
# string, or a name that only ever appears in a manifest.
#
# Nothing is added here speculatively. Every rule below has a reason next to it, because a keep rule
# with no reason is one nobody can ever safely delete.

# ── libphonenumber ──────────────────────────────────────────────────────────
# The numbering rules for every country ship as binary metadata inside the jar, loaded at runtime by
# building a resource path from the region code -- a string, assembled at runtime. R8 sees no
# reference to those files from any code path, so without this it can strip the very data the
# library exists to read, and validation then fails for every country at once.
#
# This is exactly the class of breakage that only appears in release: the debug build keeps
# everything, so the app works perfectly right up until the build that goes to the store.
-keep class com.google.i18n.phonenumbers.** { *; }
-keepclassmembers class com.google.i18n.phonenumbers.** { *; }
-keep,includedescriptorclasses class com.google.i18n.phonenumbers.Phonemetadata** { *; }

# ── Compose ─────────────────────────────────────────────────────────────────
# The Compose runtime and its compiler plugin already ship their own consumer rules, so the
# framework needs nothing from us. Left as a note rather than as rules, so nobody adds a
# belt-and-braces `-keep class androidx.compose.**` that would undo most of the shrinking.

# ── Kotlin metadata ─────────────────────────────────────────────────────────
# Enum entries are looked up by name by valueOf(), which is reflection. TutorialRouting's enums are
# only used from Kotlin code so they would survive anyway, but rememberSaveable stores enums through
# the Android Bundle machinery, which does go through their names.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── Crash reports worth reading ─────────────────────────────────────────────
# Without this, every line number in a release stack trace is gone and a crash report says only
# which obfuscated class it happened in. The mapping file that turns those back into real names is
# written to app/build/outputs/mapping/release/ -- it MUST be kept for every release that ships, or
# that build's crash reports can never be decoded.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Retrofit and the generated API client ───────────────────────────────────
# Retrofit builds its implementations at runtime from the annotations on an interface, through a
# dynamic proxy. R8 sees an interface nobody instantiates and a set of annotations nobody reads, so
# without these it strips the generic signatures Retrofit needs to know what to deserialize into --
# and the failure is a confusing "Unable to create converter" at the first call, in release only.
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
# Retrofit's own optional platform bits, referenced but not present on Android.
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn retrofit2.KotlinExtensions

# OkHttp names two optional dependencies it works fine without.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ── kotlinx.serialization ───────────────────────────────────────────────────
# Serializers are generated at COMPILE time, which is most of why this library was chosen over a
# reflective one: there is no per-model keep rule to forget. What is still needed is the companion
# that holds each generated serializer, because it is only ever reached reflectively by name.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Tink, via androidx.security:security-crypto ─────────────────────────────
#
# EncryptedTokenStore's master key comes from Tink, and Tink's classes are annotated with Error
# Prone's @Immutable -- an annotation that exists at COMPILE time and is deliberately absent from
# the runtime classpath. R8 sees the reference, cannot resolve it, and fails the build.
#
# Worth recording WHEN this appeared, because it says something about the dependency: never,
# until 10 September 2026, when the profile flow became the first code to actually construct an
# EncryptedTokenStore. security-crypto had been a declared dependency with no reachable caller,
# so R8 removed the whole tree and never had to resolve anything inside it. The rule was not
# missing before; the code was.
-dontwarn com.google.errorprone.annotations.**
