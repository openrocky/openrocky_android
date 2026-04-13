//
// OpenRocky — Voice-first AI Agent
// https://github.com/openrocky
//
// Developed by everettjf with the assistance of Claude Code and Codex.
// Date: 2026-04-13
// Copyright (c) 2026 everettjf. All rights reserved.
//

package com.xnu.rocky.runtime.tools

import android.content.Context
import android.util.Base64
import com.xnu.rocky.providers.SecureStore
import com.xnu.rocky.runtime.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import javax.net.ssl.SSLSocketFactory

@Serializable
data class EmailConfig(
    val smtpHost: String = "",
    val smtpPort: Int = 465,
    val username: String = "",
    val useTLS: Boolean = true
) {
    val isConfigured: Boolean get() = smtpHost.isNotBlank() && username.isNotBlank() && smtpPort > 0

    companion object {
        private const val CONFIG_KEY = "rocky_email_config"
        private const val PASSWORD_KEY = "rocky_email_smtp_password"
        private val json = Json { ignoreUnknownKeys = true }

        val gmailPreset = EmailConfig(smtpHost = "smtp.gmail.com", smtpPort = 465, useTLS = true)
        val outlookPreset = EmailConfig(smtpHost = "smtp.office365.com", smtpPort = 587, useTLS = true)
        val qqPreset = EmailConfig(smtpHost = "smtp.qq.com", smtpPort = 465, useTLS = true)

        fun load(context: Context): EmailConfig? {
            val prefs = context.getSharedPreferences("openrocky_email", Context.MODE_PRIVATE)
            val data = prefs.getString(CONFIG_KEY, null) ?: return null
            return try { json.decodeFromString<EmailConfig>(data) } catch (_: Exception) { null }
        }

        fun save(context: Context, config: EmailConfig) {
            val prefs = context.getSharedPreferences("openrocky_email", Context.MODE_PRIVATE)
            prefs.edit().putString(CONFIG_KEY, json.encodeToString(config)).apply()
        }

        fun remove(context: Context) {
            val prefs = context.getSharedPreferences("openrocky_email", Context.MODE_PRIVATE)
            prefs.edit().remove(CONFIG_KEY).apply()
            SecureStore.delete(PASSWORD_KEY)
        }

        fun savePassword(password: String) {
            SecureStore.set(PASSWORD_KEY, password)
        }

        fun loadPassword(): String? {
            return SecureStore.get(PASSWORD_KEY)
        }

        fun hasPassword(): Boolean = loadPassword() != null
    }
}

class SmtpEmailService(private val context: Context) {

    companion object {
        private const val TAG = "SmtpEmail"
    }

    suspend fun send(to: List<String>, subject: String, body: String, cc: List<String> = emptyList(), bcc: List<String> = emptyList()): String = withContext(Dispatchers.IO) {
        val config = EmailConfig.load(context) ?: throw Exception("Email not configured. Please set up SMTP in Settings.")
        if (!config.isConfigured) throw Exception("Email configuration incomplete.")
        val password = EmailConfig.loadPassword() ?: throw Exception("SMTP password not found.")

        val messageID = "<${UUID.randomUUID()}@rocky.local>"
        val mime = buildMIME(config.username, to, cc, subject, body, messageID)

        sendViaSMTP(config, password, config.username, to + cc + bcc, mime)
        LogManager.info("Email sent to ${to.joinToString()} messageID=$messageID", TAG)
        messageID
    }

    private fun buildMIME(from: String, to: List<String>, cc: List<String>, subject: String, body: String, messageID: String): String {
        val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)
        val dateString = dateFormat.format(Date())

        val encodedSubject = "=?UTF-8?B?${Base64.encodeToString(subject.toByteArray(), Base64.NO_WRAP)}?="
        val encodedBody = Base64.encodeToString(body.toByteArray(), Base64.DEFAULT)

        val headers = mutableListOf(
            "From: $from",
            "To: ${to.joinToString(", ")}",
        )
        if (cc.isNotEmpty()) headers.add("Cc: ${cc.joinToString(", ")}")
        headers.addAll(listOf(
            "Date: $dateString",
            "Subject: $encodedSubject",
            "Message-ID: $messageID",
            "MIME-Version: 1.0",
            "Content-Type: text/plain; charset=UTF-8",
            "Content-Transfer-Encoding: base64",
            "X-Mailer: OpenRocky/1.0"
        ))

        return headers.joinToString("\r\n") + "\r\n\r\n" + encodedBody + "\r\n"
    }

    private fun sendViaSMTP(config: EmailConfig, password: String, from: String, recipients: List<String>, data: String) {
        val socket = if (config.useTLS && config.smtpPort == 465) {
            SSLSocketFactory.getDefault().createSocket(config.smtpHost, config.smtpPort)
        } else {
            java.net.Socket(config.smtpHost, config.smtpPort)
        }

        socket.use { sock ->
            val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
            val writer = BufferedWriter(OutputStreamWriter(sock.getOutputStream()))

            fun send(cmd: String) {
                writer.write(cmd + "\r\n")
                writer.flush()
            }

            fun read(): String {
                val sb = StringBuilder()
                do {
                    val line = reader.readLine() ?: break
                    sb.appendLine(line)
                    // Multi-line responses have "-" at position 3, last line has " "
                    if (line.length >= 4 && line[3] == ' ') break
                } while (true)
                return sb.toString().trim()
            }

            // Greeting
            val greeting = read()
            if (!greeting.startsWith("220")) throw Exception("Bad SMTP greeting: $greeting")

            // EHLO
            send("EHLO rocky.local")
            val ehlo = read()
            if (!ehlo.contains("250")) throw Exception("EHLO failed: $ehlo")

            // STARTTLS for port 587
            if (config.smtpPort == 587 && config.useTLS) {
                send("STARTTLS")
                val starttls = read()
                if (!starttls.startsWith("220")) throw Exception("STARTTLS failed: $starttls")

                // Upgrade to TLS
                val sslSocket = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                    .createSocket(sock, config.smtpHost, config.smtpPort, true) as javax.net.ssl.SSLSocket
                sslSocket.startHandshake()

                // Re-create reader/writer on TLS socket
                val tlsReader = BufferedReader(InputStreamReader(sslSocket.getInputStream()))
                val tlsWriter = BufferedWriter(OutputStreamWriter(sslSocket.getOutputStream()))

                fun tlsSend(cmd: String) { tlsWriter.write(cmd + "\r\n"); tlsWriter.flush() }
                fun tlsRead(): String {
                    val sb = StringBuilder()
                    do {
                        val line = tlsReader.readLine() ?: break
                        sb.appendLine(line)
                        if (line.length >= 4 && line[3] == ' ') break
                    } while (true)
                    return sb.toString().trim()
                }

                // Re-EHLO after STARTTLS
                tlsSend("EHLO rocky.local")
                val ehlo2 = tlsRead()
                if (!ehlo2.contains("250")) throw Exception("EHLO after STARTTLS failed: $ehlo2")

                // AUTH + send on TLS socket
                performAuthAndSend(::tlsSend, ::tlsRead, from, password, recipients, data)
                return
            }

            // AUTH + send on plain/implicit-TLS socket
            performAuthAndSend(::send, ::read, from, password, recipients, data)
        }
    }

    private fun performAuthAndSend(
        send: (String) -> Unit,
        read: () -> String,
        from: String,
        password: String,
        recipients: List<String>,
        data: String
    ) {
        // AUTH LOGIN
        send("AUTH LOGIN")
        val authResp = read()
        if (!authResp.startsWith("334")) throw Exception("AUTH LOGIN failed: $authResp")

        send(Base64.encodeToString(from.toByteArray(), Base64.NO_WRAP))
        val userResp = read()
        if (!userResp.startsWith("334")) throw Exception("Username rejected: $userResp")

        send(Base64.encodeToString(password.toByteArray(), Base64.NO_WRAP))
        val passResp = read()
        if (!passResp.startsWith("235")) throw Exception("Authentication failed: $passResp")

        // MAIL FROM
        send("MAIL FROM:<$from>")
        val mailResp = read()
        if (!mailResp.startsWith("250")) throw Exception("MAIL FROM failed: $mailResp")

        // RCPT TO
        for (recipient in recipients) {
            send("RCPT TO:<$recipient>")
            val rcptResp = read()
            if (!rcptResp.startsWith("250")) throw Exception("RCPT TO <$recipient> failed: $rcptResp")
        }

        // DATA
        send("DATA")
        val dataResp = read()
        if (!dataResp.startsWith("354")) throw Exception("DATA failed: $dataResp")

        send(data + "\r\n.")
        val sendResp = read()
        if (!sendResp.startsWith("250")) throw Exception("Message rejected: $sendResp")

        // QUIT
        send("QUIT")
        try { read() } catch (_: Exception) {}
    }
}
