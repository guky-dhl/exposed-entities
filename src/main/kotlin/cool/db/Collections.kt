package cool.db

fun <T> Collection<T>.single(predicate: (T) -> Boolean, errorSupplier: (collection: Collection<T>) -> Throwable): T {
    val collection = this.filter(predicate)
    return if (collection.size == 1) {
        collection.first()
    } else {
        throw errorSupplier(collection)
    }
}

fun <T> Collection<T>.first(predicate: (T) -> Boolean, errorSupplier: (collection: Collection<T>) -> Throwable): T {
    return this.firstOrNull(predicate) ?: throw errorSupplier(this)
}
