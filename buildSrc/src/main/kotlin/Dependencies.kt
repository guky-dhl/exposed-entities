object versions {
    val exposed = "0.26.1"
    val kotlin = "1.3.72"
}

object libs {
    //database
    object exposed {
        val core = "org.jetbrains.exposed:exposed-core:${versions.exposed}"
        val jdbc = "org.jetbrains.exposed:exposed-jdbc:${versions.exposed}"
        val `java-time` = "org.jetbrains.exposed:exposed-java-time:${versions.exposed}"
    }

    val postgre = "org.postgresql:postgresql:42.2.6"
}
