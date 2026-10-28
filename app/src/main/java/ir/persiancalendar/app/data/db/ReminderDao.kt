package ir.persiancalendar.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    @Query("SELECT * FROM reminders ORDER BY enabled DESC, type, createdAt")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders")
    suspend fun getAll(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE enabled = 1")
    suspend fun getEnabled(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: Long): ReminderEntity?

    @Query("SELECT COUNT(*) FROM reminders")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: ReminderEntity): Long

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Delete
    suspend fun delete(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM dose_logs WHERE reminderId = :reminderId")
    suspend fun deleteLogsOf(reminderId: Long)
}

@Dao
interface DoseLogDao {

    /** ثبت‌های یک روز مشخص — برای «برنامه امروز» و پیشرفت آب */
    @Query("SELECT * FROM dose_logs WHERE dayKey = :dayKey")
    fun observeDay(dayKey: Int): Flow<List<DoseLogEntity>>

    @Query("SELECT * FROM dose_logs WHERE dayKey = :dayKey")
    suspend fun getDay(dayKey: Int): List<DoseLogEntity>

    @Query("SELECT COUNT(*) FROM dose_logs WHERE reminderId = :reminderId AND dayKey = :dayKey")
    suspend fun countOfDay(reminderId: Long, dayKey: Int): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: DoseLogEntity): Long

    @Query("DELETE FROM dose_logs WHERE reminderId = :reminderId AND dayKey = :dayKey AND timeMinute = :timeMinute")
    suspend fun deleteSlot(reminderId: Long, dayKey: Int, timeMinute: Int)

    @Query("DELETE FROM dose_logs WHERE reminderId = :reminderId")
    suspend fun deleteOfReminder(reminderId: Long)

    @Query("DELETE FROM dose_logs WHERE dayKey < :beforeDayKey")
    suspend fun pruneOlderThan(beforeDayKey: Int)
}
