package com.dreamdisplays.core.protocol

import com.dreamdisplays.api.capability.ServerFeature

/** True if this server snapshot advertises [feature]. */
fun ServerHello.hasFeature(feature: ServerFeature): Boolean =
    feature.wire in allowedFeatures
