package ge.yet.game.fruitmerge.engine

internal data class FruitPhysicsProfile(
    val massMultiplier: Float = 1f,
    val contactRestitution: Float = 0.08f,
    val floorRetention: Float = 0.78f,
    val wallGripSeconds: Float = 0f,
    val spinTransfer: Float = 0.12f,
    val balanceTorque: Float = 0f,
    val shockImpulse: Float = 0f,
)

private val FruitPhysicsProfiles = listOf(
    FruitPhysicsProfile(contactRestitution = 0.30f, floorRetention = 0.82f),
    FruitPhysicsProfile(
        contactRestitution = 0.06f,
        floorRetention = 0.60f,
    ),
    FruitPhysicsProfile(
        contactRestitution = 0.08f,
        floorRetention = 0.68f,
        wallGripSeconds = 0.42f,
    ),
    FruitPhysicsProfile(
        contactRestitution = 0.10f,
        floorRetention = 0.86f,
        spinTransfer = 0.58f,
    ),
    FruitPhysicsProfile(
        contactRestitution = 0.09f,
        floorRetention = 0.94f,
        spinTransfer = 0.30f,
    ),
    FruitPhysicsProfile(
        massMultiplier = 1.24f,
        contactRestitution = 0.05f,
        floorRetention = 0.70f,
    ),
    FruitPhysicsProfile(
        massMultiplier = 1.05f,
        contactRestitution = 0.07f,
        floorRetention = 0.73f,
        balanceTorque = 0.28f,
    ),
    FruitPhysicsProfile(
        massMultiplier = 0.65f,
        contactRestitution = 0.03f,
        floorRetention = 0.58f,
    ),
    FruitPhysicsProfile(
        massMultiplier = 1.08f,
        contactRestitution = 0.04f,
        floorRetention = 0.55f,
    ),
    FruitPhysicsProfile(
        massMultiplier = 1.35f,
        contactRestitution = 0.05f,
        floorRetention = 0.66f,
        shockImpulse = 0.56f,
    ),
)

internal fun fruitPhysicsProfile(level: FruitLevel): FruitPhysicsProfile =
    FruitPhysicsProfiles[level.ordinal]
