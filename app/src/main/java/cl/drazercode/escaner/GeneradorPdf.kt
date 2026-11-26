package cl.drazercode.escaner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream

/**
 * Arma un PDF con una página A4 por foto, igual que Unir PDF: vertical u horizontal según la foto,
 * imagen centrada y ajustada a la hoja. Las fotos se guardan como JPEG dentro del PDF, así el archivo
 * queda liviano (unos cientos de KB por página).
 */
object GeneradorPdf {
    private const val A4_ANCHO = 595.28f // puntos (1/72 de pulgada)
    private const val A4_ALTO = 841.89f
    private const val LADO_MAXIMO = 2480 // px; ~300 ppp en una hoja A4
    private const val CALIDAD_JPEG = 85

    private class Imagen(val ancho: Int, val alto: Int, val jpeg: ByteArray)

    fun generar(paginas: List<File>, destino: File) {
        require(paginas.isNotEmpty()) { "No hay páginas" }
        destino.parentFile?.mkdirs()
        destino.outputStream().buffered().use { salida ->
            val pdf = EscritorPdf(salida)
            val n = paginas.size
            // Objetos: 1 catálogo, 2 árbol de páginas, luego por página: hoja, contenido, imagen.
            pdf.objeto(1, "<< /Type /Catalog /Pages 2 0 R >>")
            val hijos = (0 until n).joinToString(" ") { "${3 + it * 3} 0 R" }
            pdf.objeto(2, "<< /Type /Pages /Kids [$hijos] /Count $n >>")
            paginas.forEachIndexed { i, archivo ->
                val img = cargar(archivo)
                val horizontal = img.ancho > img.alto
                val anchoHoja = if (horizontal) A4_ALTO else A4_ANCHO
                val altoHoja = if (horizontal) A4_ANCHO else A4_ALTO
                val escala = minOf(anchoHoja / img.ancho, altoHoja / img.alto)
                val w = img.ancho * escala
                val h = img.alto * escala
                val x = (anchoHoja - w) / 2
                val y = (altoHoja - h) / 2
                val hoja = 3 + i * 3
                val contenido = "q ${f(w)} 0 0 ${f(h)} ${f(x)} ${f(y)} cm /Im0 Do Q".toByteArray()
                pdf.objeto(
                    hoja,
                    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${f(anchoHoja)} ${f(altoHoja)}] " +
                        "/Resources << /XObject << /Im0 ${hoja + 2} 0 R >> >> /Contents ${hoja + 1} 0 R >>"
                )
                pdf.flujo(hoja + 1, "<< /Length ${contenido.size} >>", contenido)
                pdf.flujo(
                    hoja + 2,
                    "<< /Type /XObject /Subtype /Image /Width ${img.ancho} /Height ${img.alto} " +
                        "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${img.jpeg.size} >>",
                    img.jpeg
                )
            }
            pdf.terminar(raiz = 1)
        }
    }

    private fun cargar(archivo: File): Imagen {
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(archivo.path, limites)
        var muestreo = 1
        while (maxOf(limites.outWidth, limites.outHeight) / (muestreo * 2) >= LADO_MAXIMO) muestreo *= 2
        val opciones = BitmapFactory.Options().apply { inSampleSize = muestreo }
        var bmp = BitmapFactory.decodeFile(archivo.path, opciones)
            ?: error("No se pudo leer la imagen ${archivo.name}")
        val lado = maxOf(bmp.width, bmp.height)
        if (lado > LADO_MAXIMO) {
            val k = LADO_MAXIMO.toFloat() / lado
            val reducido = Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt(), (bmp.height * k).toInt(), true)
            if (reducido !== bmp) bmp.recycle()
            bmp = reducido
        }
        val bytes = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, CALIDAD_JPEG, bytes)
        val img = Imagen(bmp.width, bmp.height, bytes.toByteArray())
        bmp.recycle()
        return img
    }

    private fun f(v: Float) = String.format(java.util.Locale.US, "%.2f", v)

    /** Escritor PDF mínimo: objetos numerados, tabla xref y trailer. */
    private class EscritorPdf(private val salida: OutputStream) {
        private var posicion = 0L
        private val desplazamientos = sortedMapOf<Int, Long>()

        init {
            escribir("%PDF-1.4\n%âãÏÓ\n".toByteArray(Charsets.ISO_8859_1))
        }

        fun objeto(num: Int, cuerpo: String) {
            desplazamientos[num] = posicion
            escribir("$num 0 obj\n$cuerpo\nendobj\n")
        }

        fun flujo(num: Int, dic: String, datos: ByteArray) {
            desplazamientos[num] = posicion
            escribir("$num 0 obj\n$dic\nstream\n")
            escribir(datos)
            escribir("\nendstream\nendobj\n")
        }

        fun terminar(raiz: Int) {
            val inicioXref = posicion
            val total = desplazamientos.lastKey() + 1
            val sb = StringBuilder("xref\n0 $total\n0000000000 65535 f \n")
            for (i in 1 until total) {
                sb.append(String.format(java.util.Locale.US, "%010d 00000 n \n", desplazamientos.getValue(i)))
            }
            sb.append("trailer\n<< /Size $total /Root $raiz 0 R >>\nstartxref\n$inicioXref\n%%EOF\n")
            escribir(sb.toString())
        }

        private fun escribir(s: String) = escribir(s.toByteArray(Charsets.ISO_8859_1))
        private fun escribir(b: ByteArray) {
            salida.write(b)
            posicion += b.size
        }
    }
}
