package ge.yet3.blokblast

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform