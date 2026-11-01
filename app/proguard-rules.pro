# Keep NewPipeExtractor classes used through reflection / JitPack.
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**
-dontwarn org.jsoup.**
-dontwarn org.mozilla.javascript.**
-dontwarn javax.annotation.**
