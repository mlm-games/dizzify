# Obfuscation stays off: the release build is not exercised on-device in CI, and the app
# resolves several names reflectively (persisted enum names, sealed-class discriminators), so
# renaming is not something to enable without a signed release smoke test. R8 still performs
# dead-code elimination and resource shrinking.
-dontobfuscate

# kotlinx.serialization resolves generated `$$serializer` companions and nested serializers
# reflectively. The library ships consumer rules for this, but pin them explicitly so a
# dependency bump cannot silently break settings (de)serialization in release builds.
-keepattributes Signature, InnerClasses, *Annotation*
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.dizzify.**$$serializer { *; }
-keepclassmembers class app.dizzify.** {
    *** Companion;
}
-keepclasseswithmembers class app.dizzify.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Sealed HomeItem is persisted polymorphically; the discriminator is the serial name of the
# per-subclass descriptor, so the generated serializers must keep their names.
-keep class app.dizzify.data.HomeItem$* { *; }

-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

-keep class app.dizzify.ui.screens.SettingsScreenKt { *; }
