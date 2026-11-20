package com.example.methodmesh.modules.sms

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object SmsModule : MethodMeshModule {
    override val moduleId = "sms"
    override val displayName = "Send SMS"
    override val summary = "Send an identical short SMS message individually to one or more phone numbers."

    override fun as100Methods() = listOf(As100SendSmsMethod)

    override fun rilBindings() = listOf(
        RilBinding("send SMS", As100SendSmsMethod.ID, "Send an identical SMS individually to one or more phone numbers"),
        RilBinding("send text message", As100SendSmsMethod.ID, "Send any short value or custom message by SMS")
    )

    override fun capabilityScreens() = listOf(SmsCapabilityScreen)

    override fun capabilitySettings() = mapOf(
        As100SendSmsMethod.ID to listOf(
            MethodSetting.TextSetting("sms_phone", "Phone number", group = "Recipient", defaultValue = ""),
            MethodSetting.TextSetting("sms_message", "Message", group = "Message", defaultValue = "")
        )
    )
}
