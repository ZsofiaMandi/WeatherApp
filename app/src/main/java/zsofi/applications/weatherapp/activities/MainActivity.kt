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
import android.os.Build
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
import zsofi.applications.weatherapp.activities.utils.Constants
import zsofi.applications.weatherapp.activities.utils.GetAddressFromLatLng
import zsofi.applications.weatherapp.databinding.ActivityMainBinding
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

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
            val addressTask = GetAddressFromLatLng(
                this@MainActivity, mLatitude, mLongitude)
            addressTask.setAddressListener(object: GetAddressFromLatLng.AddressListener{
                override fun onAddressFound(address: String?){
                    binding?.tvName?.text = address
                }
                override fun onError(){
                    Log.e("Get Address:: ", "Something went wrong")
                }
            })
            addressTask.getAddress()
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
                .create(WeatherService::class.java)

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
                        val weatherList: WeatherResponse = response.body()!!
                        Log.i("Response Result", "$weatherList")
                        setupUI(weatherList)
                    }else{
                        when(response.code()){
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
    private fun showProgressDialog(context: Context){
        customProgressDialog = Dialog(context)
        customProgressDialog?.setContentView(R.layout.dialog_custom_progress)
        customProgressDialog?.setCanceledOnTouchOutside(false)
        customProgressDialog?.show()
    }
    // Function to dismiss Progress dialog
    private fun cancelProgressDialog(){
        if(customProgressDialog != null){
            customProgressDialog?.dismiss()
            customProgressDialog = null
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setupUI(weatherList: WeatherResponse){
        for(i in weatherList.weather.indices){
            Log.i("Weather Name", weatherList.weather.toString())
            binding?.tvMain?.text = weatherList.weather[i].main
            binding?.tvMainDescription?.text = weatherList.weather[i].description
            when (weatherList.weather[i].icon) {
                "01d" -> binding?.ivMain?.setImageResource(R.drawable.sunny)
                "02d" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                "03d" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                "04d" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                "04n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                "10d" -> binding?.ivMain?.setImageResource(R.drawable.rain)
                "11d" -> binding?.ivMain?.setImageResource(R.drawable.storm)
                "01n" -> binding?.ivMain?.setImageResource(R.drawable.snowflake)
                "02n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                "03n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                "10n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                "11n" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
                "13n" -> binding?.ivMain?.setImageResource(R.drawable.rain)
                "50d" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
            }
        }
        var unit: String
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            unit = getUnit(application.resources.configuration.locales[0].country.toString())
        }else{
            unit = getUnit(application.resources.configuration.locale.country.toString())
        }
        val temp = weatherList.main.temp.toString().substring(0,4)
        binding?.tvTemp?.text = "$temp $unit"

        binding?.tvSunriseTime?.text = unixTime(weatherList.sys.sunrise)
        binding?.tvSunsetTime?.text = unixTime(weatherList.sys.sunset)

        val minTemp = weatherList.main.temp_min.toString().substring(0,4)
        val maxTemp = weatherList.main.temp_max.toString().substring(0,4)

        binding?.tvMin?.text = "min $minTemp $unit "
        binding?.tvMax?.text = "max $maxTemp $unit "

        binding?.tvHumidity?.text = "${weatherList.main.humidity}%"

        val speed = (weatherList.wind.speed * 3.6)
        val roundedSpeed = (speed * 100.0).roundToInt() / 100.0
        binding?.tvSpeed?.text = roundedSpeed.toString()

        val locale = Locale("", weatherList.sys.country)
        binding?.tvCountry?.text = locale.displayCountry

    }

    private fun getUnit(value: String) : String{
        var tempValue = "°C"
        if ("US" == value || "LR" == value || "MM" == value){
            tempValue = "°F"
        }
        return tempValue
    }
    private fun unixTime(timex: Long) : String?{
        val date = Date(timex * 1000L)
        val sdf = SimpleDateFormat("HH:mm")
        // Setting the default timezone of the user
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }

    override fun onDestroy() {
        if (binding != null){
            binding = null
        }
        super.onDestroy()
    }
}