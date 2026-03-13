# ProGuard rules for the desktop module.
# Add keep rules here if you enable code shrinking/obfuscation.

# Allow optional libraries referenced by dependencies but not bundled.
-dontwarn javax.annotation.**
-dontwarn javax.annotation.meta.**
-dontwarn kotlinx.serialization.**
-dontwarn kotlinx.datetime.serializers.**
-dontwarn org.conscrypt.**
