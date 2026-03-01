package com.lotterytool.data.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
import com.lotterytool.data.room.userDynamic.UserDynamicDao
import com.lotterytool.data.room.userDynamic.UserDynamicEntity

@Database(
    entities = [UserEntity::class,
        ArticleEntity::class,
        DynamicIdsEntity::class,
        DynamicInfoEntity::class,
        TaskEntity::class,
        OfficialInfoEntity::class,
        ActionEntity::class,
        CrashLogEntity::class,
        UserDynamicEntity::class
    ],
    views = [DynamicInfoDetail::class],
    version = 3,
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
    abstract fun userDynamicDao(): UserDynamicDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. 创建符合新结构的新表 tasks_new
                db.execSQL(
                    """
                CREATE TABLE IF NOT EXISTS `tasks_new` (
                    `articleId` INTEGER PRIMARY KEY NOT NULL, 
                    `state` TEXT NOT NULL, 
                    `currentProgress` INTEGER NOT NULL, 
                    `totalProgress` INTEGER NOT NULL, 
                    `errorMessage` TEXT, 
                    `lastUpdateTime` INTEGER NOT NULL
                )
            """.trimIndent()
                )

                // 2. 拷贝数据（不包含已删除的 detailErrorCount 和 actionErrorCount）
                db.execSQL(
                    """
                INSERT INTO `tasks_new` (`articleId`, `state`, `currentProgress`, `totalProgress`, `errorMessage`, `lastUpdateTime`)
                SELECT `articleId`, `state`, `currentProgress`, `totalProgress`, `errorMessage`, `lastUpdateTime` 
                FROM `tasks`
            """.trimIndent()
                )

                // 3. 删除旧表
                db.execSQL("DROP TABLE `tasks`")

                // 4. 将新表重命名为 tasks
                db.execSQL("ALTER TABLE `tasks_new` RENAME TO `tasks`")

                // 5. 重新创建索引
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_state` ON `tasks` (`state`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. 创建新表
                db.execSQL("""
            CREATE TABLE IF NOT EXISTS `user_dynamic` (
                `serviceId` INTEGER NOT NULL, 
                `dynamicId` INTEGER NOT NULL, 
                `mid` INTEGER NOT NULL, 
                `type` TEXT NOT NULL, 
                `offset` TEXT NOT NULL, 
                `lastUpdated` INTEGER NOT NULL, 
                PRIMARY KEY(`serviceId`)
            )
        """.trimIndent())

                // 2. 删除旧视图
                db.execSQL("DROP VIEW IF EXISTS `dynamic_info_detail`")

                // 3. 创建新视图 - 必须完全复刻 DynamicInfoDetail.kt 中的原始格式
                // 甚至每一行的前导空格都要一致
                db.execSQL("""
CREATE VIEW `dynamic_info_detail` AS SELECT
        d.*,
        o.time AS official_time,
        o.isError AS official_isError,
        a.repostResult,
        a.likeResult,
        a.replyResult,
        a.followResult,
        u.serviceId AS service_id
    FROM dynamic_info AS d
    LEFT JOIN official_info AS o ON d.dynamicId = o.dynamicId
    LEFT JOIN action_info AS a ON d.dynamicId = a.dynamicId
    LEFT JOIN user_dynamic AS u ON d.dynamicId = u.dynamicId
        """.trimEnd()) // 注意：这里用 trimEnd() 保证不破坏内部换行逻辑
            }
        }
    }
}
