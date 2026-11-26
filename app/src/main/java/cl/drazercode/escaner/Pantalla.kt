package cl.drazercode.escaner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val Colores = lightColorScheme(
    primary = Color(0xFF1565C0),
    secondary = Color(0xFF00796B),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaPrincipal(
    vm: EscanerViewModel,
    alEscanear: () -> Unit,
    alRepetir: (Int) -> Unit,
    alCompartir: () -> Unit,
) {
    MaterialTheme(colorScheme = Colores) {
        val avisos = remember { SnackbarHostState() }
        var verAjustes by rememberSaveable { mutableStateOf(vm.servidor.isBlank()) }
        var confirmarNuevo by remember { mutableStateOf(false) }

        LaunchedEffect(vm.mensaje) {
            vm.mensaje?.let {
                avisos.showSnackbar(it, withDismissAction = true, duration = SnackbarDuration.Long)
                vm.mensaje = null
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Escáner Policlínico") },
                    actions = {
                        IconButton(onClick = { verAjustes = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Ajustes del PC")
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(avisos) },
        ) { relleno ->
            Column(
                Modifier
                    .padding(relleno)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                OutlinedTextField(
                    value = vm.paciente,
                    onValueChange = { vm.paciente = it },
                    label = { Text("Nombre del paciente") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when (vm.paginas.size) {
                            0 -> "Sin páginas"
                            1 -> "1 página"
                            else -> "${vm.paginas.size} páginas"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (vm.paginas.isNotEmpty() || vm.paciente.isNotBlank()) {
                        TextButton(onClick = { confirmarNuevo = true }) { Text("Nuevo paciente") }
                    }
                }

                Box(Modifier.weight(1f)) {
                    if (vm.paginas.isEmpty()) {
                        Text(
                            "Escribe el nombre del paciente y pulsa «Escanear». " +
                                "El escáner recorta y endereza cada hoja; ahí mismo puedes girar, " +
                                "aplicar filtros para resaltar el texto y repetir fotos.",
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            itemsIndexed(vm.paginas, key = { _, f -> f.name }) { i, archivo ->
                                FilaPagina(
                                    numero = i + 1,
                                    archivo = archivo,
                                    esPrimera = i == 0,
                                    esUltima = i == vm.paginas.lastIndex,
                                    alSubir = { vm.mover(i, i - 1) },
                                    alBajar = { vm.mover(i, i + 1) },
                                    alRepetir = { alRepetir(i) },
                                    alQuitar = { vm.quitar(i) },
                                )
                            }
                        }
                    }
                }

                Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = alEscanear, modifier = Modifier.fillMaxWidth(), enabled = vm.ocupado == null) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (vm.paginas.isEmpty()) "Escanear" else "Agregar páginas")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { vm.enviar() },
                            enabled = vm.ocupado == null && vm.paginas.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Enviar al PC")
                        }
                        OutlinedButton(
                            onClick = alCompartir,
                            enabled = vm.ocupado == null && vm.paginas.isNotEmpty(),
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Compartir PDF")
                        }
                    }
                }
            }
        }

        vm.ocupado?.let { texto ->
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                title = { Text(texto) },
                text = {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                },
            )
        }

        if (confirmarNuevo) {
            AlertDialog(
                onDismissRequest = { confirmarNuevo = false },
                title = { Text("¿Empezar con otro paciente?") },
                text = { Text("Se borrarán las páginas escaneadas y el nombre escrito.") },
                confirmButton = {
                    TextButton(onClick = { vm.nuevoPaciente(); confirmarNuevo = false }) { Text("Borrar") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmarNuevo = false }) { Text("Cancelar") }
                },
            )
        }

        if (verAjustes && vm.ocupado == null) {
            DialogoAjustes(
                actual = vm.servidor,
                alProbar = { vm.guardarServidor(it); vm.probarConexion(it) },
                alGuardar = { vm.guardarServidor(it); verAjustes = false },
                alCerrar = { verAjustes = false },
            )
        }
    }
}

@Composable
private fun FilaPagina(
    numero: Int,
    archivo: File,
    esPrimera: Boolean,
    esUltima: Boolean,
    alSubir: () -> Unit,
    alBajar: () -> Unit,
    alRepetir: () -> Unit,
    alQuitar: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Miniatura(archivo, Modifier.size(72.dp, 96.dp))
            Spacer(Modifier.width(12.dp))
            Text("Página $numero", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = alSubir, enabled = !esPrimera) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Subir")
            }
            IconButton(onClick = alBajar, enabled = !esUltima) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Bajar")
            }
            IconButton(onClick = alRepetir) {
                Icon(Icons.Default.Refresh, contentDescription = "Repetir foto")
            }
            IconButton(onClick = alQuitar) {
                Icon(Icons.Default.Delete, contentDescription = "Quitar")
            }
        }
    }
}

@Composable
private fun Miniatura(archivo: File, modifier: Modifier) {
    val imagen by produceState<Bitmap?>(null, archivo) {
        value = withContext(Dispatchers.IO) {
            val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(archivo.path, limites)
            var muestreo = 1
            while (limites.outHeight / (muestreo * 2) >= 300) muestreo *= 2
            BitmapFactory.decodeFile(archivo.path, BitmapFactory.Options().apply { inSampleSize = muestreo })
        }
    }
    Box(modifier.background(Color(0xFFE0E0E0)), contentAlignment = Alignment.Center) {
        imagen?.let { Image(it.asImageBitmap(), contentDescription = null) }
    }
}

@Composable
private fun DialogoAjustes(
    actual: String,
    alProbar: (String) -> Unit,
    alGuardar: (String) -> Unit,
    alCerrar: () -> Unit,
) {
    var direccion by rememberSaveable { mutableStateOf(actual) }
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("PC con Unir PDF") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Escribe la dirección que muestra Unir PDF en el PC, por ejemplo 192.168.1.50:8080. " +
                        "El teléfono debe estar conectado al mismo Wi-Fi del hospital."
                )
                OutlinedTextField(
                    value = direccion,
                    onValueChange = { direccion = it },
                    label = { Text("Dirección (IP:puerto)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { alProbar(direccion) }, enabled = direccion.isNotBlank()) {
                    Text("Probar conexión")
                }
            }
        },
        confirmButton = { TextButton(onClick = { alGuardar(direccion) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cerrar") } },
    )
}
