package cl.drazercode.escaner

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class EscanerViewModel(app: Application) : AndroidViewModel(app) {
    private val preferencias = app.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    /** Páginas en el orden en que irán en el PDF (copias JPEG privadas de la app). */
    val paginas = mutableStateListOf<File>()
    var paciente by mutableStateOf("")
    var servidor by mutableStateOf(preferencias.getString("servidor", "").orEmpty())
        private set
    var ocupado by mutableStateOf<String?>(null)
        private set
    var mensaje by mutableStateOf<String?>(null)

    /** Si no es null, el próximo escaneo reemplaza esa página (repetir foto). */
    private var reemplazar: Int? = null

    private val carpetaPaginas = File(app.filesDir, "paginas")
    private val carpetaSalida = File(app.cacheDir, "salida")

    init {
        // Al abrir la app no se conserva nada de pacientes anteriores.
        carpetaPaginas.deleteRecursively()
        carpetaSalida.deleteRecursively()
        carpetaPaginas.mkdirs()
    }

    fun prepararReemplazo(indice: Int?) {
        reemplazar = indice
    }

    fun recibirPaginas(uris: List<Uri>) {
        val indice = reemplazar
        reemplazar = null
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val copias = withContext(Dispatchers.IO) { uris.map { copiar(it) } }
            if (indice != null && indice in paginas.indices) {
                paginas[indice].delete()
                paginas[indice] = copias.first()
                copias.drop(1).forEach { it.delete() }
            } else {
                paginas.addAll(copias)
            }
        }
    }

    fun cancelarEscaneo() {
        reemplazar = null
    }

    fun mover(desde: Int, hacia: Int) {
        if (desde !in paginas.indices || hacia !in paginas.indices) return
        paginas.add(hacia, paginas.removeAt(desde))
    }

    fun quitar(indice: Int) {
        if (indice in paginas.indices) paginas.removeAt(indice).delete()
    }

    fun guardarServidor(direccion: String) {
        servidor = direccion.trim()
        preferencias.edit().putString("servidor", servidor).apply()
    }

    fun probarConexion(direccion: String) = tarea("Probando conexión…") {
        val equipo = Servidor.probar(direccion)
        "Conectado con Unir PDF en $equipo."
    }

    fun enviar() {
        if (!listoParaPdf()) return
        val nombre = paciente.trim()
        tarea("Enviando PDF al PC…") {
            val pdf = generarPdf(nombre)
            val guardado = Servidor.subir(servidor, nombre, pdf)
            pdf.delete()
            withContext(Dispatchers.Main) { nuevoPaciente() }
            "PDF enviado: $guardado"
        }
    }

    /** Genera el PDF y se lo entrega a [alTerminar] para compartirlo con otra app. */
    fun prepararParaCompartir(alTerminar: (File) -> Unit) {
        if (!listoParaPdf()) return
        val nombre = paciente.trim()
        tarea("Generando PDF…") {
            val pdf = generarPdf(nombre)
            withContext(Dispatchers.Main) { alTerminar(pdf) }
            null
        }
    }

    fun nuevoPaciente() {
        paginas.forEach { it.delete() }
        paginas.clear()
        paciente = ""
        carpetaSalida.deleteRecursively()
    }

    private fun listoParaPdf(): Boolean {
        when {
            paginas.isEmpty() -> mensaje = "Primero escanea al menos una página."
            paciente.isBlank() -> mensaje = "Escribe el nombre del paciente."
            else -> return true
        }
        return false
    }

    private fun generarPdf(nombre: String): File {
        carpetaSalida.deleteRecursively()
        val destino = File(carpetaSalida, nombreArchivo(nombre) + ".pdf")
        GeneradorPdf.generar(paginas.toList(), destino)
        return destino
    }

    private fun tarea(descripcion: String, trabajo: suspend () -> String?) {
        if (ocupado != null) return
        ocupado = descripcion
        viewModelScope.launch {
            mensaje = try {
                withContext(Dispatchers.IO) { trabajo() }
            } catch (e: Servidor.ErrorServidor) {
                e.message
            } catch (e: Exception) {
                "Error: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                ocupado = null
            }
        }
    }

    private fun copiar(uri: Uri): File {
        val destino = File(carpetaPaginas, UUID.randomUUID().toString() + ".jpg")
        getApplication<Application>().contentResolver.openInputStream(uri)!!.use { entrada ->
            destino.outputStream().use { entrada.copyTo(it) }
        }
        return destino
    }
}
