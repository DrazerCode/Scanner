package cl.drazercode.escaner

import org.junit.Assert.assertEquals
import org.junit.Test

class PruebasTest {
    @Test
    fun limpiaNombreDelPaciente() {
        assertEquals("Martin Paves Ibaceta", nombreArchivo("  Martin   Paves Ibaceta "))
        assertEquals("Juan Pérez", nombreArchivo("Juan/Pérez"))
        assertEquals("Ana", nombreArchivo("Ana..."))
        assertEquals("Escaneo", nombreArchivo("???"))
        assertEquals(80, nombreArchivo("a".repeat(200)).length)
    }

    @Test
    fun normalizaDireccionDelPc() {
        assertEquals("http://192.168.1.50:8080", Servidor.normalizar("192.168.1.50"))
        assertEquals("http://192.168.1.50:8081", Servidor.normalizar(" 192.168.1.50:8081/ "))
        assertEquals("http://pc-oftalmo:8080", Servidor.normalizar("http://pc-oftalmo"))
    }
}
