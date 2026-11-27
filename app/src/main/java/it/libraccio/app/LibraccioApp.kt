package it.libraccio.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import it.libraccio.app.data.LibraryDatabase
import it.libraccio.app.data.LibraryRepository
import it.libraccio.app.data.SettingsStore
import it.libraccio.app.sound.SoundFx

class LibraccioApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(context: Context) {
    private val db = Room.databaseBuilder(context, LibraryDatabase::class.java, "libraccio.db").build()
    val settings = SettingsStore(context)
    val repository = LibraryRepository(context, db.dao())
    val sounds = SoundFx { settings.state.value.sounds }
}
