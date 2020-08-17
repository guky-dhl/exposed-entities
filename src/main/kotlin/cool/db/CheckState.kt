package cool.db

internal fun check(expr: Boolean, errorSupplier: () -> Exception) {
    if (!expr) {
        throw errorSupplier()
    }
}
