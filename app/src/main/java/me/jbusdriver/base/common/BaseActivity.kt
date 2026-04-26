package me.jbusdriver.base.common

import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import io.reactivex.rxjava3.disposables.CompositeDisposable

abstract class BaseActivity : AppCompatActivity() {
    protected val rxManager by lazy { CompositeDisposable() }
    protected val TAG: String by lazy { this::class.java.simpleName }
    private var destroyed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate $savedInstanceState")
        super.onCreate(savedInstanceState)
    }

    override fun onDestroy() {
        super.onDestroy()
        rxManager.clear()
        rxManager.dispose()
        Log.d(TAG, "onDestroy")
        destroyed = true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    val isDestroyedCompatible: Boolean
        get() {
            return if (Build.VERSION.SDK_INT >= 17)
                isDestroyedCompatible17
            else
                destroyed || super.isFinishing()
        }

    private val isDestroyedCompatible17: Boolean
        @TargetApi(17)
        get() = super.isDestroyed()

    val viewContext: Context by lazy { this }
}