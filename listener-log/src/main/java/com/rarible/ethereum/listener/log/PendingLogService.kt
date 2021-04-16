package com.rarible.ethereum.listener.log

import com.rarible.core.common.retryOptimisticLock
import com.rarible.core.logging.LoggingUtils
import com.rarible.ethereum.listener.log.domain.LogEvent
import com.rarible.ethereum.listener.log.domain.LogEventStatus
import com.rarible.ethereum.listener.log.persist.LogEventRepository
import com.rarible.rpc.domain.Word
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.Marker
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.kotlin.core.publisher.toFlux
import scalether.domain.response.Block
import scalether.domain.response.Transaction
import scalether.java.Lists

@Service
class PendingLogService(
    private val logEventRepository: LogEventRepository
) {

    fun markInactive(collection: String, topic: Word, block: Block<Transaction>): Flux<LogEvent> {
        return LoggingUtils.withMarkerFlux { marker ->
            logEventRepository.findPendingLogs(collection)
                .filter { it.topic == topic }
                .map { RichLog(collection, it) }
                .collectList()
                .flatMapMany { markInactive(marker, block, it) }
        }
    }

    private fun markInactive(marker: Marker, block: Block<Transaction>, logs: List<RichLog>): Flux<LogEvent> {
        if (logs.isEmpty()) {
            return Flux.empty()
        }
        val byTxHash = logs.groupBy { (_, log) -> log.transactionHash }
        val byFromNonce = logs.groupBy { (_, log) -> Pair(log.from, log.nonce) }
        return Flux.fromIterable(Lists.toJava(block.transactions()))
            .flatMap { tx ->
                val first = byTxHash[tx.hash()] ?: emptyList()
                val second = (byFromNonce[Pair(tx.from(), tx.nonce().toLong())] ?: emptyList()) - first
                Flux.concat(
                    markInactive(marker, LogEventStatus.INACTIVE, first),
                    markInactive(marker, LogEventStatus.DROPPED, second)
                )
            }
    }

    private fun markInactive(marker: Marker, status: LogEventStatus, logs: List<RichLog>): Flux<LogEvent> {
        return if (logs.isNotEmpty()) {
            logger.info(marker, "markInactive $status $logs")
            logs.toFlux()
                .flatMap { (col, log) ->
                    logEventRepository.findLogEvent(col, log.id)
                        .map { it.copy(status = status, visible = false) }
                        .flatMap { logEventRepository.save(col, it) }
                        .retryOptimisticLock()
                }
        } else {
            Flux.empty()
        }
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(PendingLogService::class.java)
    }
}

data class RichLog(val collection: String, val log: LogEvent)