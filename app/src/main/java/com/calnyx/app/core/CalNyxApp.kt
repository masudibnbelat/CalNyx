package com.calnyx.app.core

import android.app.Application
import com.calnyx.app.data.AppDatabase

class CalNyxApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
}
