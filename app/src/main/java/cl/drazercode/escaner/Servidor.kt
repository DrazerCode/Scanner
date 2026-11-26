package cl.drazercode.escaner

import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

/**
 * Comunicación con Unir PDF en el PC del policlínico, por la red local (HTTP, sin internet).
 *   GET  /api/ping   -> {"ok":true,"app":"UnirPDF","equipo":"<PC>"}
 *   POST /api/subir  -> multipart "paciente" + "pdf"; responde {"ok":true,"archivo":"..."}
 */
object Servidor {
    const val PUERTO_POR_DEFECTO = 8080

    class ErrorServidor(mensaje: String) : Exception(mensaje)

    /** Acepta "192.168.1.50", "192.168.1.50:8080" o "http://192.168.1.50:8080/". */
    fun normalizar(direccion: String): String {
        var s = direccion.trim().trimEnd('/')
        if (s.isEmpty()) throw ErrorServidor("Configura primero la dirección del PC con Unir PDF (⚙).")
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
        val sinEsquema = s.substringAfter("://")
        if (!sinEsquema.substringBefore('/').contains(':')) {
            s = s.substringBefore("://") + "://" + sinEsquema.substringBefore('/') + ":$PUERTO_POR_DEFECTO"
        }
        return s
    }

    /** Devuelve el nombre del PC si responde Unir PDF. */
    fun probar(direccion: String): String {
        val con = abrir("${normalizar(direccion)}/api/ping", 5_000)
        try {
            val json = leer(con)
            return json.optString("equipo").ifEmpty { "Unir PDF" }
        } finally {
            con.disconnect()
        }
    }

    /** Sube el PDF y devuelve el nombre con que se guardó en el PC. */
    fun subir(direccion: String, paciente: String, pdf: File): String {
        val limite = "----Escaner" + UUID.randomUUID().toString().replace("-", "")
        val con = abrir("${normalizar(direccion)}/api/subir", 120_000)
        try {
            con.requestMethod = "POST"
            con.doOutput = true
            con.setRequestProperty("Content-Type", "multipart/form-data; boundary=$limite")
            con.setChunkedStreamingMode(64 * 1024)
            DataOutputStream(con.outputStream.buffered()).use { out ->
                out.write("--$limite\r\n".toByteArray())
                out.write("Content-Disposition: form-data; name=\"paciente\"\r\n".toByteArray())
                out.write("Content-Type: text/plain; charset=UTF-8\r\n\r\n".toByteArray())
                out.write(paciente.toByteArray(Charsets.UTF_8))
                out.write("\r\n--$limite\r\n".toByteArray())
                val nombre = pdf.name
                val nombreAscii = nombre.replace(Regex("[^\\x20-\\x7E]"), "_").replace("\"", "_")
                val nombreUtf8 = URLEncoder.encode(nombre, "UTF-8").replace("+", "%20")
                out.write(
                    ("Content-Disposition: form-data; name=\"pdf\"; filename=\"$nombreAscii\"; " +
                        "filename*=UTF-8''$nombreUtf8\r\n").toByteArray()
                )
                out.write("Content-Type: application/pdf\r\n\r\n".toByteArray())
                pdf.inputStream().use { it.copyTo(out) }
                out.write("\r\n--$limite--\r\n".toByteArray())
            }
            val json = leer(con)
            return json.optString("archivo").ifEmpty { pdf.name }
        } finally {
            con.disconnect()
        }
    }

    private fun abrir(url: String, tiempoLectura: Int): HttpURLConnection {
        val con = URL(url).openConnection() as HttpURLConnection
        con.connectTimeout = 5_000
        con.readTimeout = tiempoLectura
        con.setRequestProperty("Accept", "application/json")
        return con
    }

    private fun leer(con: HttpURLConnection): JSONObject {
        val codigo = try {
            con.responseCode
        } catch (e: java.net.SocketTimeoutException) {
            throw ErrorServidor("El PC no responde. Revisa que Unir PDF esté abierto y que el teléfono esté en la misma red Wi-Fi.")
        } catch (e: java.io.IOException) {
            throw ErrorServidor("No se pudo conectar con el PC (${e.message}). Revisa la dirección, el Wi-Fi y que Unir PDF esté abierto.")
        }
        val cuerpo = (if (codigo in 200..299) con.inputStream else con.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        val json = try {
            JSONObject(cuerpo)
        } catch (e: Exception) {
            throw ErrorServidor("La dirección responde, pero no parece ser Unir PDF (código $codigo).")
        }
        if (codigo !in 200..299 || !json.optBoolean("ok")) {
            throw ErrorServidor(json.optString("error").ifEmpty { "Error del servidor (código $codigo)." })
        }
        return json
    }
}
