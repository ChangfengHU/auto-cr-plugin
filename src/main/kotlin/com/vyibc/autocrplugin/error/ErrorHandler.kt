package com.vyibc.autocrplugin.error

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 基础错误处理框架 - V5.1版本
 * 错误分类、日志记录、异常恢复机制
 */
class ErrorHandler private constructor() {
    
    private val logger = Logger.getInstance(ErrorHandler::class.java)
    private val errorStats = ConcurrentHashMap<ErrorCategory, AtomicInteger>()
    private val errorHistory = mutableListOf<ErrorRecord>()
    private val recoveryStrategies = mutableMapOf<ErrorCategory, RecoveryStrategy>()
    
    init {
        // 注册默认恢复策略
        registerDefaultStrategies()
    }
    
    /**
     * 处理错误
     */
    fun handleError(
        error: Throwable,
        context: ErrorContext,
        severity: ErrorSeverity = ErrorSeverity.MEDIUM
    ): ErrorHandlingResult {
        val category = categorizeError(error)
        val record = ErrorRecord(
            timestamp = Instant.now(),
            category = category,
            severity = severity,
            error = error,
            context = context,
            stackTrace = error.stackTraceToString()
        )
        
        // 记录错误
        logError(record)
        
        // 更新统计
        updateStats(category)
        
        // 添加到历史
        synchronized(errorHistory) {
            errorHistory.add(record)
            // 保留最近1000条记录
            if (errorHistory.size > 1000) {
                errorHistory.removeAt(0)
            }
        }
        
        // 尝试恢复
        val recovery = attemptRecovery(category, error, context)
        
        return ErrorHandlingResult(
            handled = recovery.success,
            recovered = recovery.recovered,
            message = recovery.message,
            action = recovery.action
        )
    }
    
    /**
     * 分类错误
     */
    private fun categorizeError(error: Throwable): ErrorCategory {
        return when (error) {
            is java.io.IOException -> ErrorCategory.IO_ERROR
            is java.net.UnknownHostException, 
            is java.net.ConnectException -> ErrorCategory.NETWORK_ERROR
            is IllegalArgumentException,
            is IllegalStateException -> ErrorCategory.VALIDATION_ERROR
            is OutOfMemoryError -> ErrorCategory.MEMORY_ERROR
            is InterruptedException -> ErrorCategory.CONCURRENCY_ERROR
            is NullPointerException -> ErrorCategory.NULL_POINTER
            is ClassCastException -> ErrorCategory.TYPE_ERROR
            else -> {
                when {
                    error.message?.contains("API") == true -> ErrorCategory.API_ERROR
                    error.message?.contains("Git") == true -> ErrorCategory.GIT_ERROR
                    error.message?.contains("PSI") == true -> ErrorCategory.PSI_ERROR
                    else -> ErrorCategory.UNKNOWN
                }
            }
        }
    }
    
    /**
     * 记录错误
     */
    private fun logError(record: ErrorRecord) {
        val message = buildErrorMessage(record)
        
        when (record.severity) {
            ErrorSeverity.LOW -> logger.info(message)
            ErrorSeverity.MEDIUM -> logger.warn(message)
            ErrorSeverity.HIGH -> logger.error(message)
            ErrorSeverity.CRITICAL -> {
                logger.error("CRITICAL ERROR: $message")
                // 可以添加额外的通知机制
            }
        }
    }
    
    /**
     * 构建错误消息
     */
    private fun buildErrorMessage(record: ErrorRecord): String {
        return buildString {
            append("[${record.category}] ")
            append("[${record.severity}] ")
            append("${record.error.javaClass.simpleName}: ${record.error.message}")
            append("\nContext: ${record.context}")
            if (record.severity >= ErrorSeverity.HIGH) {
                append("\nStack trace: ${record.stackTrace}")
            }
        }
    }
    
    /**
     * 更新统计
     */
    private fun updateStats(category: ErrorCategory) {
        errorStats.computeIfAbsent(category) { AtomicInteger(0) }.incrementAndGet()
    }
    
    /**
     * 尝试恢复
     */
    private fun attemptRecovery(
        category: ErrorCategory, 
        error: Throwable, 
        context: ErrorContext
    ): RecoveryResult {
        val strategy = recoveryStrategies[category] ?: recoveryStrategies[ErrorCategory.UNKNOWN]!!
        
        return try {
            strategy.recover(error, context)
        } catch (e: Exception) {
            logger.error("Recovery strategy failed", e)
            RecoveryResult(
                success = false,
                recovered = false,
                message = "Recovery failed: ${e.message}",
                action = RecoveryAction.NONE
            )
        }
    }
    
    /**
     * 注册恢复策略
     */
    fun registerRecoveryStrategy(category: ErrorCategory, strategy: RecoveryStrategy) {
        recoveryStrategies[category] = strategy
    }
    
    /**
     * 注册默认策略
     */
    private fun registerDefaultStrategies() {
        // 网络错误恢复
        recoveryStrategies[ErrorCategory.NETWORK_ERROR] = object : RecoveryStrategy {
            override fun recover(error: Throwable, context: ErrorContext): RecoveryResult {
                return RecoveryResult(
                    success = true,
                    recovered = false,
                    message = "Network error detected. Please check your connection.",
                    action = RecoveryAction.RETRY
                )
            }
        }
        
        // 内存错误恢复
        recoveryStrategies[ErrorCategory.MEMORY_ERROR] = object : RecoveryStrategy {
            override fun recover(error: Throwable, context: ErrorContext): RecoveryResult {
                // 触发垃圾回收
                System.gc()
                return RecoveryResult(
                    success = true,
                    recovered = false,
                    message = "Memory error. Triggering garbage collection.",
                    action = RecoveryAction.REDUCE_LOAD
                )
            }
        }
        
        // 默认恢复策略
        recoveryStrategies[ErrorCategory.UNKNOWN] = object : RecoveryStrategy {
            override fun recover(error: Throwable, context: ErrorContext): RecoveryResult {
                return RecoveryResult(
                    success = false,
                    recovered = false,
                    message = "Unknown error: ${error.message}",
                    action = RecoveryAction.LOG_AND_CONTINUE
                )
            }
        }
    }
    
    /**
     * 获取错误统计
     */
    fun getErrorStatistics(): Map<ErrorCategory, Int> {
        return errorStats.mapValues { it.value.get() }
    }
    
    /**
     * 获取最近的错误
     */
    fun getRecentErrors(limit: Int = 10): List<ErrorRecord> {
        return synchronized(errorHistory) {
            errorHistory.takeLast(limit)
        }
    }
    
    /**
     * 清除错误历史
     */
    fun clearHistory() {
        synchronized(errorHistory) {
            errorHistory.clear()
        }
        errorStats.clear()
    }
    
    /**
     * 创建协程异常处理器
     */
    fun createCoroutineExceptionHandler(
        context: ErrorContext
    ): CoroutineExceptionHandler {
        return CoroutineExceptionHandler { _, exception ->
            handleError(exception, context, ErrorSeverity.HIGH)
        }
    }
    
    companion object {
        @JvmStatic
        val instance: ErrorHandler by lazy { ErrorHandler() }
    }
}

/**
 * 错误分类
 */
enum class ErrorCategory {
    IO_ERROR,
    NETWORK_ERROR,
    API_ERROR,
    GIT_ERROR,
    PSI_ERROR,
    VALIDATION_ERROR,
    MEMORY_ERROR,
    CONCURRENCY_ERROR,
    NULL_POINTER,
    TYPE_ERROR,
    UNKNOWN
}

/**
 * 错误严重程度
 */
enum class ErrorSeverity {
    LOW, MEDIUM, HIGH, CRITICAL
}

/**
 * 错误上下文
 */
data class ErrorContext(
    val operation: String,
    val component: String,
    val additionalInfo: Map<String, Any> = emptyMap()
) {
    override fun toString(): String {
        return "Operation: $operation, Component: $component, Info: $additionalInfo"
    }
}

/**
 * 错误记录
 */
data class ErrorRecord(
    val timestamp: Instant,
    val category: ErrorCategory,
    val severity: ErrorSeverity,
    val error: Throwable,
    val context: ErrorContext,
    val stackTrace: String
)

/**
 * 错误处理结果
 */
data class ErrorHandlingResult(
    val handled: Boolean,
    val recovered: Boolean,
    val message: String,
    val action: RecoveryAction
)

/**
 * 恢复动作
 */
enum class RecoveryAction {
    NONE,
    RETRY,
    FALLBACK,
    REDUCE_LOAD,
    LOG_AND_CONTINUE,
    RESTART_COMPONENT
}

/**
 * 恢复策略接口
 */
interface RecoveryStrategy {
    fun recover(error: Throwable, context: ErrorContext): RecoveryResult
}

/**
 * 恢复结果
 */
data class RecoveryResult(
    val success: Boolean,
    val recovered: Boolean,
    val message: String,
    val action: RecoveryAction
)

/**
 * 带错误处理的执行器
 */
class SafeExecutor(
    private val errorHandler: ErrorHandler = ErrorHandler.instance
) {
    
    /**
     * 安全执行
     */
    suspend fun <T> execute(
        context: ErrorContext,
        block: suspend () -> T
    ): Result<T> {
        return try {
            Result.success(block())
        } catch (e: Exception) {
            val result = errorHandler.handleError(e, context)
            Result.failure(Exception(result.message))
        }
    }
    
    /**
     * 带重试的安全执行
     */
    suspend fun <T> executeWithRetry(
        context: ErrorContext,
        maxRetries: Int = 3,
        delayMs: Long = 1000,
        block: suspend () -> T
    ): Result<T> {
        var lastException: Exception? = null
        
        repeat(maxRetries) { attempt ->
            try {
                return Result.success(block())
            } catch (e: Exception) {
                lastException = e
                val result = errorHandler.handleError(
                    e, 
                    context.copy(additionalInfo = context.additionalInfo + ("attempt" to attempt + 1))
                )
                
                if (result.action != RecoveryAction.RETRY) {
                    return Result.failure(e)
                }
                
                if (attempt < maxRetries - 1) {
                    kotlinx.coroutines.delay(delayMs * (attempt + 1))
                }
            }
        }
        
        return Result.failure(lastException ?: Exception("All retries failed"))
    }
}