package com.lotterytool.data.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.lotterytool.data.room.action.ActionDao
import com.lotterytool.data.room.action.ActionEntity
import com.lotterytool.data.room.article.ArticleDao
import com.lotterytool.data.room.article.ArticleEntity
import com.lotterytool.data.room.dynamicID.Converters
import com.lotterytool.data.room.dynamicID.DynamicIdsDao
import com.lotterytool.data.room.dynamicID.DynamicIdsEntity
import com.lotterytool.data.room.dynamicInfo.DynamicInfoDao
import com.lotterytool.data.room.dynamicInfo.DynamicInfoDetail
import com.lotterytool.data.room.dynamicInfo.DynamicInfoEntity
import com.lotterytool.data.room.log.CrashLogDao
import com.lotterytool.data.room.log.CrashLogEntity
import com.lotterytool.data.room.officialInfo.OfficialInfoDao
import com.lotterytool.data.room.officialInfo.OfficialInfoEntity
import com.lotterytool.data.room.task.TaskDao
import com.lotterytool.data.room.task.TaskEntity
import com.lotterytool.data.room.user.UserDao
import com.lotterytool.data.room.user.UserEntity

@Database(
    entities = [UserEntity::class,
        ArticleEntity::class,
        DynamicIdsEntity::class,
        DynamicInfoEntity::class,
        TaskEntity::class,
        OfficialInfoEntity::class,
        ActionEntity::class,
        CrashLogEntity::class
    ],
    views = [DynamicInfoDetail::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun articleDao(): ArticleDao
    abstract fun dynamicIdDao(): DynamicIdsDao
    abstract fun dynamicInfoDao(): DynamicInfoDao
    abstract fun taskDao(): TaskDao
    abstract fun officialInfoDao(): OfficialInfoDao
    abstract fun actionDao(): ActionDao
    abstract fun crashLogDao(): CrashLogDao
}
