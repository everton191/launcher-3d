package br.com.ne3d.spbshellmodern

import androidx.test.platform.app.InstrumentationRegistry
import br.com.ne3d.spbshellmodern.data.WidgetRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class WidgetRepositoryTest {
    @Test fun photosAndWeatherUseCurrentAndroidDataSources() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = WidgetRepository(context)
        // A coleção pode estar vazia ou limitada pelo seletor de fotos do Android 13.
        assertNotNull(repository.loadRecentPhotos())
        val weather = repository.loadWeather("Fortaleza")
        assertNotNull("Localização e clima atual devem estar disponíveis", weather)
        assertTrue(weather!!.temperature in -90..60)
        assertTrue(weather.description.isNotBlank())
    }
}
