package app.dizzify

import android.app.Application
import app.dizzify.data.AppModel
import app.dizzify.data.repository.AppRepository
import app.dizzify.settings.LauncherSettings
import app.dizzify.settings.LauncherState
import io.github.mlmgames.settings.core.SettingsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import app.dizzify.test.util.MainDispatcherRule

class LauncherViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val application = mockk<Application> {
        every { applicationContext } returns mockk()
    }

    private val settingsRepo = mockk<SettingsRepository<LauncherSettings>>()
    private val stateRepo = mockk<SettingsRepository<LauncherState>>()
    private val appRepository = mockk<AppRepository>(relaxed = true)

    private val settingsFlow = MutableStateFlow(LauncherSettings())
    private val stateFlow = MutableStateFlow(LauncherState())

    private val appListFlow = MutableStateFlow<List<AppModel>>(emptyList())
    private val appListAllFlow = MutableStateFlow<List<AppModel>>(emptyList())
    private val hiddenAppsFlow = MutableStateFlow<List<AppModel>>(emptyList())

    private fun createViewModel(): LauncherViewModel {
        every { settingsRepo.flow } returns settingsFlow
        every { stateRepo.flow } returns stateFlow

        every { appRepository.appList } returns appListFlow
        every { appRepository.appListAll } returns appListAllFlow
        every { appRepository.hiddenApps } returns hiddenAppsFlow

        return LauncherViewModel(application, settingsRepo, stateRepo, appRepository)
    }

    @Test
    fun `initial state is correct`() = runTest {
        val viewModel = createViewModel()
        assertEquals(false, viewModel.ui.value.isLoading) // Init coroutine runs quickly on test dispatcher
        assertEquals("", viewModel.ui.value.query)
    }

    @Test
    fun `search query updates ui state`() = runTest {
        val viewModel = createViewModel()
        viewModel.setQuery("test")
        assertEquals("test", viewModel.ui.value.query)
    }
}
