package com.lotterytool.di

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.lotterytool.data.api.ApiServices
import com.lotterytool.data.repository.ArticleRepository
import com.lotterytool.data.repository.UserDynamicRepository
import com.lotterytool.data.repository.CheckVersionRepository
import com.lotterytool.data.repository.DynamicIdRepository
import com.lotterytool.data.repository.DynamicInfoRepository
import com.lotterytool.data.repository.OfficialRepository
import com.lotterytool.data.repository.QRRepository
import com.lotterytool.data.repository.UserRepository
import com.lotterytool.data.room.article.ArticleDao
import com.lotterytool.data.room.dynamicID.DynamicIdsDao
import com.lotterytool.data.room.dynamicInfo.DynamicInfoDao
import com.lotterytool.data.room.officialInfo.OfficialInfoDao
import com.lotterytool.data.room.user.UserDao
import com.lotterytool.data.room.userDynamic.UserDynamicDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import jakarta.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    @Singleton
    fun provideGson(): Gson {
        return Gson()
    }

    @Provides
    @Singleton
    fun provideUserRepository(
        apiService: ApiServices,
        userDao: UserDao
    ): UserRepository {
        return UserRepository(apiService, userDao)
    }

    @Provides
    @Singleton
    fun provideArticleRepository(
        apiServices: ApiServices,
        articleDao: ArticleDao,
        userDao: UserDao
    ): ArticleRepository {
        return ArticleRepository(apiServices, articleDao, userDao)
    }

    @Provides
    @Singleton
    fun provideDynamicIdRepository(
        apiServices: ApiServices,
        dynamicIdsDao: DynamicIdsDao
    ): DynamicIdRepository {
        return DynamicIdRepository(apiServices, dynamicIdsDao)
    }

    @Provides
    @Singleton
    fun provideDynamicInfoRepository(
        apiServices: ApiServices,
        dynamicInfoDao: DynamicInfoDao,
        dynamicIdsDao: DynamicIdsDao,
        gson: Gson,
        officialRepository: OfficialRepository,
        officialInfoDao: OfficialInfoDao
    ): DynamicInfoRepository {
        return DynamicInfoRepository(
            apiServices,
            dynamicInfoDao,
            dynamicIdsDao,
            gson,
            officialRepository,
            officialInfoDao
        )
    }

    @Provides
    @Singleton
    fun provideOfficialRepository(
        apiServices: ApiServices,
        officialInfoDao: OfficialInfoDao
    ): OfficialRepository {
        return OfficialRepository(apiServices, officialInfoDao)
    }

    @Provides
    @Singleton
    fun provideQRRepository(
        apiServices: ApiServices
    ): QRRepository {
        return QRRepository(apiServices)
    }

    @Module
    @InstallIn(SingletonComponent::class)
    object AppModule {
        @Provides
        @Singleton
        fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
            return context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        }
    }

    @Provides
    @Singleton
    fun provideCheckVersionRepository(
        apiServices: ApiServices,
        @ApplicationContext context: Context
    ): CheckVersionRepository {
        return CheckVersionRepository(apiServices, context)
    }

    @Provides
    @Singleton
    fun provideCheckDynamicRepository(
        apiServices: ApiServices,
        userDynamicDao: UserDynamicDao
    ): UserDynamicRepository {
        return UserDynamicRepository(apiServices, userDynamicDao)
    }
}
