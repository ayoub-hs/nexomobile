package com.nexopos.erp.core.network

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

private const val ONLINE_REQUIRED_MESSAGE = "Network connection required for this feature."

fun Throwable.isConnectivityIssue(): Boolean {
    var current: Throwable? = this

    while (current != null) {
        when (current) {
            is UnknownHostException,
            is ConnectException,
            is SocketTimeoutException -> return true
            is IOException -> return true
        }

        current = current.cause
    }

    return false
}

fun Throwable.onlineOnlyMessage(fallback: String): String {
    return if (isConnectivityIssue()) {
        ONLINE_REQUIRED_MESSAGE
    } else {
        message ?: fallback
    }
}
