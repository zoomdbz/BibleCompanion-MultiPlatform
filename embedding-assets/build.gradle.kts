plugins {
    id("com.android.asset-pack")
}

assetPack {
    packName.set("embedding_assets")
    dynamicDelivery {
        deliveryType.set("install-time")
    }
}
