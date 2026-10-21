package com.calnyx.app

import android.app.Application
import com.calnyx.app.data.AppDatabase

class CalNyxApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
}
