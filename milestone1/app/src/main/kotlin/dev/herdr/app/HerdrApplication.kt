package dev.herdr.app

import android.app.Application
import androidx.room.Room
import dev.herdr.core.data.BoardDatabase
import dev.herdr.core.data.BoardRepository
import dev.herdr.core.data.SettingsStore
import dev.herdr.core.ssh.SshConnector
import java.io.File

class AppContainer(app: Application) {
    val settings = SettingsStore(app)
    val database: BoardDatabase = Room.databaseBuilder(app, BoardDatabase::class.java, "herdr.db")
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
    val repository = BoardRepository(database.boardDao())
    val connector = SshConnector(File(app.filesDir, "trust/known_hosts.spike"))
}

class HerdrApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
