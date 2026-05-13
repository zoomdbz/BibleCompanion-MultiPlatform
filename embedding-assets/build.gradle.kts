plugins {
    id("com.android.asset-pack")
}

// fast-follow delivery: Google Play downloads this asset pack automatically
// right after the user installs the app, in the background. The semantic
// search index isn't available during the brief window before the pack
// finishes downloading; EmbeddingSearch.ensureBuilt() returns silently when
// assets aren't loadable, so the app falls back to keyword-only search until
// the pack lands. Switched from install-time (200 MB compressed cap on base
// APK + all install-time packs combined) because this pack alone is ~250 MB
// compressed, well over the cap.
assetPack {
    packName.set("embedding_assets")
    dynamicDelivery {
        deliveryType.set("fast-follow")
    }
}
