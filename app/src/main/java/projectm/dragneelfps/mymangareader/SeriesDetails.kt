package projectm.dragneelfps.mymangareader

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast

class SeriesDetails : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.series_details)
        val s: String = intent.getStringExtra("link")
        Toast.makeText(this,s,Toast.LENGTH_SHORT).show()
    }
}
