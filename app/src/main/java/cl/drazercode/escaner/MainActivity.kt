package cl.drazercode.escaner

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.FileProvider
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.io.File

class MainActivity : ComponentActivity() {
    private val vm: EscanerViewModel by viewModels()

    private val lanzador = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { resultado ->
        val datos = GmsDocumentScanningResult.fromActivityResultIntent(resultado.data)
        if (resultado.resultCode == RESULT_OK && datos != null) {
            vm.recibirPaginas(datos.pages.orEmpty().map { it.imageUri })
        } else {
            vm.cancelarEscaneo()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PantallaPrincipal(
                vm = vm,
                alEscanear = { escanear(reemplazar = null) },
                alRepetir = { indice -> escanear(reemplazar = indice) },
                alCompartir = { vm.prepararParaCompartir(::compartir) },
            )
        }
    }

    /**
     * Abre el escáner de Google: detecta los bordes, recorta y endereza la hoja; permite girar,
     * aplicar filtros (blanco y negro / mejorar texto), repetir y borrar fotos antes de aceptar.
     */
    private fun escanear(reemplazar: Int?) {
        vm.prepararReemplazo(reemplazar)
        val opciones = GmsDocumentScannerOptions.Builder()
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setGalleryImportAllowed(true)
            .apply { if (reemplazar != null) setPageLimit(1) }
            .build()
        GmsDocumentScanning.getClient(opciones)
            .getStartScanIntent(this)
            .addOnSuccessListener { lanzador.launch(IntentSenderRequest.Builder(it).build()) }
            .addOnFailureListener {
                vm.cancelarEscaneo()
                vm.mensaje = "No se pudo abrir el escáner: ${it.message}. " +
                    "Revisa que Google Play Services esté actualizado."
            }
    }

    /** Respaldo: compartir el PDF con otra app (correo, Drive, etc.). */
    private fun compartir(pdf: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.archivos", pdf)
        val envio = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, pdf.nameWithoutExtension)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(envio, "Compartir PDF"))
    }
}
