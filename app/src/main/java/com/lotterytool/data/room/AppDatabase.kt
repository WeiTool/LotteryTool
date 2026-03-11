package com.lotterytool.data.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lotterytool.data.room.action.ActionDao
import com.lotterytool.data.room.action.ActionEntity
import com.lotterytool.data.room.article.ArticleDao
import com.lotterytool.data.room.article.ArticleEntity
import com.lotterytool.data.room.dynamicID.DynamicIdEntity
import com.lotterytool.data.room.dynamicID.DynamicIdsDao
import com.lotterytool.data.room.dynamicInfo.DynamicDeleteDao
import com.lotterytool.data.room.dynamicInfo.DynamicInfoDao
import com.lotterytool.data.room.view.DynamicInfoDetail
import com.lotterytool.data.room.dynamicInfo.DynamicInfoEntity
import com.lotterytool.data.room.log.CrashLogDao
import com.lotterytool.data.room.log.CrashLogEntity
import com.lotterytool.data.room.officialInfo.OfficialInfoDao
import com.lotterytool.data.room.officialInfo.OfficialInfoEntity
import com.lotterytool.data.room.task.TaskDao
import com.lotterytool.data.room.task.TaskEntity
import com.lotterytool.data.room.user.UserDao
import com.lotterytool.data.room.user.UserEntity
import com.lotterytool.data.room.userDynamic.UserDynamicDao
import com.lotterytool.data.room.userDynamic.UserDynamicEntity
import com.lotterytool.data.room.view.viewDao.DynamicInfoDetailDao

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
        UserDynamicEntity::class
    ],
    views = [DynamicInfoDetail::class],
    version = 6,
    exportSchema = true
)
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
}