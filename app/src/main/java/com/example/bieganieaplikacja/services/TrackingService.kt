package com.example.bieganieaplikacja.services

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_LOW
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.MutableLiveData
import com.example.bieganieaplikacja.*
import com.example.bieganieaplikacja.R
import com.example.bieganieaplikacja.other.Constants.ACTION_PAUSE_SERVICE
import com.example.bieganieaplikacja.other.Constants.ACTION_START_OR_RESUME_SERVICE
import com.example.bieganieaplikacja.other.Constants.ACTION_STOP_SERVICE
import com.example.bieganieaplikacja.other.Constants.FASTEST_LOCATION_INTERVAL
import com.example.bieganieaplikacja.other.Constants.LOCATION_UPDATE_INTERVAL
import com.example.bieganieaplikacja.other.Constants.NOTIFICATION_CHANNEL_ID
import com.example.bieganieaplikacja.other.Constants.NOTIFICATION_CHANNEL_NAME
import com.example.bieganieaplikacja.other.Constants.NOTIFICATION_ID
import com.example.bieganieaplikacja.other.Constants.TIMER_UPDATE_INTERVAL
import com.example.bieganieaplikacja.other.Constants.flags
import com.example.bieganieaplikacja.other.TrackingUtility
import com.google.android.gms.location.*
import com.google.android.gms.location.LocationServices.getFusedLocationProviderClient
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

typealias Polyline = MutableList<LatLng>
typealias Polylines = MutableList<Polyline>

@AndroidEntryPoint
class TrackingService : LifecycleService() {
    var isFirstRun = true
    var serviceKilled = false

    @Inject
    lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    private val timeRunInSeconds = MutableLiveData<Long>()

    @Inject
    lateinit var baseNotificationBuilder: NotificationCompat.Builder

    lateinit var curNotificationBuilder: NotificationCompat.Builder

    companion object {
        val timeRunInMillis = MutableLiveData<Long>()
        val isTracking = MutableLiveData<Boolean>()
        val pathPoints = MutableLiveData<Polylines>()
    }

    private fun postInitialValues() {
        isTracking.postValue(false)
        pathPoints.postValue(mutableListOf())
        timeRunInSeconds.postValue(0L)
        timeRunInMillis.postValue(0L)
    }

    override fun onCreate() {
        super.onCreate()
        curNotificationBuilder = baseNotificationBuilder
        postInitialValues()
        fusedLocationProviderClient = getFusedLocationProviderClient(this)

        isTracking.observe(this) {
            updateLocationTracking(it)
            updateNotificationTrackingState(it)
        }
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                ACTION_START_OR_RESUME_SERVICE -> {
                    if (isFirstRun) {
                        startForegroundService()
                        isFirstRun = false
                        Timber.d("1.service start")
                    } else {
                        Timber.d(" Resumed service")
                        startTimer()
                    }

                }
                ACTION_PAUSE_SERVICE -> {
                     Timber.d("Paused services")
                    pauseService()
                }
                ACTION_STOP_SERVICE -> {
                    Timber.d("Stop services")
                    killService()
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private var isTimerEnabled = false
    private var lapTime = 0L
    private var timeRun = 0L
    private var timeStarted = 0L
    private var lastSecondTimeStamp = 0L

    private fun startTimer() {
        addEmptyPolyline()
        isTracking.postValue(true)
        timeStarted = System.currentTimeMillis()
        isTimerEnabled = true
        CoroutineScope(Dispatchers.Main).launch {
            while (isTracking.value!!) {
                lapTime = System.currentTimeMillis() - timeStarted
                timeRunInMillis.postValue(timeRun + lapTime)

                if (timeRunInMillis.value!! >= lastSecondTimeStamp + 2000L) {
                    timeRunInSeconds.postValue(timeRunInSeconds.value!! + 2)
                    lastSecondTimeStamp += 2000L
                }
                delay(TIMER_UPDATE_INTERVAL)
            }
            timeRun += lapTime
        }
    }

    private fun pauseService() {
        isTracking.postValue(false)
        isTimerEnabled = false
    }

    private fun updateNotificationTrackingState(isTracking: Boolean) {
        val notificationActionText = if (isTracking) "Pause" else "Resume"
        val pendingIntent = if (isTracking) {
            val pauseIntent = Intent(this, TrackingService::class.java).apply {
                action = ACTION_PAUSE_SERVICE
            }
            PendingIntent.getService(
                this, 1, pauseIntent, flags)
        } else {
            val resumeIntent = Intent(this, TrackingService::class.java).apply {
                action = ACTION_START_OR_RESUME_SERVICE
            }
            PendingIntent.getService(this, 2, resumeIntent, flags)
        }

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        curNotificationBuilder.javaClass.getDeclaredField("mActions").apply {
            isAccessible = true
            set(curNotificationBuilder, ArrayList<NotificationCompat.Action>())
        }

        if (!serviceKilled) {
            curNotificationBuilder = baseNotificationBuilder
                .addAction(
                    R.drawable.ic_pause_black_24dp, notificationActionText, pendingIntent
                )
            notificationManager.notify(NOTIFICATION_ID, curNotificationBuilder.build())
        }
    }

    private fun killService() {
        serviceKilled = true
        isFirstRun = true
        pauseService()
        postInitialValues()
        stopForeground(true)
        stopSelf()

        @SuppressLint("MissingPermission")
        private fun updateLocationTracking(isTracking: Boolean) {
            if (isTracking) {
                if (TrackingUtility.hasLocationPermission(this)) {
                    val locationRequest = LocationRequest.Builder(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        LOCATION_UPDATE_INTERVAL
                    )
                        .setWaitForAccurateLocation(false)
                        .setMinUpdateIntervalMillis(LOCATION_UPDATE_INTERVAL)
                        .setMaxUpdateDelayMillis(FASTEST_LOCATION_INTERVAL)
                        .build()

                    fusedLocationProviderClient.requestLocationUpdates(
                        locationRequest,
                        locationCallBack,
                        Looper.getMainLooper()
                    )
                } else {
                    fusedLocationProviderClient.removeLocationUpdates(
                        locationCallback
                    )
                }
            }
        }

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(p0: LocationResult) {
                super.onLocationResult(p0)
                if (isTracking.value!!) {
                    p0.locations.let {
                        for (location in it) {
                            addPathPoint(location)
                            Timber.d("New Location: ${location.longitude} , ${location.latitude}")
                        }
                    }
                }
            }
        }

     fun addPathPoint(location: Location?) {
        location?.let {
            val pos = LatLng(location.latitude, location.longitude)
            pathPoints.value?.apply {
                last().add(pos)
                pathPoints.postValue(this)
            }
        }
    }

        fun addEmptyPolyline() = pathPoints.value?.apply {
            add(mutableListOf())
            pathPoints.postValue(this)
        } ?: pathPoints.postValue(mutableListOf(mutableListOf()))

         fun startForegroundService() {
            startTimer()
            isTracking.postValue(true)
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                createNotificationChannel(notificationManager)
            }

            Timber.d("2. service start")
            startForeground(NOTIFICATION_ID, baseNotificationBuilder.build())

            timeRunInSeconds.observe(this) {
                if (!serviceKilled) {
                    val notification =
                        curNotificationBuilder.setContentText(
                            TrackingUtility.getFormattedStopWatchTime(it * 1000L)
                        )
                    notificationManager.notify(NOTIFICATION_ID, notification.build())
                }
            }
        }


        @RequiresApi(Build.VERSION_CODES.O)
         fun createNotificationChannel(notificationManager: NotificationManager) {
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_NAME, IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }
    }}




