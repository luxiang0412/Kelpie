package dev.herdr.app

import android.app.Application
import androidx.room.Room
import dev.herdr.core.data.BoardDatabase
import dev.herdr.core.data.BoardRepository
import dev.herdr.core.data.HostRepository
import dev.herdr.core.ssh.SshConnector
import java.io.File

class AppContainer(app: Application) {
    val hosts = HostRepository(app)
    val database: BoardDatabase = Room.databaseBuilder(app, BoardDatabase::class.java, "herdr.db")
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
    val boards = BoardRepository(database.boardDao())
    val transcripts = dev.herdr.core.data.TranscriptStore(database.transcriptDao())

    /** 每台主机独立信任库（指纹互不影响）。 */
    fun connectorFor(hostId: String): SshConnector =
        SshConnector(File(app.filesDir, "trust/$hostId.known_hosts"))

    private val app: Application = app
}

class HerdrApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
