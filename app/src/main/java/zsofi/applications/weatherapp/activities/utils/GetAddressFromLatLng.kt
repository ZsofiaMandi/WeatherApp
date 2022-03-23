package zsofi.applications.weatherapp.activities.utils

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Handler
import android.os.Looper
import java.lang.Exception
import java.lang.StringBuilder
import java.util.*
import java.util.concurrent.Executors

class GetAddressFromLatLng(
    context: Context,
    private val latitude: Double,
    private val longitude: Double
    ) {

    private val mExecutor = Executors.newSingleThreadExecutor()
    private val mHandler = Handler(Looper.getMainLooper())

    private var executeResult : String? = null

    private val geocoder: Geocoder = Geocoder(context, Locale.getDefault())
    private lateinit var mAddressListener: AddressListener

    fun getAddress(){
        mExecutor.execute {
            try{
                val addressList: List<Address>? =
                    geocoder.getFromLocation(latitude, longitude, 1)
                if(!addressList.isNullOrEmpty()){
                    val address: Address = addressList[0]
                    executeResult = address.locality
                }else{
                    executeResult = ""
                }
            }catch (e: Exception){
                e.printStackTrace()
            }
            mHandler.post {
                if(executeResult == null){
                    mAddressListener.onError()
                }else{
                    mAddressListener.onAddressFound(executeResult)
                }
            }
        }
    }

    fun setAddressListener(addressListener: AddressListener){
        mAddressListener = addressListener
    }

    interface AddressListener{
        fun onAddressFound(address: String?)
        fun onError()
    }
}