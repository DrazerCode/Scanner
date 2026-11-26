package cl.drazercode.escaner

/** Limpia el nombre del paciente para usarlo como nombre de archivo en Windows (igual que Unir PDF). */
fun nombreArchivo(paciente: String): String {
    val limpio = paciente
        .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .trimEnd('.', ' ')
        .take(80)
        .trim()
    return limpio.ifEmpty { "Escaneo" }
}
