package org.jellyfin.androidtv.ui.sipflix

import fi.iki.elonen.NanoHTTPD
import timber.log.Timber

class SipflixLoginServer(port: Int) : NanoHTTPD(port) {

    var onCredentials: ((username: String, password: String) -> Unit)? = null

    override fun serve(session: IHTTPSession): Response {
        return when (session.method) {
            Method.GET -> newFixedLengthResponse(Response.Status.OK, MIME_HTML, loginHtml())
            Method.POST -> {
                val files = mutableMapOf<String, String>()
                try {
                    session.parseBody(files)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to parse login POST body")
                }
                val params = session.parameters
                val username = params["username"]?.firstOrNull()?.trim() ?: ""
                val password = params["password"]?.firstOrNull() ?: ""

                if (username.isNotEmpty()) {
                    onCredentials?.invoke(username, password)
                    newFixedLengthResponse(Response.Status.OK, MIME_HTML, successHtml())
                } else {
                    newFixedLengthResponse(Response.Status.OK, MIME_HTML, loginHtml(error = true))
                }
            }
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
        }
    }

    private fun loginHtml(error: Boolean = false) = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
            <title>Sipflix Login</title>
            <style>
                * { box-sizing: border-box; margin: 0; padding: 0; }
                body {
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
                    background: #0F0F1A;
                    color: #fff;
                    min-height: 100vh;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    padding: 20px;
                }
                .card {
                    background: #1A1A2E;
                    border-radius: 16px;
                    padding: 36px 32px;
                    width: 100%;
                    max-width: 380px;
                    border: 1px solid rgba(255,255,255,0.08);
                }
                h1 {
                    color: #FF6B6B;
                    font-size: 28px;
                    letter-spacing: 4px;
                    text-align: center;
                    margin-bottom: 6px;
                }
                .subtitle {
                    text-align: center;
                    color: rgba(255,255,255,0.5);
                    font-size: 14px;
                    margin-bottom: 28px;
                }
                .error {
                    background: rgba(255,107,107,0.15);
                    border: 1px solid rgba(255,107,107,0.4);
                    border-radius: 8px;
                    padding: 10px 14px;
                    font-size: 13px;
                    color: #FF6B6B;
                    margin-bottom: 16px;
                    text-align: center;
                }
                label {
                    display: block;
                    font-size: 12px;
                    color: rgba(255,255,255,0.5);
                    text-transform: uppercase;
                    letter-spacing: 1px;
                    margin-bottom: 6px;
                    margin-top: 16px;
                }
                input {
                    width: 100%;
                    padding: 13px 14px;
                    background: rgba(255,255,255,0.06);
                    border: 1px solid rgba(255,255,255,0.12);
                    border-radius: 8px;
                    color: #fff;
                    font-size: 16px;
                    outline: none;
                    transition: border-color 0.2s;
                }
                input:focus { border-color: #FF6B6B; }
                button {
                    width: 100%;
                    padding: 14px;
                    background: #FF6B6B;
                    color: #fff;
                    border: none;
                    border-radius: 8px;
                    font-size: 16px;
                    font-weight: 600;
                    cursor: pointer;
                    margin-top: 24px;
                    transition: background 0.2s;
                }
                button:active { background: #e05555; }
            </style>
        </head>
        <body>
            <div class="card">
                <h1>SIPFLIX</h1>
                <p class="subtitle">Sign in to your account</p>
                ${if (error) """<div class="error">Please enter your username</div>""" else ""}
                <form method="POST" action="/">
                    <label for="username">Username</label>
                    <input type="text" id="username" name="username"
                           autocomplete="username" autocapitalize="none"
                           autocorrect="off" spellcheck="false" autofocus>
                    <label for="password">Password</label>
                    <input type="password" id="password" name="password"
                           autocomplete="current-password">
                    <button type="submit">Sign In</button>
                </form>
            </div>
        </body>
        </html>
    """.trimIndent()

    private fun successHtml() = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Sipflix</title>
            <style>
                body {
                    font-family: -apple-system, BlinkMacSystemFont, sans-serif;
                    background: #0F0F1A;
                    color: #fff;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    min-height: 100vh;
                    text-align: center;
                    padding: 20px;
                }
                .icon { font-size: 48px; margin-bottom: 16px; }
                h2 { color: #4ECDC4; font-size: 22px; margin-bottom: 10px; }
                p { color: rgba(255,255,255,0.5); font-size: 15px; }
            </style>
        </head>
        <body>
            <div>
                <div class="icon">✓</div>
                <h2>Signing in…</h2>
                <p>You can close this tab and look at your TV.</p>
            </div>
        </body>
        </html>
    """.trimIndent()
}
