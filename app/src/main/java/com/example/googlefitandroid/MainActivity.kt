package com.example.googlefitandroid

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.googlefitandroid.databinding.ActivityMainBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.*
import com.google.android.gms.fitness.request.DataDeleteRequest
import com.google.android.gms.fitness.request.DataReadRequest
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var _binding: ActivityMainBinding
    private val binding get() = _binding

    private val TAG = "MainActivity"
    private val GOOGLE_FIT_PERMISSIONS_REQUEST_CODE = 1
    private val MY_PERMISSIONS_REQUEST_ACTIVITY_RECOGNITION = 2

    val fitnessOptions = FitnessOptions.builder()
        .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
        .addDataType(DataType.AGGREGATE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
        .addDataType(DataType.TYPE_STEP_COUNT_CUMULATIVE, FitnessOptions.ACCESS_WRITE)
        .addDataType(DataType.TYPE_STEP_COUNT_CUMULATIVE, FitnessOptions.ACCESS_READ)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        //setContentView(R.layout.activity_main)

        val account = GoogleSignIn.getAccountForExtension(this, fitnessOptions)

        if (!GoogleSignIn.hasPermissions(account, fitnessOptions)) {
            GoogleSignIn.requestPermissions(
                this, // your activity
                GOOGLE_FIT_PERMISSIONS_REQUEST_CODE, // e.g. 1
                account,
                fitnessOptions)
        } else {
            accessGoogleFit()
            binding.save.setOnClickListener { editCountSteps() }
            binding.delete.setOnClickListener { deleteCountSteps() }

        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        binding.save.setOnClickListener { editCountSteps() }
        binding.delete.setOnClickListener { deleteCountSteps() }

        when (resultCode) {
            Activity.RESULT_OK -> when (requestCode) {
                GOOGLE_FIT_PERMISSIONS_REQUEST_CODE -> accessGoogleFit()
                MY_PERMISSIONS_REQUEST_ACTIVITY_RECOGNITION -> accessGoogleFit()
                else -> {
                    // Result wasn't from Google Fit
                }
            }
            else -> {
                // Permission not granted
            }
        }
    }


    private fun accessGoogleFit() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
            != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
                MY_PERMISSIONS_REQUEST_ACTIVITY_RECOGNITION);
        }

        val end = LocalDateTime.now()
        val start = end.minusYears(1)
        val endSeconds = end.atZone(ZoneId.systemDefault()).toEpochSecond()
        val startSeconds = start.atZone(ZoneId.systemDefault()).toEpochSecond()

        val readRequest = DataReadRequest.Builder()
            .aggregate(DataType.AGGREGATE_STEP_COUNT_DELTA)
            .setTimeRange(startSeconds, endSeconds, TimeUnit.SECONDS)
            .bucketByTime(1, TimeUnit.DAYS)
            .build()
        val account = GoogleSignIn.getAccountForExtension(this, fitnessOptions)
        Fitness.getHistoryClient(this, account)
            .readData(readRequest)
            .addOnSuccessListener { response ->
                // Use response data here
                Fitness.getHistoryClient(this, GoogleSignIn.getAccountForExtension(this, fitnessOptions))
                    .readDailyTotal(DataType.TYPE_STEP_COUNT_DELTA)
                    .addOnSuccessListener { result ->
                        val totalSteps =
                            result.dataPoints.firstOrNull()?.getValue(Field.FIELD_STEPS)?.asInt() ?: 0
                        binding.labelSteps.text = "Count steps: $totalSteps"
                    }
                    .addOnFailureListener { e ->
                        Log.i(TAG, "There was a problem getting steps.", e)
                    }

                Log.i(TAG, "OnSuccess()")
            }
            .addOnFailureListener({ e -> Log.d(TAG, "OnFailure()", e) })
    }

    private fun editCountSteps() {
        val endTime = LocalDateTime.now().atZone(ZoneId.systemDefault())
        val startTime = endTime.minusSeconds(1)

        val dataSource = DataSource.Builder()
            .setAppPackageName(this)
            .setDataType(DataType.TYPE_STEP_COUNT_DELTA)
            .setStreamName("$TAG - step count")
            .setType(DataSource.TYPE_RAW)
            .build()

        val stepCountDelta = binding.steps.text.toString()

        if (stepCountDelta.isBlank()) {
            Toast.makeText(this, "The input field is empty", Toast.LENGTH_LONG).show()
            return
        }

        val dataPoint =
            DataPoint.builder(dataSource)
                .setField(Field.FIELD_STEPS, stepCountDelta.toInt())
                .setTimeInterval(startTime.toEpochSecond(), endTime.toEpochSecond(), TimeUnit.SECONDS)
                .build()

        val dataSet = DataSet.builder(dataSource)
            .add(dataPoint)
            .build()

        Fitness.getHistoryClient(this, GoogleSignIn.getAccountForExtension(this, fitnessOptions))
            .insertData(dataSet)
            .addOnSuccessListener {
                Toast.makeText(this, "DataSet added successfully!", Toast.LENGTH_SHORT).show()
                accessGoogleFit()
                Log.i(TAG, "DataSet added successfully!")
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "There was an error adding the DataSet!", Toast.LENGTH_SHORT).show()
                Log.w(TAG, "There was an error adding the DataSet", e)
            }
    }

    private fun deleteCountSteps() {
        val endTime = LocalDateTime.now().atZone(ZoneId.systemDefault())
        val startTime = endTime.minusMinutes(1)

        val request = DataDeleteRequest.Builder()
            .setTimeInterval(startTime.toEpochSecond(), endTime.toEpochSecond(), TimeUnit.SECONDS)
            .addDataType(DataType.TYPE_STEP_COUNT_DELTA)
            .build()

        Fitness.getHistoryClient(this, GoogleSignIn.getAccountForExtension(this, fitnessOptions))
            .deleteData(request)
            .addOnSuccessListener {
                Toast.makeText(this, "Data deleted successfully!", Toast.LENGTH_SHORT).show()
                accessGoogleFit()
                Log.i(TAG, "Data deleted successfully!")
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "There was an error with the deletion request!", Toast.LENGTH_LONG).show()
                Log.w(TAG, "There was an error with the deletion request", e)
            }
    }
}