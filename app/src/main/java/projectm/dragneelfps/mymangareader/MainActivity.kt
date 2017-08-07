package projectm.dragneelfps.mymangareader

import android.os.Bundle
import android.support.v7.app.AppCompatActivity

/**
 * Created by srawa on 8/5/2017.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.series_list)



        GetSeriesList2(Source2.Mangajuki, this).populateList()
    }

}