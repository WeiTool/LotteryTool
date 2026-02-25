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
        onProgress: suspend (current: Int, total: Int) -> Unit // 删除了 error: String?
    ) {
        val successfulIds = dynamicInfoDao.getSuccessfulDynamicIds(articleId)
        val total = successfulIds.size

        if (total == 0) {
            onProgress(0, 0)
            return
        }

        for ((index, dynamicId) in successfulIds.withIndex()) {
            if (!currentCoroutineContext().isActive) break

            val existingAction = actionDao.getActionById(dynamicId)
            if (existingAction != null) {
                onProgress(index + 1, total)
                continue
            }

            try {
                // performAction 内部会自动处理结果并存入 ActionEntity 数据库
                performAction(
                    dynamicId,
                    cookie,
                    csrf,
                    content = RepostContent.getRandom(),
                    message = ReplyMessage.getRandom()
                )
                onProgress(index + 1, total)

            } catch (e: Exception) {
                if (e is CancellationException) throw e
                onProgress(index + 1, total)
            }

            if (index < total - 1) {
                delay(Random.nextLong(1000, 2000))
            }
        }

        withContext(NonCancellable) {
            onProgress(total, total)
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

            // 0. 获取现有的执行记录
            val existingAction = actionDao.getActionById(dynamicId)

            // 校验函数：只有明确成功或无需重复操作的情况才跳过
            fun shouldExecute(currentRes: String?): Boolean {
                val idempotentErrors = listOf("成功", "已经关注用户，无法重复关注")
                return currentRes == null || currentRes !in idempotentErrors
            }

            // --- 1. 执行转发 ---
            val repostRes = if (shouldExecute(existingAction?.repostResult)) {
                when (val res = repostRepository.executeRepost(cookie, csrf, dynamicId, content)) {
                    is FetchResult.Success -> "成功"
                    is FetchResult.Error -> res.message // 记录最新的错误，如“操作频繁”
                }
            } else {
                existingAction?.repostResult ?: "成功" // 消除风险：保留原始成功状态
            }

            delay(Random.nextLong(2000, 3000))

            // --- 2. 执行点赞 ---
            val likeRes = if (shouldExecute(existingAction?.likeResult)) {
                when (val res = likeRepository.executeLike(cookie, csrf, dynamicId)) {
                    is FetchResult.Success -> "成功"
                    is FetchResult.Error -> res.message
                }
            } else {
                existingAction?.likeResult ?: "成功"
            }

            delay(Random.nextLong(1000, 2000))

            // --- 3. 执行评论 ---
            val replyRes = if (shouldExecute(existingAction?.replyResult)) {
                // 注意：此处 11 可能需根据动态类型动态调整，此处保持你原始逻辑
                when (val res = replyRepository.executeReply(cookie, 11, info.rid, message, csrf)) {
                    is FetchResult.Success -> "成功"
                    is FetchResult.Error -> res.message
                }
            } else {
                existingAction?.replyResult ?: "成功"
            }

            delay(Random.nextLong(1000, 2000))

            // --- 4. 执行关注 ---
            val followRes = if (shouldExecute(existingAction?.followResult)) {
                when (val res = followRepository.executeFollow(cookie, info.uid, 1, csrf)) {
                    is FetchResult.Success -> "成功"
                    is FetchResult.Error -> res.message
                }
            } else {
                existingAction?.followResult ?: "成功"
            }

            // --- 5. 最终存库 ---
            val actionEntity = ActionEntity(
                dynamicId = dynamicId,
                repostResult = repostRes,
                likeResult = likeRes,
                replyResult = replyRes,
                followResult = followRes
            )
            actionDao.insertAction(actionEntity)

            // 如果依然存在任何一项不是“成功”，则返回 Error 让 UI 显示
            val realError = listOf(repostRes, likeRes, replyRes, followRes)
                .find { it != "成功" && it != "已经关注用户，无法重复关注" }

            if (realError != null) {
                FetchResult.Error(realError)
            } else {
                FetchResult.Success(Unit)
            }

        } catch (e: Exception) {
            FetchResult.Error(e.localizedMessage ?: "执行动作时发生异常")
        }
    }
}