package me.jbusdriver.ui.activity

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.collection.ArrayMap
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.gson.JsonObject
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.functions.BiFunction
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import me.jbusdriver.R
import me.jbusdriver.base.*
import me.jbusdriver.base.common.BaseActivity
import me.jbusdriver.base.common.C
import me.jbusdriver.common.KLog
import me.jbusdriver.http.GitHub
import me.jbusdriver.http.JAVBusService
import me.jbusdriver.ui.data.enums.DataSourceType
import org.jsoup.Jsoup

class SplashActivity : BaseActivity() {

    private var urls = arrayMapof<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        init()
    }

    private fun init() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED
            ) {
                startLoadUrls()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    REQUEST_STORAGE
                )
            }
        } else {
            startLoadUrls()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_STORAGE) {
            if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                toast("存储权限未授予，备份功能将不可用")
            }
            startLoadUrls()
        }
    }

    private fun startLoadUrls() {
        initUrls()
            .subscribeOn(Schedulers.io())
            .doOnError { e ->
                KLog.e("获取可用url错误 :$e")
                CacheLoader.acache.remove(C.Cache.BUS_URLS)
            }
            .retry(1)
            .doFinally {
                postMain {
                    toast("load url : ${JAVBusService.defaultFastUrl}")
                    MainActivity.start(this)
                    finish()
                }.addTo(rxManager)
            }
            .subscribeBy(onNext = { map ->
                KLog.w("init urls ok : $map")
                map[DataSourceType.CENSORED.key]?.let { url ->
                    JAVBusService.defaultFastUrl = url.urlHost
                }
            }, onError = { e ->
                e.printStackTrace()
                KLog.w("init urls error : $e")
            })
            .addTo(rxManager)
    }

    private fun initUrls(): Observable<ArrayMap<String, String>> {
        return if (CacheLoader.lru.get(C.Cache.BUS_URLS).isNullOrBlank()) {
            val urlsFromDisk = CacheLoader.justDisk(C.Cache.BUS_URLS).map {
                GSON.fromJson<ArrayMap<String, String>>(it)
            }
            val urlsFromUpdateCache =
                Flowable.concat(
                    CacheLoader.justLru(C.Cache.ANNOUNCE_URL),
                    GitHub.INSTANCE.announce().addUserCase()
                        .doOnError { e -> KLog.e("announce error", e) }
                        .doOnNext { value ->
                            KLog.i("announce ok", value)
                            CacheLoader.cacheDisk(C.Cache.ANNOUNCE_VALUE to value)
                        }
                )
                    .firstOrError().toFlowable()
                    .map { source ->
                        val r = GSON.fromJson<JsonObject>(source) ?: JsonObject()
                        CacheLoader.cacheLru(C.Cache.ANNOUNCE_VALUE to r)
                        arrayMapof<String, String>().apply {
                            val xyzLoader = r.getAsJsonObject("xyzLoader") ?: JsonObject()
                            JAVBusService.defaultXyzUrl =
                                xyzLoader.get("url")?.asString?.removeSuffix("/").orEmpty()
                            JAVBusService.xyzHostDomains.addAll(
                                xyzLoader.getAsJsonArray("legacyHost")?.map { it.asString }
                                    ?: emptyList())
                            val availableUrls = r.get("backUp")?.asJsonArray
                            availableUrls?.let { arr ->
                                arr.mapNotNull { it.asString }.shuffled().firstOrNull()?.let { url ->
                                    JAVBusService.defaultFastUrl = url
                                    urls[DataSourceType.CENSORED.key] = url
                                }
                            }
                            put(DataSourceType.CENSORED.key, availableUrls.toString())
                            KLog.d("init urls first :$source for $this")
                        }
                    }
                    .flatMap { mapResult ->
                        urls = mapResult
                        val mapFlow = GSON.fromJson<List<String>>(
                            mapResult[DataSourceType.CENSORED.key].orEmpty()
                        ).map { url ->
                            Flowable.combineLatest(
                                Flowable.just(url),
                                JAVBusService.INSTANCE.get(url).addUserCase(15).onErrorReturnItem(""),
                                BiFunction<String, String, Pair<String, String>> { t1, t2 -> t1 to (t2 ?: "") }
                            )
                        }
                        Flowable.mergeDelayError(mapFlow).filter { it.second.isNotBlank() }.take(1)
                    }
                    .firstOrError()
                    .doOnError { CacheLoader.acache.remove(C.Cache.ANNOUNCE_URL) }
                    .map { pair ->
                        val ds = DataSourceType.values().drop(1).toMutableList()
                        Jsoup.parse(pair.second).select(".navbar-nav a").forEach { box ->
                            ds.find { box.text() == it.key }?.let { type ->
                                ds.remove(type)
                                urls.put(type.key, box.attr("href").removeSuffix("/"))
                            }
                        }
                        urls[DataSourceType.XYZ.key]?.let { xyzUrl ->
                            urls[DataSourceType.XYZ_ACTRESSES.key] =
                                "$xyzUrl/${DataSourceType.XYZ_ACTRESSES.key.split("/").last()}"
                            urls.put(
                                DataSourceType.XYZ_GENRE.key,
                                "$xyzUrl/${DataSourceType.XYZ_GENRE.key.split("/").last()}"
                            )
                        }
                        urls[DataSourceType.CENSORED.key] = pair.first

                        if (JAVBusService.defaultXyzUrl.isNotBlank()) {
                            urls[DataSourceType.XYZ.key] = JAVBusService.defaultXyzUrl
                            urls[DataSourceType.XYZ_ACTRESSES.key] =
                                "${JAVBusService.defaultXyzUrl}/actresses"
                            urls[DataSourceType.XYZ_GENRE.key] =
                                "${JAVBusService.defaultXyzUrl}/genre"
                        } else {
                            val host = JAVBusService.xyzHostDomains.firstOrNull() ?: "work"
                            val baseUrlSuffix =
                                urls[DataSourceType.XYZ.key]?.substringAfterLast(".").orEmpty()
                            urls[DataSourceType.XYZ.key] =
                                urls[DataSourceType.XYZ.key]?.replace(baseUrlSuffix, host)
                            urls[DataSourceType.XYZ_ACTRESSES.key] =
                                urls[DataSourceType.XYZ_ACTRESSES.key]?.replace(baseUrlSuffix, host)
                            urls[DataSourceType.XYZ_GENRE.key] =
                                urls[DataSourceType.XYZ_GENRE.key]?.replace(baseUrlSuffix, host)
                        }

                        CacheLoader.cacheLruAndDisk(C.Cache.BUS_URLS to urls)
                        CacheLoader.lru.put(DataSourceType.CENSORED.key + "false", pair.second)
                        KLog.d("init urls second :$urls ")
                        urls
                    }.toFlowable()
            Flowable.concat(urlsFromDisk, urlsFromUpdateCache)
                .firstOrError().toObservable()
                .subscribeOn(Schedulers.io())
        } else CacheLoader.justLru(C.Cache.BUS_URLS).map {
            GSON.fromJson<ArrayMap<String, String>>(it)
        }.toObservable()
    }

    companion object {
        private const val REQUEST_STORAGE = 100
        fun start(context: Context) {
            context.startActivity(Intent(context, SplashActivity::class.java))
        }
    }
}
