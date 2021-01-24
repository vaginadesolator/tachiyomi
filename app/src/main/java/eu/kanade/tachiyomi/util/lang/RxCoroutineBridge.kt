package eu.kanade.tachiyomi.util.lang

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import rx.Emitter
import rx.Observable
import rx.Observer
import rx.Scheduler
import rx.Single
import rx.SingleSubscriber
import rx.Subscriber
import rx.Subscription
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/*
 * Util functions for bridging RxJava and coroutines. Taken from TachiyomiEH/SY.
 */

suspend fun <T> Single<T>.await(subscribeOn: Scheduler? = null): T {
    return suspendCancellableCoroutine { continuation ->
        val self = if (subscribeOn != null) subscribeOn(subscribeOn) else this
        lateinit var sub: Subscription
        sub = self.subscribe(
            {
                continuation.resume(it) {
                    sub.unsubscribe()
                }
            },
            {
                if (!continuation.isCancelled) {
                    continuation.resumeWithException(it)
                }
            }
        )

        continuation.invokeOnCancellation {
            sub.unsubscribe()
        }
    }
}

suspend fun <T> Single<T>.await(): T = suspendCancellableCoroutine { cont ->
    cont.unsubscribeOnCancellation(
        subscribe(
            object : SingleSubscriber<T>() {
                override fun onSuccess(t: T) {
                    cont.resume(t)
                }

                override fun onError(error: Throwable) {
                    cont.resumeWithException(error)
                }
            }
        )
    )
}

suspend fun <T> Observable<T>.awaitSingle(): T = single().awaitOne()

private suspend fun <T> Observable<T>.awaitOne(): T = suspendCancellableCoroutine { cont ->
    cont.unsubscribeOnCancellation(
        subscribe(
            object : Subscriber<T>() {
                override fun onStart() {
                    request(1)
                }

                override fun onNext(t: T) {
                    cont.resume(t)
                }

                override fun onCompleted() {
                    if (cont.isActive) cont.resumeWithException(
                        IllegalStateException(
                            "Should have invoked onNext"
                        )
                    )
                }

                override fun onError(e: Throwable) {
                    /*
                       * Rx1 observable throws NoSuchElementException if cancellation happened before
                       * element emission. To mitigate this we try to atomically resume continuation with exception:
                       * if resume failed, then we know that continuation successfully cancelled itself
                       */
                    val token = cont.tryResumeWithException(e)
                    if (token != null) {
                        cont.completeResume(token)
                    }
                }
            }
        )
    )
}

internal fun <T> CancellableContinuation<T>.unsubscribeOnCancellation(sub: Subscription) =
    invokeOnCancellation { sub.unsubscribe() }

fun <T : Any> Observable<T>.asFlow(): Flow<T> = callbackFlow {
    val observer = object : Observer<T> {
        override fun onNext(t: T) {
            offer(t)
        }

        override fun onError(e: Throwable) {
            close(e)
        }

        override fun onCompleted() {
            close()
        }
    }
    val subscription = subscribe(observer)
    awaitClose { subscription.unsubscribe() }
}

fun <T> runAsObservable(
    block: suspend () -> T,
    backpressureMode: Emitter.BackpressureMode = Emitter.BackpressureMode.NONE
): Observable<T> {
    return Observable.create(
        { emitter ->
            val job = GlobalScope.launch(Dispatchers.Unconfined, start = CoroutineStart.ATOMIC) {
                try {
                    emitter.onNext(block())
                    emitter.onCompleted()
                } catch (e: Throwable) {
                    // Ignore `CancellationException` as error, since it indicates "normal cancellation"
                    if (e !is CancellationException) {
                        emitter.onError(e)
                    } else {
                        emitter.onCompleted()
                    }
                }
            }
            emitter.setCancellation { job.cancel() }
        },
        backpressureMode
    )
}
