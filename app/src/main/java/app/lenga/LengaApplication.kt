package app.lenga

import android.app.Application
import app.lenga.service.BlockingState

class LengaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        BlockingState.init(this)
    }
}
