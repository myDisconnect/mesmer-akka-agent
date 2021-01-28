package io.scalac.extension.resource

import io.scalac.core.util.Timestamp
import io.scalac.extension.config.CleaningSettings

trait MutableCleanableStorage[K, V] extends SelfCleaning with MutableStorage[K, V] {
  protected def cleaningConfig: CleaningSettings
  protected def extractTimestamp(value: V): Timestamp
  protected def currentTimestamp: Timestamp = Timestamp.create()

  override def clean(): Unit = {
    val current        = currentTimestamp
    val maxStalenessMs = cleaningConfig.maxStaleness.toMillis
    for {
      key <- buffer.keysIterator
    } buffer.updateWith(key) {
      case Some(v) if extractTimestamp(v).interval(current) > maxStalenessMs => None
      case v                                                                 => v
    }
  }
}