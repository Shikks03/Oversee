package com.example.oversee.utils

import android.content.Context
import android.content.Intent

/**
 * Extension function for Context to send updates to the Child Dashboard Console.
 * This can now be called from any Activity or Service using:
 * sendConsoleUpdate("Your message here")
 */
fun Context.sendConsoleUpdate(message: String) {
    val intent = Intent("com.example.oversee.CONSOLE_UPDATE").apply {
        putExtra("message", message)
        // Set package to ensure only our app receives this broadcast
        setPackage(packageName)
    }
    sendBroadcast(intent)
}