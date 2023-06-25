package galaxyraiders.core.game

import galaxyraiders.core.physics.Point2D
import galaxyraiders.core.physics.Vector2D

class Explosion(
  initialPosition: Point2D,
  initialVelocity: Vector2D,
  radius: Double,
  mass: Double,
) :
  SpaceObject("Explosion", '*', initialPosition, initialVelocity, radius, mass){

  var isTriggered: Boolean = false
  
  var life_time: Int = 0
    private set
  
  fun comeca(){
    life_time = life_time + 1
    if(life_time > 15){
      isTriggered = true
    }
  }
  }