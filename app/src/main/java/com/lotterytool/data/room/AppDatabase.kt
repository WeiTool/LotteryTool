package com.lotterytool.data.room

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.lotterytool.data.room.action.ActionDao
import com.lotterytool.data.room.action.ActionEntity
import com.lotterytool.data.room.article.ArticleDao
import com.lotterytool.data.room.article.ArticleDeleteDao
import com.lotterytool.data.room.article.ArticleEntity
import com.lotterytool.data.room.dynamicID.DynamicIdEntity
import com.lotterytool.data.room.dynamicID.DynamicIdsDao
import com.lotterytool.data.room.dynamicInfo.DynamicDeleteDao
import com.lotterytool.data.room.dynamicInfo.DynamicInfoDao
import com.lotterytool.data.room.dynamicInfo.DynamicInfoEntity
import com.lotterytool.data.room.log.CrashLogDao
import com.lotterytool.data.room.log.CrashLogEntity
import com.lotterytool.data.room.officialInfo.OfficialInfoDao
import com.lotterytool.data.room.officialInfo.OfficialInfoEntity
import com.lotterytool.data.room.saveTime.SaveTimeDao
import com.lotterytool.data.room.saveTime.SaveTimeEntity
import com.lotterytool.data.room.task.TaskDao
import com.lotterytool.data.room.task.TaskEntity
import com.lotterytool.data.room.task.TaskState
import com.lotterytool.data.room.user.UserDao
import com.lotterytool.data.room.user.UserEntity
import com.lotterytool.data.room.userDynamic.UserDynamicDao
import com.lotterytool.data.room.userDynamic.UserDynamicEntity
import com.lotterytool.data.room.view.DynamicInfoDetail
import com.lotterytool.data.room.view.viewDao.DynamicInfoDetailDao
import java.lang.ProcessBuilder.Redirect.to

@Database(
    entities = [
        UserEntity::class,
        ArticleEntity::class,
        DynamicIdEntity::class,
        DynamicInfoEntity::class,
        TaskEntity::class,
        OfficialInfoEntity::class,
        ActionEntity::class,
        CrashLogEntity::class,
        UserDynamicEntity::class,
        SaveTimeEntity::class,
    ],
    views = [DynamicInfoDetail::class],
    version = 8,
    autoMigrations = [
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8)
    ],
    exportSchema = true
)
@TypeConverters(DatabaseConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun articleDao(): ArticleDao
    abstract fun dynamicIdDao(): DynamicIdsDao
    abstract fun dynamicInfoDao(): DynamicInfoDao
    abstract fun taskDao(): TaskDao
    abstract fun officialInfoDao(): OfficialInfoDao
    abstract fun actionDao(): ActionDao
    abstract fun crashLogDao(): CrashLogDao
    abstract fun userDynamicDao(): UserDynamicDao
    abstract fun dynamicInfoDetailDao(): DynamicInfoDetailDao
    abstract fun dynamicDeleteDao(): DynamicDeleteDao
    abstract fun saveTimeDao(): SaveTimeDao
    abstract fun articleDeleteDao(): ArticleDeleteDao
}

class DatabaseConverters {
    @TypeConverter
    fun toState(value: String) = TaskState.valueOf(value)

    @TypeConverter
    fun fromState(state: TaskState) = state.name
}