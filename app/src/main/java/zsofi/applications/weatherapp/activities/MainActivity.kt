package zsofi.applications.weatherapp.activities

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.google.android.gms.location.*
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import zsofi.applications.weatherapp.R
import zsofi.applications.weatherapp.activities.models.WeatherResponse
import zsofi.applications.weatherapp.activities.network.WeatherService
import zsofi.applications.weatherapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private var binding : ActivityMainBinding? = null

    private lateinit var mFusedLocationClient : FusedLocationProviderClient
    private var customProgressDialog: Dialog? = null

    private val mLocationCallBack = object : LocationCallback(){
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location = locationResult.lastLocation
            val mLatitude = mLastLocation.latitude
            val mLongitude = mLastLocation.longitude
            Log.i("Current Latitude", "$mLatitude")
            Log.i("Current Longitude", "$mLongitude")
            getLocationWeatherDetails(mLatitude, mLongitude)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding?.root)

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (isLocationEnabled()){
            getCurrentLocation()
        }else{
            showRationalDialogForLocation()
        }
    }

    private fun getLocationWeatherDetails(latitude: Double, longitude: Double){
        if(Constants.isnNetworkAvailable(this)){

            val retrofit: Retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service : WeatherService = retrofit
                .create<WeatherService>(WeatherService::class.java)

            val listCall: Call<WeatherResponse> = service.getWeather(
                latitude, longitude, Constants.METRIC_UNIT, Constants.APP_ID
            )

            showProgressDialog(this)
            // This is where we start doing stuff in the background
            listCall.enqueue(object : Callback<WeatherResponse>{
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    if(response.isSuccessful){
                        val weatherList: WeatherResponse? = response.body()
                        Log.i("Response Result", "$weatherList")
                    }else{
                        val rc = response.code()
                        when(rc){
                            400 -> {
                                Log.e("Error 400", "Bad Connection")
                            }
                            404 -> {
                                Log.e("Error 404", "Not Found")
                            }
                            else -> {
                                Log.e("Error", "Generic Error")
                            }
                        }
                    }
                    cancelProgressDialog()
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    Log.e("Errorrrr", t.message.toString())
                    cancelProgressDialog()
                }

            })

        }else{
            Toast.makeText(this@MainActivity,
                "No internet connection is available",
                Toast.LENGTH_SHORT).show()
        }
    }

    private fun isLocationEnabled(): Boolean{
        // This provides access to the system location services
        val locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun showRationalDialogForLocation() {
        AlertDialog.Builder(this, R.style.myDialogTheme).setMessage(
            "It looks like you have turned off your location provider. Please turn it on."
        )
            .setPositiveButton("GO TO SETTINGS") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }

            }.setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun getCurrentLocation(){
        Dexter.withContext(this).withPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ).withListener(object: MultiplePermissionsListener {
            override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                if(report!!.areAllPermissionsGranted()){
                    requestNewLocationData()
                }else{
                    Toast.makeText(this@MainActivity,
                        "You have denied location permission. Please enable them, " +
                                "it is mandatory for the app to work",
                    Toast.LENGTH_LONG).show()
                }
            }
            override fun onPermissionRationaleShouldBeShown(
                permissions: MutableList<PermissionRequest>?,
                token: PermissionToken?
            ) {
                showRationalDialogForPermissions()
            }
        }).onSameThread().check()
    }

    @SuppressLint("MissingPermission")
    private fun requestNewLocationData(){
        val mLocationRequest = LocationRequest.create().apply {
            interval = 100
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            numUpdates = 1
        }
        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest,
            mLocationCallBack,
            Looper.myLooper()!!)
    }

    private fun showRationalDialogForPermissions(){
        AlertDialog.Builder(this, R.style.myDialogTheme).setMessage("It looks like you have turned off the permission" +
                " required for this feature. It can be enabled under the Applications Settings")
            .setPositiveButton("GO TO SETTINGS"){
                    _, _ ->
                try{
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch(e: ActivityNotFoundException){
                    e.printStackTrace()
                }

            }.setNegativeButton("Cancel"){dialog, _ ->
                dialog.dismiss()
            }.show()
    }

    // Function to show Progress dialog
    fun showProgressDialog(context: Context){
        customProgressDialog = Dialog(context)
        customProgressDialog?.setContentView(R.layout.dialog_custom_progress)
        customProgressDialog?.setCanceledOnTouchOutside(false)
        customProgressDialog?.show()
    }
    // Function to dismiss Progress dialog
    fun cancelProgressDialog(){
        if(customProgressDialog != null){
            customProgressDialog?.dismiss()
            customProgressDialog = null
        }
    }

    override fun onDestroy() {
        if (binding != null){
            binding = null
        }
        super.onDestroy()
    }
}