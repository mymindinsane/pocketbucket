package dev.pocketbucket.data

sealed class ApiFailure(val userMessage: String) : Exception(userMessage) {
    class Unauthorized : ApiFailure("Authentication was rejected (401). Check your credentials. If using Basic authentication, ask your administrator whether REST Basic authentication is permitted; browser SSO login does not enable it.")
    class Forbidden : ApiFailure("Access denied (403). Check account permissions, token scope and server authentication policy.")
    class NotFound : ApiFailure("Resource not found (404). Check the server context path and your access to this resource.")
    class Timeout : ApiFailure("The server timed out. Check your connection and device VPN, then retry.")
    class Connection : ApiFailure("Could not reach the server securely. Check the URL, system certificate trust and device VPN.")
    class InvalidResponse : ApiFailure("The server returned an unexpected response. Confirm this is Bitbucket Server/Data Center, not Cloud or an SSO login page.")
    class Http(val status: Int) : ApiFailure("The server returned HTTP $status. Try again later or contact your administrator.")
}
