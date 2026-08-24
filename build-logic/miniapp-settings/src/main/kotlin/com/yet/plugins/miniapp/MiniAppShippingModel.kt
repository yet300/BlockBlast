package com.yet.plugins.miniapp

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import javax.inject.Inject

abstract class MiniAppShippingModel @Inject constructor(objects: ObjectFactory) {
    val declarations: ListProperty<MiniAppDeclaration> =
        objects.listProperty(MiniAppDeclaration::class.java)
}
