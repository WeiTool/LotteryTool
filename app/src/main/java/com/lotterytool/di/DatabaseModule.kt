package com.lotterytool.di

import android.content.Context
import androidx.room.Room
import com.lotterytool.data.room.AppDatabase
import com.lotterytool.data.room.action.ActionDao
import com.lotterytool.data.room.article.ArticleDao
import com.lotterytool.data.room.dynamicID.DynamicIdsDao
import com.lotterytool.data.room.dynamicInfo.DynamicInfoDao
import com.lotterytool.data.room.log.CrashLogDao
import com.lotterytool.data.room.officialInfo.OfficialInfoDao
import com.lotterytool.data.room.task.TaskDao
import com.lotterytool.data.room.user.UserDao
import com.lotterytool.data.room.userDynamic.UserDynamicDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import jakarta.inject.Singleton
import kotlin.jvm.java

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "lottery_database"
        )
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .addMigrations(AppDatabase.MIGRATION_2_3)
            .build()
    }

    @Provides
    fun provideUserDao(database: AppDatabase): UserDao {
        return database.userDao()
    }

    @Provides
    fun provideArticleDao(database: AppDatabase): ArticleDao {
        return database.articleDao()
    }

    @Provides
    fun provideDynamicIdDao(database: AppDatabase): DynamicIdsDao {
        return database.dynamicIdDao()
    }

    @Provides
    fun provideDynamicInfoDao(database: AppDatabase): DynamicInfoDao {
        return database.dynamicInfoDao()
    }

    @Provides
    fun provideTaskDao(database: AppDatabase): TaskDao {
        return database.taskDao()
    }

    @Provides
    fun provideOfficialInfoDao(database: AppDatabase): OfficialInfoDao {
        return database.officialInfoDao()
    }

    @Provides
    fun provideActionDao(database: AppDatabase): ActionDao {
        return database.actionDao()
    }

    @Provides
    fun provideCrashLogDao(database: AppDatabase): CrashLogDao {
        return database.crashLogDao()
    }

    @Provides
    fun provideUserDynamicDao(database: AppDatabase): UserDynamicDao {
        return database.userDynamicDao()
    }

}