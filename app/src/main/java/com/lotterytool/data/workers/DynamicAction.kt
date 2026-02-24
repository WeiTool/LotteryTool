package com.lotterytool.data.workers

import com.lotterytool.data.repository.actionRepository.FollowRepository
import com.lotterytool.data.repository.actionRepository.LikeRepository
import com.lotterytool.data.repository.actionRepository.ReplyRepository
import com.lotterytool.data.repository.actionRepository.RepostRepository
import com.lotterytool.data.room.action.ActionDao
import com.lotterytool.data.room.action.ActionEntity
import com.lotterytool.data.room.dynamicInfo.DynamicInfoDao
import com.lotterytool.utils.FetchResult
import com.lotterytool.utils.ReplyMessage
import com.lotterytool.utils.RepostContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.random.Random

class DynamicAction @Inject constructor(
    private val dynamicInfoDao: DynamicInfoDao,
    private val actionDao: ActionDao,
    private val followRepository: FollowRepository,
    private val likeRepository: LikeRepository,
    private val replyRepository: ReplyRepository,
    private val repostRepository: RepostRepository,
) {
    /**
     * 模仿 processAndStoreAllDynamics 的逻辑
     * 批量执行所有动态的操作（转发、点赞、评论、关注）
     */
    suspend fun allAction(
        articleId: Long,
        cookie: String,
        csrf: String,
        onProgress: suspend (current: Int, total: Int, error: String?) -> Unit
    ) {
        val successfulIds = dynamicInfoDao.getSuccessfulDynamicIds(articleId)
        val total = successfulIds.size

        if (total == 0) {
            onProgress(0, 0, "没有可执行的动态")
            return
        }

        for ((index, dynamicId) in successfulIds.withIndex()) {
            if (!currentCoroutineContext().isActive) {
                break
            }

            // 获取已经处理的
            val existingAction = actionDao.getActionById(dynamicId)
            if (existingAction != null) {
                onProgress(index + 1, total, null)
                continue
            }

            try {
                val result = performAction(
                    dynamicId,
                    cookie,
                    csrf,
                    content = RepostContent.getRandom(),
                    message = ReplyMessage.getRandom()
                )

                if (result is FetchResult.Error) {
                    onProgress(index + 1, total, "error")
                } else {
                    onProgress(index + 1, total, null)
                }

            } catch (e: Exception) {
                if (e is CancellationException) throw e
                onProgress(index + 1, total, "error")
            }

            if (index < total - 1) {
                delay(Random.nextLong(1000, 2000))
            }
        }

        withContext(NonCancellable) {
            onProgress(total, total, null)
        }
    }

    /**
     * 执行单个动态的全套动作并存库
     */
    suspend fun performAction(
        dynamicId: Long,
        cookie: String,
        csrf: String,
        content: String,
        message: String
    ): FetchResult<Unit> = withContext(NonCancellable) {
        return@withContext try {
            val info = dynamicInfoDao.getInfoById(dynamicId)
                ?: return@withContext FetchResult.Error("未找到动态详情")

            // 0. 获取现有的执行记录（如果有）
            val existingAction = actionDao.getActionById(dynamicId)

            // 定义辅助函数：检查是否需要执行该步骤
            fun shouldExecute(currentRes: String?): Boolean {
                // 如果结果已经是成功，或者属于幂等性错误（如已关注），则不需要重新执行
                return currentRes == null || (currentRes != "成功" && currentRes != "已经关注用户，无法重复关注")
            }

            // 1. 执行转发
            val repostRes = if (shouldExecute(existingAction?.repostResult)) {
                when (val res = repostRepository.executeRepost(cookie, csrf, dynamicId, content)) {
                    is FetchResult.Success -> "成功"
                    is FetchResult.Error -> res.message
                }
            } else {
                existingAction?.repostResult ?: "成功"
            }

            delay(Random.nextLong(1000, 2000))

            // 2. 执行点赞
            val likeRes = if (shouldExecute(existingAction?.likeResult)) {
                when (val res = likeRepository.executeLike(cookie, csrf, dynamicId)) {
                    is FetchResult.Success -> "成功"
                    is FetchResult.Error -> res.message
                }
            } else {
                existingAction?.likeResult ?: "成功"
            }

            delay(Random.nextLong(1000, 2000))

            // 3. 执行评论
            val replyRes = if (shouldExecute(existingAction?.replyResult)) {
                when (val res = replyRepository.executeReply(cookie, 11, info.rid, message, csrf)) {
                    is FetchResult.Success -> "成功"
                    is FetchResult.Error -> res.message
                }
            } else {
                existingAction?.replyResult ?: "成功"
            }

            delay(Random.nextLong(1000, 2000))

            // 4. 执行关注
            val followRes = if (shouldExecute(existingAction?.followResult)) {
                when (val res = followRepository.executeFollow(cookie, info.uid, 1, csrf)) {
                    is FetchResult.Success -> "成功"
                    is FetchResult.Error -> res.message
                }
            } else {
                existingAction?.followResult ?: "成功"
            }

            // 5. 更新数据库：Room 的 insertAction 通常配置为 OnConflictStrategy.REPLACE
            // 这会用包含新结果的 Entity 覆盖旧记录
            val actionEntity = ActionEntity(
                dynamicId = dynamicId,
                repostResult = repostRes,
                likeResult = likeRes,
                replyResult = replyRes,
                followResult = followRes
            )
            actionDao.insertAction(actionEntity)

            FetchResult.Success(Unit)
        } catch (e: Exception) {
            FetchResult.Error(e.localizedMessage ?: "执行动作时发生异常")
        }
    }
}