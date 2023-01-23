import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.bieganieaplikacja.db.Converters
import com.example.bieganieaplikacja.db.Run
import com.example.bieganieaplikacja.db.RunDao


@Database(entities = [Run::class], version = 1)
@TypeConverters(Converters::class)
abstract class RunningDatabase : RoomDatabase() {

    abstract fun getRunDao(): RunDao
}
